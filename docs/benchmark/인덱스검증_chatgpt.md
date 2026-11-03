# `(deleted_at, post_id)` 복합 인덱스 교차검증

## 1. 검증 배경

게시글 커서 조회는 다음 조건을 사용한다.

```sql
WHERE deleted_at IS NULL
  AND post_id < ?
ORDER BY post_id DESC
LIMIT 20
```

초기 실험에서는 아래 복합 인덱스가 삭제된 게시글을 건너뛰어 조회 성능을 개선할 것이라고 가정했다.

```sql
CREATE INDEX idx_post_deleted_id
    ON post (deleted_at, post_id);
```

그러나 최초 실험 쿼리는 `SELECT post_id`만 조회했다. 이 경우 쿼리에 필요한 모든 컬럼이 복합 인덱스 안에 있으므로 인위적으로 **커버링 인덱스**가 성립한다.

```sql
SELECT post_id
FROM post
WHERE deleted_at IS NULL
  AND post_id < ?
ORDER BY post_id DESC
LIMIT 20;
```

실제 API에서는 `title`, `content`, `created_at`, `user_id` 등 Post의 다른 컬럼과 작성자 정보도 필요하다. 따라서 최초 실험에서 관측한 커버링 효과가 실제 API에서도 발생하는지 확인하기 위해 쿼리를 두 형태로 나누어 교차검증했다.

## 2. 교차검증 쿼리

### B: Post 컬럼만 조회

복합 인덱스가 커버링되지 않도록 실제 응답에 필요한 Post 컬럼을 함께 조회한다.

```sql
SELECT p.post_id,
       p.title,
       p.content,
       p.created_at,
       p.user_id
FROM post p
WHERE p.deleted_at IS NULL
  AND p.post_id < ?
ORDER BY p.post_id DESC
LIMIT 20;
```

### A: 작성자 JOIN 포함

실제 `PostRepository`의 `join fetch p.user`에 가까운 형태로 작성자 정보까지 조회한다.

```sql
SELECT p.post_id,
       p.title,
       p.content,
       p.created_at,
       p.user_id,
       u.nickname,
       u.profile_image_url
FROM post p
JOIN user u ON u.user_id = p.user_id
WHERE p.deleted_at IS NULL
  AND p.post_id < ?
ORDER BY p.post_id DESC
LIMIT 20;
```

각 쿼리는 `cursor=1,000,000`, `500,000`, `100,000`에서 측정했다. BEFORE는 PK만 존재하는 상태이고, AFTER는 `(deleted_at, post_id)` 복합 인덱스를 추가한 상태다.

## 3. 측정 결과

### 3.1 요약

| cursor | B BEFORE | B AFTER | A BEFORE | A AFTER | 복합 인덱스 채택 |
|---:|---:|---:|---:|---:|:---:|
| 1,000,000 | 0.0713 ms | 0.0517 ms | 597 ms | 587 ms | 아니오 |
| 500,000 | 0.0738 ms | 0.0527 ms | 309 ms | 292 ms | 아니오 |
| 100,000 | 0.0495 ms | 0.0739 ms | 68.6 ms | 67.1 ms | 아니오 |

모든 AFTER 실행계획에서 MySQL은 새 복합 인덱스가 아닌 기존 PK를 계속 사용했다.

```text
Index range scan on p using PRIMARY
```

기대했던 다음 실행계획은 한 번도 나타나지 않았다.

```text
Index range scan on p using idx_post_deleted_id
```

따라서 BEFORE와 AFTER의 시간 차이는 복합 인덱스의 효과로 볼 수 없으며, 캐시와 실행 환경에 따른 측정 편차로 해석해야 한다.

## 4. B: Post 컬럼만 조회 결과 해석

B 쿼리는 모든 cursor에서 PK 범위 스캔을 사용했다.

```text
Limit: 20
  -> Filter: deleted_at IS NULL AND post_id < ?
    -> Index range scan on p using PRIMARY (reverse)
```

MySQL은 PK에서 cursor 위치를 바로 찾고 역순으로 읽은 뒤, 노출 게시글 20개가 모이면 실행을 종료했다. 실제 스캔 행 수도 20행으로 측정됐다.

cursor 500,000에서는 시간이 `0.0738ms → 0.0527ms`로 줄었지만, 양쪽 모두 동일하게 PK를 사용했다. 반대로 cursor 100,000에서는 `0.0495ms → 0.0739ms`로 증가했다. 이처럼 개선과 악화가 섞여 있고 실행계획도 동일하므로 복합 인덱스 효과가 아니라 반복 측정 편차다.

결과적으로 Post 컬럼만 조회하는 커서 쿼리는 PK만으로도 약 `0.05~0.07ms` 수준으로 충분히 빠르다.

## 5. A: 작성자 JOIN 포함 결과 해석

A 쿼리에서는 MySQL이 단순한 PK seek 후 20행 조인을 선택하지 않았다. 다음과 같이 많은 후보 행을 처리한 뒤 정렬하고 LIMIT을 적용했다.

```text
Limit: 20
  -> Sort: p.post_id DESC
    -> Stream results
      -> Inner hash join
        -> Table scan on user
        -> Hash
          -> Filter
            -> Index range scan on post using PRIMARY
```

### cursor 1,000,000

- PK 스캔: 999,999행
- 삭제 필터 후: 750,000행
- hash join: 750,000행
- 정렬 후 반환: 20행
- 총 시간: 597ms

### cursor 500,000

- PK 스캔: 499,999행
- 삭제 필터 후: 375,000행
- hash join: 375,000행
- 총 시간: 309ms

### cursor 100,000

- PK 스캔: 99,999행
- 삭제 필터 후: 75,000행
- hash join: 75,000행
- 총 시간: 68.6ms

AFTER에서도 같은 실행계획이 유지됐으며 복합 인덱스는 선택되지 않았다. `597→587ms`, `309→292ms`, `68.6→67.1ms`의 차이는 실행계획이 동일하므로 인덱스 개선 효과라고 주장할 수 없다.

## 6. 최초 커버링 인덱스 실험의 정정

최초 실험에서는 cursor 100,000에서 다음 실행계획이 관측됐다.

```text
Covering index range scan on post using idx_post_deleted_id
```

이 결과는 `SELECT post_id`만 사용해 쿼리에 필요한 컬럼이 모두 인덱스에 포함됐기 때문에 발생했다. 실제 API처럼 Post의 나머지 컬럼과 작성자 정보를 조회하면 `(deleted_at, post_id)`만으로는 커버링 인덱스가 될 수 없다.

따라서 다음 기존 해석은 실제 API 성능 개선 근거로 사용할 수 없다.

> cursor 100,000에서 복합 인덱스로 약 40% 개선됐다.

교차검증 결과 실제 조회 형태에서는 복합 인덱스가 단 한 번도 선택되지 않았다.

## 7. 최종 결론

### 복합 인덱스

- 실제 조회 컬럼을 반영하면 `(deleted_at, post_id)`는 커버링 인덱스가 아니다.
- B와 A 모두 MySQL 옵티마이저가 복합 인덱스를 선택하지 않았다.
- Post 단독 커서 조회는 PK만으로 약 `0.05~0.07ms`로 이미 충분히 빠르다.
- 복합 인덱스는 INSERT·UPDATE·DELETE 시 유지 비용과 저장 공간을 추가로 소비한다.
- 현재 워크로드에서는 복합 인덱스를 추가할 근거가 없으므로 실험 후 DROP하고 운영 스키마에 반영하지 않는다.

### 별도로 발견된 JOIN 병목

작성자 JOIN 쿼리에서는 hash join과 정렬로 인해 최대 75만 행을 처리하고 약 597ms가 소요됐다. 이는 복합 인덱스 검증과 별개의 병목이다.

다만 현재 벤치 데이터는 100만 게시글이 모두 동일한 `user_id=1`을 사용하므로, 이 데이터 분포가 옵티마이저의 hash join 선택에 영향을 줬을 수 있다. 따라서 바로 `join fetch` 구현이 잘못됐다고 단정해서는 안 된다.

다음 단계에서는 아래 항목을 별도로 검증한다.

1. 여러 작성자로 분산된 현실적인 데이터 재시딩
2. Hibernate가 실제 생성한 SQL을 그대로 `EXPLAIN ANALYZE`
3. `Post 20건을 먼저 제한한 뒤 User를 조인`하는 서브쿼리 또는 2단계 조회와 비교

## 8. 재현 및 원복

교차검증 실행:

```bash
./docs/benchmark/index_experiment.sh 20
```

실험 후 인덱스 제거:

```bash
docker exec -e MYSQL_PWD=benchpass bench-mysql \
  mysql -uroot ktb_community \
  -e "DROP INDEX idx_post_deleted_id ON post;"
```

