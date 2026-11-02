# `(deleted_at, post_id)` 복합 인덱스 검증

실험 3의 재측정 기록. 1차 측정이 무효였던 이유와, 실제 쿼리 형태로 다시 측정해 얻은 결과 두 가지를 정리한다.

- 데이터: 게시글 100만 건(노출 75만 / 삭제 25만), **user 1명** (전 게시글 `user_id=1`)
- 측정: [`index_experiment.sh`](index_experiment.sh) — `EXPLAIN ANALYZE`, 워밍업 1회 후 측정
- 쿼리 두 형태 교차 확인
  - **[B]** `SELECT p.post_id, p.title, p.content, p.created_at, p.user_id FROM post p ...` (조인 없음)
  - **[A]** `... FROM post p JOIN user u ON u.user_id = p.user_id ...` (실제 목록 쿼리 형태)

---

## 0. 1차 측정이 무효였던 이유

1차 실험은 `SELECT post_id`만 조회했다. 이 경우 `(deleted_at, post_id)` 인덱스에 필요한 컬럼이 모두 들어 있어
**커버링 인덱스 스캔**이 성립했고, `cursor=100,000`에서 0.084 → 0.050ms(~40%) 개선으로 관측됐다.

그러나 실제 목록 쿼리는 `title`·`content`·`created_at`·`user_id`가 필요하고 **user 조인**까지 한다.
→ 인덱스에 없는 컬럼을 읽어야 하므로 **커버링이 성립하지 않는다.**
→ 즉 1차의 "40% 개선"은 단순화된 쿼리에서만 나온 값이며 **프로덕션에 적용되지 않는다.**

실제 컬럼 구성으로 다시 측정한 결과가 아래다.

---

## 발견 1 — 복합 인덱스는 채택되지 않음 (추가 불필요)

**[B] post 컬럼만 (조인 없음)** — 인덱스 추가 전/후 모두 **`Index range scan on p using PRIMARY`** 유지.
`idx_post_deleted_id`가 **단 한 번도 채택되지 않았다.**

| cursor (post_id <) | BEFORE (PK 단독) | AFTER (복합 인덱스 존재) | 채택 여부 |
|--:|--:|--:|:--:|
| 1,000,000 | 0.071 ms | 0.052 ms | ✗ (PRIMARY) |
| 500,000 | 0.074 ms | 0.053 ms | ✗ (PRIMARY) |
| 100,000 | 0.050 ms | 0.074 ms | ✗ (PRIMARY) |

- 시간 차이는 **측정 노이즈 수준**(0.05~0.07ms 사이 진동). AFTER가 더 느린 구간(cursor=100,000)도 있어 개선이라 볼 수 없다.
- 세 경우 모두 실제 스캔 행수는 20행으로 동일 — 커서 페이징이 이미 최소 작업만 하고 있다.

**해석:** 실제 컬럼을 조회하는 순간 커버링이 불가능해지고, 복합 인덱스를 타면 **행마다 클러스터드 인덱스(PK)로 돌아가 나머지 컬럼을 읽는 테이블 룩업**이 추가된다. 옵티마이저는 그 비용을 알기 때문에 PK 단독 스캔을 고수한다.

**결론:** `(deleted_at, post_id)` 복합 인덱스는 **추가할 이유가 없다.**
읽기 이득이 없고, 인덱스 유지에는 **쓰기 비용**(INSERT/UPDATE/DELETE마다 갱신)과 저장 공간이 든다.
→ 실험 후 인덱스는 DROP하여 원복.

> 부수적 결론: id 커서 + PK 조합은 이미 최적에 가깝다. "느리면 인덱스를 추가한다"가 아니라, **쿼리가 실제로 무엇을 읽는지 확인한 뒤 판단해야 한다**는 사례.

---

## 발견 2 — ⚠️ 조인 쿼리에서 LIMIT 조기 종료가 무력화됨

**[A] 작성자 join 포함** — 인덱스 추가 여부와 무관하게 **모든 경우 매우 느림**.

| cursor (post_id <) | BEFORE | AFTER | 비고 |
|--:|--:|--:|---|
| 1,000,000 | **597 ms** | 587 ms | 인덱스와 무관 |
| 500,000 | 309 ms | 292 ms | |
| 100,000 | 68.6 ms | 67.1 ms | |

[B]가 0.05ms인데 [A]는 **597ms** — 약 1만 배 차이다.

### 실행계획
```
-> Limit: 20 row(s)                                   (actual time=597..597 rows=20)
   -> Sort: p.post_id DESC, limit input to 20 row(s)  (actual time=597..597)
      -> Stream results                               (actual time=295..554 rows=750000)
         -> Inner hash join (u.user_id = p.user_id)   (actual time=295..380 rows=750000)
            -> Table scan on u                        (rows=1)
            -> Hash
               -> Filter: (deleted_at is null and post_id < 1000000)  (rows=750000)
                  -> Index range scan on p using PRIMARY (reverse)    (rows=999999)
```

**문제의 핵심:** 옵티마이저가 **해시 조인**을 선택했다. 그 결과
1. `post`를 **100만 행 전부 스캔** (`rows=999999`)
2. 조건 통과한 **75만 행을 전부 조인** (`rows=750000`)
3. 그 결과를 **정렬(Sort)** 한 뒤
4. 마지막에 20개를 취함

즉 **인덱스 순서로 20개만 읽고 멈추는 조기 종료가 완전히 사라졌다.** 커서 페이징의 O(1) 특성이 무너지고 O(N)이 된다.
(해시 조인의 출력은 정렬 순서가 보장되지 않으므로 별도 Sort가 강제된다.)

### ✅ 검증 완료 — 시딩 데이터 아티팩트로 확정

[`verify_join_plan.sh`](verify_join_plan.sh)로 유저 수를 1명 → 1000명으로 늘리고 `post.user_id`를 분산시킨 뒤 동일 쿼리를 재측정했다.

| 조건 | actual time | 실행계획 | post 스캔 행수 |
|---|--:|---|--:|
| 유저 **1명** | **1084 ms** | Hash join + Sort | 999,999 |
| 유저 **1000명** | **0.091 ms** | **Nested loop inner join** | **20** |

```
-> Limit: 20 row(s)                                        (actual time=0.0747..0.0908 rows=20)
   -> Nested loop inner join                               (actual time=0.0744..0.0896 rows=20)
      -> Index range scan on p using PRIMARY (reverse)      (actual time=0.0615..0.0638 rows=20)
      -> Single-row index lookup on u using PRIMARY (user_id=p.user_id)  (rows=1 loops=20)
```

**약 12,000배 차이.** 시간보다 중요한 것은 구조 변화다.
- `Index range scan ... rows=20` — post를 **20행만** 읽고 멈춘다 → **LIMIT 조기 종료 복원**
- `Single-row index lookup on u ... loops=20` — 행마다 user를 PK로 1건씩 조회(중첩 루프)
- **Sort 노드가 사라짐** — 인덱스 순서가 유지되므로 별도 정렬이 불필요

**결론: `join fetch p.user`는 프로덕션에서 안전하다.** 커서 페이징의 O(1) 특성이 유지되며, N+1 개선은 회귀를 만들지 않았다.

> ⚠️ 정정: 중간 단계로 시도한 `SET SESSION optimizer_switch='hash_join=off'`는 **효과가 없었다.**
> MySQL 8.0.19부터 `hash_join` 플래그가 제거되어 무시된다(실행계획이 그대로 hash join). 제어하려면 `/*+ NO_BNL(p, u) */` 힌트를 써야 한다.
> 판정은 유저 분산(아래) 결과만으로 충분히 명확했다.

### 원인 — 시딩 데이터의 한계
```
SELECT COUNT(*) FROM user;                  → 1
SELECT COUNT(DISTINCT user_id) FROM post;   → 1
```
`user` 테이블에 **1행뿐**이라, 실행계획에 `Table scan on u (rows=1)`로 잡힌다.
옵티마이저는 "user는 1행이니 해시 조인이 싸다"고 판단해 이 계획을 골랐을 가능성이 크다.

실서비스처럼 작성자가 다양하면 **중첩 루프 조인 + user PK 조회**를 선택해,
post를 인덱스 순서로 읽으며 20개에서 멈추는 계획이 살아날 것으로 예상된다.

### 왜 그냥 넘기면 안 되는가
이 [A] 쿼리 형태는 **현재 프로덕션 코드가 실제로 사용하는 쿼리**다.
N+1 개선 때 `PostRepository`에 `join fetch p.user`를 추가하면서 만들어진 형태이며([`N_PLUS_1_FIX.md`](N_PLUS_1_FIX.md)),
쿼리 **개수**는 63→5로 줄였지만 목록 쿼리 **자체의 지연**은 아직 측정하지 않았다.

- 데이터 아티팩트라면 → 문제 없음, 단 검증 필요
- 구조적 문제라면 → `join fetch`가 커서 페이징의 이점을 상쇄하는 회귀

---

## 다음 검증 (비파괴적)

해시 조인만 끄고 중첩 루프 계획의 성능을 보면 원인이 갈린다. 데이터를 건드리지 않고 세션 설정만 바꾼다.

```sql
SET SESSION optimizer_switch='hash_join=off';
EXPLAIN ANALYZE
SELECT p.post_id, p.title, p.content, p.created_at, p.user_id, u.nickname, u.profile_image_url
FROM post p JOIN user u ON u.user_id = p.user_id
WHERE p.deleted_at IS NULL AND p.post_id < 1000000
ORDER BY p.post_id DESC LIMIT 20;
```

- **1ms 미만이 나오면** → 시딩 데이터(유저 1명) 탓. 프로덕션에선 문제없을 가능성 높음.
- **여전히 수백 ms면** → 쿼리 구조 문제. `join fetch` 유지 여부를 재검토해야 함.

추가 검증안:
- 유저를 N명으로 늘려 `post.user_id`를 분산시킨 뒤 [A] 재측정 (실서비스에 더 가까운 조건)
- k6로 엔드포인트 지연(avg/p95) before/after 측정 — 쿼리 수 개선이 체감 지연으로 이어졌는지 확인

---

## 요약

| 항목 | 결과 |
|---|---|
| 1차 측정 | `SELECT post_id`만 조회 → 커버링 성립 → **무효** |
| 복합 인덱스 (실제 컬럼) | **채택되지 않음**, 개선 없음 → **추가 불필요** (DROP 원복) |
| 조인 쿼리 [A] (유저 1명) | 해시 조인 + 전체 정렬로 **597~1084ms**, LIMIT 조기 종료 무력화 |
| 조인 쿼리 [A] (유저 1000명) | **0.091ms**, Nested loop + PK 조회, 20행만 스캔 → **정상** |
| 판정 | **시딩 데이터 아티팩트로 확정.** `join fetch` 유지해도 안전 |
| 남은 한계 | 유저 1명 조건에서 측정한 N+1 Before(63)는 작성자 로딩이 1회만 잡힘 → 실제로는 더 컸을 것 |
| 다음 할 일 | 다중 유저 조건에서 N+1 재측정(선택), k6 엔드포인트 지연 측정 |
