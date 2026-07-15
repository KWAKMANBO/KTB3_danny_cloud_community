#!/usr/bin/env bash
# OFFSET vs 커서 페이징 성능 비교 (DB 레벨, EXPLAIN ANALYZE).
# 같은 "깊이(depth)"에서 두 방식이 실제로 검사하는 행 수(rows examined)와
# 실행 시간(actual time)을 비교한다. 커서의 핵심 강점 = depth와 무관하게 상수.
#
# 사용법: ./benchmark/compare_pagination.sh [size]
#   ./benchmark/compare_pagination.sh 20
#
# 전제: seed.sh 로 게시글이 시딩되어 있어야 함 (post.post_id = auto_increment).
set -euo pipefail
SIZE="${1:-20}"
DEPTHS=(0 1000 10000 100000 500000 900000)

# MYSQL_PWD로 비밀번호 전달 → "password on command line insecure" 경고 억제
MYSQL="docker exec -i -e MYSQL_PWD=benchpass bench-mysql mysql -uroot ktb_community"

MAXID=$($MYSQL -N -e "SELECT MAX(post_id) FROM post;")
echo ">>> size=${SIZE}, max_post_id=${MAXID}"
echo ">>> OFFSET: 나이브 페이징(LIMIT/OFFSET). CURSOR: WHERE post_id < ? 커서 페이징."
echo

for D in "${DEPTHS[@]}"; do
  CURSOR=$(( MAXID - D ))
  echo "================================ depth=${D} ================================"

  echo "--- [OFFSET] LIMIT ${SIZE} OFFSET ${D} ---"
  $MYSQL -e "EXPLAIN ANALYZE
    SELECT post_id FROM post
    WHERE deleted_at IS NULL
    ORDER BY post_id DESC
    LIMIT ${SIZE} OFFSET ${D};"

  echo "--- [CURSOR] WHERE post_id < ${CURSOR} LIMIT ${SIZE} ---"
  $MYSQL -e "EXPLAIN ANALYZE
    SELECT post_id FROM post
    WHERE deleted_at IS NULL AND post_id < ${CURSOR}
    ORDER BY post_id DESC
    LIMIT ${SIZE};"
  echo
done

echo ">>> 해석 포인트: OFFSET 행은 'rows examined'가 depth+size 만큼 증가(전부 스캔 후 버림),"
echo "    CURSOR 행은 depth와 무관하게 size 근처로 일정. actual time(마지막 줄 실측)을 비교하라."
