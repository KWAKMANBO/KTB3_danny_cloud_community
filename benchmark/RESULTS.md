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
- 증거 로그: [`nplus1_before.log`](nplus1_before.log) / [`nplus1_after.log`](nplus1_after.log) / [`nplus1_after5.log`](nplus1_after5.log) / [`nplus1_after_multiuser.log`](nplus1_after_multiuser.log)

**size 무관 상수 검증:**

| size | Before (N+1) | After (배치) |
|--:|--:|--:|
| 5 | ~19 (비례 증가) | **5** (상수) |
| 20 | 63 (비례 증가) | **5** (상수) |

→ After는 size와 무관하게 5개. IN절 원소 수만 size 따라 변하고(20→5), 쿼리 **개수는 불변** = N+1 해소.

**작성자 다양성 검증 (다중 유저):**

위 측정은 게시글 100만 건이 모두 같은 작성자(`user_id=1`)인 조건이었다. 이 경우 작성자 로딩이
영속성 컨텍스트 캐시로 1회만 잡히므로, **Before가 실제보다 과소 측정**된다(작성자가 모두 다르면 20회 → Before ≈ 82).

유저를 1,000명으로 늘리고 `post.user_id`를 분산시킨 뒤 재측정:

| 조건 | 작성자 수 | After SELECT 수 |
|---|--:|--:|
| 초기 시딩 | 1명 | 5 |
| 다중 유저 | **1,000명** | **5** (변화 없음) |

목록 쿼리가 `from post p join user u on u.user_id = p.user_id ... limit ?` 이므로,
20개 게시글의 작성자가 전부 달라도 **이 한 쿼리로 모두 로딩**된다.
→ `join fetch`가 작성자 N+1을 실제로 차단함을 확인. (join fetch가 없었다면 여기서 작성자 조회 20회가 추가됐을 것)

*(avg/p95/req·s는 k6 측정 예정)*

---

## 실험 3 — `(deleted_at, post_id)` 복합 인덱스 (보조)
커서 쿼리 `WHERE deleted_at IS NULL AND post_id < ? ORDER BY post_id DESC LIMIT 20`에
복합 인덱스를 추가하고 전/후를 `EXPLAIN ANALYZE`로 비교. (측정: [`index_experiment.sh`](index_experiment.sh))

상세 기록: [`인덱스검증_claude.md`](인덱스검증_claude.md)

> **1차 측정은 폐기했다.** `SELECT post_id`만 조회해 `(deleted_at, post_id)` 인덱스로 **커버링 스캔이 성립**했고
> "cursor=100,000에서 40% 개선"이 나왔으나, 실제 목록 쿼리는 `title·content·created_at·user_id` + **user 조인**이
> 필요해 커버링이 성립하지 않는다. 아래는 실제 컬럼 구성으로 재측정한 결과다.

### 결과 — 복합 인덱스는 채택되지 않음

**[B] post 컬럼만 조회** (`post_id, title, content, created_at, user_id`)

| cursor(post_id <) | Before (PK 단독) | After (인덱스 존재) | After 실행계획 | 채택 |
|--:|--:|--:|---|:--:|
| 1,000,000 | 0.071 ms | 0.052 ms | Index range scan **PRIMARY** | ✗ |
| 500,000 | 0.074 ms | 0.053 ms | Index range scan **PRIMARY** | ✗ |
| 100,000 | 0.050 ms | 0.074 ms | Index range scan **PRIMARY** | ✗ |

- `idx_post_deleted_id`가 **단 한 번도 채택되지 않았다.** 세 경우 모두 PK 스캔 유지, 실제 스캔 행수 20행.
- 시간 차이는 **측정 노이즈 수준**(0.05~0.07ms 진동). AFTER가 더 느린 구간(cursor=100,000)도 있어 개선으로 볼 수 없다.

**해석:** 실제 컬럼을 조회하는 순간 커버링이 불가능해지고, 복합 인덱스를 타면 행마다 클러스터드 인덱스로 돌아가
나머지 컬럼을 읽는 **테이블 룩업**이 추가된다. 옵티마이저는 그 비용을 알기에 PK 단독 스캔을 고수한다.

**결론:** `(deleted_at, post_id)` 복합 인덱스는 **추가할 이유가 없다.** 읽기 이득이 없는 반면
**쓰기 비용**(INSERT/UPDATE/DELETE마다 인덱스 갱신)과 저장 공간이 든다. 실험 후 DROP하여 원복.

> 부수적 결론: id 커서 + PK 조합은 이미 최적에 가깝다. "느리면 인덱스 추가"가 아니라
> **쿼리가 실제로 무엇을 읽는지 확인한 뒤 판단해야 한다**는 사례.

### 파생 검증 — 조인 쿼리의 실행계획 (join fetch 안전성)

[A] 작성자 join 형태(= 프로덕션 목록 쿼리)를 측정하다 **597~1084ms**라는 이상값을 발견했다.
실행계획이 **해시 조인 + 전체 정렬**로, 75만 행을 모두 조인·정렬한 뒤 20개를 취해 **LIMIT 조기 종료가 무력화**된 상태였다.

원인을 좁히기 위해 유저 수를 늘려 재측정([`verify_join_plan.sh`](verify_join_plan.sh)):

| 조건 | actual time | 실행계획 | post 스캔 행수 |
|---|--:|---|--:|
| 유저 **1명** | **1084 ms** | Hash join + Sort | 999,999 |
| 유저 **1,000명** | **0.091 ms** | **Nested loop + PK 조회** | **20** |

→ 유저가 1명뿐이라 옵티마이저가 "user는 1행이니 해시 조인이 싸다"고 오판한 **시딩 데이터 아티팩트**로 확정.
작성자가 다양해지자 중첩 루프를 선택해 인덱스 순서로 20행만 읽고 멈추며, Sort 노드도 사라졌다.

**결론: `join fetch p.user`는 프로덕션에서 안전하다.** 커서 페이징의 O(1) 특성이 유지되며 N+1 개선은 회귀를 만들지 않았다.
