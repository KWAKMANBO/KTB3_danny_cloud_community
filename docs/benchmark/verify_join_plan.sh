#!/usr/bin/env bash
# 발견 2 검증: 목록 조인 쿼리(join fetch 형태)의 해시 조인 계획이
# "유저가 1명뿐인 시딩 데이터" 때문인지 확인한다.
#
# 3단계로 원인을 좁힌다:
#   STEP 1. 현재 상태(유저 1명) 계획 — 재확인
#   STEP 2. hash_join=off 로 강제 — 중첩 루프면 빨라지는지 (비파괴적, 메커니즘 확인)
#   STEP 3. 유저를 N명으로 늘리고 post.user_id 분산 후 재측정 (현실 조건)
#
# 사용법: ./docs/benchmark/verify_join_plan.sh [유저수] [size]
#   ./docs/benchmark/verify_join_plan.sh 1000 20
#
# ⚠️ STEP 3은 데이터를 변경한다(post.user_id 분산). 원복하려면:
#   ./docs/benchmark/seed.sh 1000000 1 25    # 전 게시글을 user_id=1로 되돌림
set -euo pipefail
USERS="${1:-1000}"
SIZE="${2:-20}"
# benchpass: 로컬 벤치 전용 컨테이너의 더미 비밀번호 (실서비스와 무관, 노출돼도 위험 없음)
MYSQL="docker exec -i -e MYSQL_PWD=benchpass bench-mysql mysql -uroot ktb_community"

CURSOR=1000000   # 가장 느렸던 지점(전체 범위)

JOIN_SQL="SELECT p.post_id, p.title, p.content, p.created_at, p.user_id, u.nickname, u.profile_image_url
          FROM post p JOIN user u ON u.user_id = p.user_id
          WHERE p.deleted_at IS NULL AND p.post_id < ${CURSOR}
          ORDER BY p.post_id DESC LIMIT ${SIZE}"

echo ">>> 검증 대상: 목록 조인 쿼리 (cursor<${CURSOR}, size=${SIZE})"

# 복합 인덱스가 남아 있으면 제거 (실험 3 결론: 추가 불필요)
$MYSQL -e "DROP INDEX idx_post_deleted_id ON post;" 2>/dev/null \
  && echo ">>> idx_post_deleted_id 제거됨 (원복)" || echo ">>> 복합 인덱스 없음 (정상)"

echo
echo "============ STEP 1. 현재 상태 (유저 $($MYSQL -N -e 'SELECT COUNT(*) FROM user;')명) ============"
$MYSQL -N -e "$JOIN_SQL;" >/dev/null   # 워밍업
$MYSQL -e "EXPLAIN ANALYZE $JOIN_SQL;"

echo
echo "============ STEP 2. hash_join=off (중첩 루프 강제) ============"
$MYSQL -e "SET SESSION optimizer_switch='hash_join=off';
           $JOIN_SQL;" >/dev/null      # 워밍업
$MYSQL -e "SET SESSION optimizer_switch='hash_join=off';
           EXPLAIN ANALYZE $JOIN_SQL;"
echo ">>> 여기서 1ms 미만이면 → 해시 조인 선택이 문제. 원인은 데이터(유저 1명)일 가능성 큼."

echo
echo "============ STEP 3. 유저 ${USERS}명으로 늘리고 작성자 분산 ============"
$MYSQL -e "
-- 기존 유저(user_id=1 benchuser)는 유지하고 부족한 만큼 추가
INSERT INTO user (email, password, nickname, profile_image_url, created_at, updated_at, deleted_at)
SELECT CONCAT('bench', n, '@test.com'), 'dummy', CONCAT('bench_', n), NULL, NOW(), NOW(), NULL
FROM (
  WITH digits AS (
    SELECT 0 AS N UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
    UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7
    UNION ALL SELECT 8 UNION ALL SELECT 9
  )
  SELECT (d0.N + d1.N*10 + d2.N*100 + d3.N*1000 + 1) AS n
  FROM digits d0, digits d1, digits d2, digits d3
) t
WHERE t.n <= ${USERS} - (SELECT COUNT(*) FROM user);
"
ACTUAL_USERS=$($MYSQL -N -e "SELECT COUNT(*) FROM user;")
echo ">>> 현재 유저 수: ${ACTUAL_USERS}"

echo ">>> post.user_id 분산 중 (100만 행 UPDATE, 수십 초 소요)..."
$MYSQL -e "UPDATE post SET user_id = MOD(post_id, ${ACTUAL_USERS}) + 1;"
$MYSQL -e "SELECT COUNT(DISTINCT user_id) AS distinct_authors FROM post;"

echo
echo "--- STEP 3 재측정 (유저 ${ACTUAL_USERS}명) ---"
$MYSQL -N -e "$JOIN_SQL;" >/dev/null   # 워밍업
$MYSQL -e "EXPLAIN ANALYZE $JOIN_SQL;"

echo
echo ">>> 판정 기준"
echo "    STEP 3이 1ms 미만 + 'Nested loop' 계획 → 시딩 데이터(유저 1명) 아티팩트. 프로덕션 문제 없음."
echo "    STEP 3도 수백 ms + 'hash join' 유지     → 구조적 문제. join fetch 재검토 필요."
echo ">>> 데이터 원복: ./docs/benchmark/seed.sh 1000000 1 25"
