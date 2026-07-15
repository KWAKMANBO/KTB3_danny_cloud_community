#!/usr/bin/env bash
# 게시글/카운트 대량 시딩. 사용법: ./benchmark/seed.sh [건수] [user_id] [삭제%]
#   ./benchmark/seed.sh 100000            # 10만 건, 삭제 25%
#   ./benchmark/seed.sh 1000000 1 30      # 100만 건, user_id=1, 삭제 30%
#   ./benchmark/seed.sh 1000000 1 0       # 삭제 없이 100만 건
set -euo pipefail
COUNT="${1:-100000}"
USER_ID="${2:-1}"
DELETED_PCT="${3:-25}"   # soft-delete 비율(%). (deleted_at, id) 인덱스 효과 관측용

echo ">>> 시딩 시작: ${COUNT} 건 (user_id=${USER_ID}, 삭제 ${DELETED_PCT}%)"
START=$(date +%s)

docker exec -i -e MYSQL_PWD=benchpass bench-mysql mysql --default-character-set=utf8mb4 -uroot ktb_community <<SQL
SET @target = ${COUNT};
SET @uid = ${USER_ID};
SET @del_pct = ${DELETED_PCT};

-- 기존 데이터 제거 (유저는 유지). count가 post를 FK 참조하므로 count 먼저 삭제
DELETE FROM \`count\`;
DELETE FROM post;
ALTER TABLE post AUTO_INCREMENT = 1;

-- 0~9 자리수 6개를 교차조인 → 최대 1,000,000까지 tally 생성 (재귀 없음)
-- deleted_at: MOD(n,100) < del_pct 인 행을 soft-delete 처리 → 정확히 del_pct% 삭제
INSERT INTO post (user_id, title, content, created_at, updated_at, deleted_at)
SELECT @uid,
       CONCAT('벤치 게시글 ', n),
       CONCAT('성능 측정용 더미 본문입니다. post number ', n),
       TIMESTAMPADD(SECOND, n, '2025-01-01 00:00:00'),
       TIMESTAMPADD(SECOND, n, '2025-01-01 00:00:00'),
       IF(MOD(n, 100) < @del_pct, TIMESTAMPADD(SECOND, n, '2025-06-01 00:00:00'), NULL)
FROM (
  WITH digits AS (
    SELECT 0 AS N UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
    UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7
    UNION ALL SELECT 8 UNION ALL SELECT 9
  )
  SELECT (d0.N + d1.N*10 + d2.N*100 + d3.N*1000 + d4.N*10000 + d5.N*100000 + 1) AS n
  FROM digits d0, digits d1, digits d2, digits d3, digits d4, digits d5
) t
WHERE t.n <= @target
ORDER BY t.n;

-- 게시글마다 count 1:1 생성 (조회/좋아요/댓글 수는 랜덤). 삭제 글에도 존재해도 무방
INSERT INTO \`count\` (post_id, comment_count, like_count, view_count)
SELECT post_id, FLOOR(RAND()*10), FLOOR(RAND()*50), FLOOR(RAND()*500) FROM post;

SELECT COUNT(*) AS seeded_posts_total FROM post;
SELECT COUNT(*) AS visible_posts FROM post WHERE deleted_at IS NULL;
SELECT COUNT(*) AS deleted_posts FROM post WHERE deleted_at IS NOT NULL;
SELECT COUNT(*) AS seeded_counts FROM \`count\`;
SQL

END=$(date +%s)
echo ">>> 시딩 완료: $((END-START))초 소요"
