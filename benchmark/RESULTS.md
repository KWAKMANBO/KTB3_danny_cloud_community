# 게시글 목록 조회 성능 최적화 — 측정 결과

커서(키셋) 페이징 도입과 N+1 제거의 효과를 100만 건 데이터로 측정한다.
정렬/커서 기준은 `post_id`(auto_increment)로 통일 → "최근 글 먼저" + 중복/누락 방지 + PK 인덱스 재사용.

## 측정 조건
- 데이터: 게시글 **1,000,000건** (노출 750,000 / soft-delete 250,000 = 25%, `count` 1:1)
- 시딩: [`seed.sh`](seed.sh) — `./benchmark/seed.sh 1000000 1 25`
- 환경: 로컬 Docker (MySQL 8.0 + Redis 7 + App, `community-be-multi:bench`)
- 도구:
  - DB 레벨 — `EXPLAIN ANALYZE` ([`compare_pagination.sh`](compare_pagination.sh)): 스캔 행수 / actual time
  - 엔드포인트 레벨 — k6 20 VU / 30초 ([`k6/list.js`](k6/list.js)): avg / p95 / req·s
- 측정 원칙: 워밍업 후 측정(첫 실행 버림), 반복, p95 병기

---

## 실험 1 — OFFSET vs 커서 (깊은 페이지에서의 확장성) ⭐
"왜 커서 페이징인가"의 핵심 근거. 같은 depth(목록 위치)에서 두 방식이 검사하는 행 수를 비교.

- **OFFSET**: `... ORDER BY post_id DESC LIMIT 20 OFFSET N` — 앞의 N행을 전부 읽고 버림 → O(depth)
- **CURSOR**: `... WHERE post_id < ? ORDER BY post_id DESC LIMIT 20` — 인덱스로 시작점 seek → O(1)

`size=20`, `EXPLAIN ANALYZE` 실측:

| depth | OFFSET 스캔 행수 | OFFSET actual time | CURSOR 스캔 행수 | CURSOR actual time | 배율 |
|------:|---:|---:|---:|---:|---:|
| 0 | 21 | 0.077 ms | 20 | 3.83 ms* | — |
| 1,000 | 1,346 | 0.85 ms | 20 | 0.20 ms | ~4× |
| 10,000 | 13,346 | 3.99 ms | 20 | 0.18 ms | ~22× |
| 100,000 | 133,346 | 41.8 ms | 20 | 0.094 ms | ~445× |
| 500,000 | 666,671 | 193 ms | 20 | 0.052 ms | ~3,700× |
| 900,000 | **1,000,000** | 234 ms | 20 | 0.057 ms | **~4,100×** |

`*` depth=0 커서 3.83ms는 콜드 캐시(스크립트의 첫 커서 쿼리, 인덱스 페이지 미적재). 이후 값이 0.05~0.2ms로 일정한 것으로 보아 워밍업 후 ~0.05ms대로 수렴.

**결론:** OFFSET은 depth에 정비례(스캔 21→100만 행, 0.077→234ms). 커서는 depth와 무관하게 항상 20행·~0.05ms 상수. 깊은 페이지일수록 격차가 수천 배로 벌어진다.

**추가 관측 (실행계획 근거):**
- **없는 페이지도 풀스캔**: depth 900,000에서 OFFSET은 `rows=0` 반환(노출 750,000 < offset 900,000)인데도 `Index scan on PRIMARY (reverse)`로 **100만 행 전체**를 훑고 234ms 소비.
- **삭제 행이 OFFSET 낭비를 증폭**: depth 100,000에 스캔 133,346행 — 삭제된 25%는 offset 카운트에 안 잡히지만 읽기는 발생.
- **실행계획 대비**: OFFSET = `Index scan on PRIMARY (reverse)` 전체 훑음 / CURSOR = `Index range scan over (post_id < ?)` 시작점 seek 후 20행.
- **옵티마이저 추정 ≠ 실측**: 커서 쿼리의 `cost≈99399 / rows≈495686`은 비관적 추정이나, `LIMIT 20`이 조기 종료시켜 실제 20행·~0.05ms.

---

## 실험 2 — N+1 제거 (페이지당 쿼리 수)
현재 게시글마다 `count`·`like`·`user`를 개별 조회 → 페이지(20건)당 약 60여 개 쿼리.
배치 조회(`IN` / fetch join)로 상수 개로 축소.

측정: `count_queries.sh`로 `GET /posts?size=20` 1회당 SELECT 수(`Com_select` 델타).
상세 분석: [`N_PLUS_1_ANALYSIS.md`](N_PLUS_1_ANALYSIS.md)

| 단계 | 페이지당 SELECT 수 (size=20) | 첫 페이지 avg | 첫 페이지 p95 | req·s |
|---|---:|---:|---:|---:|
| Before (N+1) | **63~64** (측정) | | | |
| After (배치) | **5** (측정) | | | |

**Before 63 분해:** 목록 1 + findByEmail 21(checkLike 20 + JWT필터 1) + count 20 + like existsById 20 + 작성자 1.
→ N+1 본체 = 반복문의 60개(count 20 + findByEmail 20 + existsById 20).

**After 5 분해:** 목록+작성자 join fetch 1 + count `IN(20)` 1 + like `IN(20)` 1 + findByEmail 2(JWT필터 1 + getPostList hoist 1).
- count·like 각 20 → **1**(IN절 배치), findByEmail 21 → **2**, 작성자 → 목록에 **join fetch** 흡수.
- 남은 findByEmail 중복 1건은 `SecurityContext` principal 재사용으로 추가 축소 가능(미세 최적화, N+1 아님).
- 증거 로그: [`nplus1_before.log`](nplus1_before.log) / [`nplus1_after.log`](nplus1_after.log) / [`nplus1_after5.log`](nplus1_after5.log)

**size 무관 상수 검증 (핵심 증거):**

| size | Before (N+1) | After (배치) |
|--:|--:|--:|
| 5 | ~19 (비례 증가) | **5** (상수) |
| 20 | 63 (비례 증가) | **5** (상수) |

→ After는 size와 무관하게 5개. IN절 원소 수만 size 따라 변하고(20→5), 쿼리 **개수는 불변** = N+1 해소.

*(avg/p95/req·s는 k6 측정 예정)*

---

## 실험 3 — `(deleted_at, post_id)` 복합 인덱스 (보조)
커서 쿼리 `WHERE deleted_at IS NULL AND post_id < ? ORDER BY post_id DESC`에 대한 인덱스 효과.
(삭제 비율 25% 조건에서 PK 단독 스캔 대비 개선폭 관측.)

| 단계 | 인덱스 | 첫 페이지 p95 | 깊은 페이지 p95 |
|---|---|---:|---:|
| Before | PK(post_id)만 | | |
| After | + `(deleted_at, post_id)` | | |

*(measured 예정)*
