#!/usr/bin/env bash
# 게시글 목록 엔드포인트 1회 호출 동안 실행된 SELECT 쿼리 수 측정 (N+1 진단).
# MySQL의 Com_select 카운터를 호출 전후로 비교한다.
#
# 사용법: ./benchmark/count_queries.sh [size]
#   ./benchmark/count_queries.sh 20
#
# 전제: bench-app(8080) 기동 + benchuser 계정 존재 + bench-mysql 기동.
set -euo pipefail
SIZE="${1:-20}"
BASE="${BASE:-http://localhost:8080/api}"
# benchpass: 로컬 벤치 전용 컨테이너의 더미 비밀번호 (실서비스와 무관, 노출돼도 위험 없음)
MYSQL="docker exec -i -e MYSQL_PWD=benchpass bench-mysql mysql -N -uroot ktb_community"

# 1) 로그인 → access_token 추출
LOGIN=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"benchuser@test.com","password":"benchpass1!"}')
TOKEN=$(printf '%s' "$LOGIN" | grep -o '"access_token":"[^"]*"' | head -1 | sed 's/.*:"//;s/"//')
if [ -z "$TOKEN" ]; then
  echo "!! 로그인 실패. 응답: $LOGIN"; exit 1
fi
echo ">>> 로그인 성공 (size=${SIZE})"

# 2) 호출 전 SELECT 카운터
BEFORE=$($MYSQL -e "SHOW GLOBAL STATUS LIKE 'Com_select';" | awk '{print $2}')

# 3) 게시글 목록 1회 호출
curl -s "$BASE/posts?size=${SIZE}" -H "Authorization: Bearer $TOKEN" > /dev/null

# 4) 호출 후 SELECT 카운터
AFTER=$($MYSQL -e "SHOW GLOBAL STATUS LIKE 'Com_select';" | awk '{print $2}')

echo ">>> GET /posts?size=${SIZE} 1회 동안 실행된 SELECT 쿼리 수: $((AFTER - BEFORE))"
echo "    (JWT 필터의 유저 조회 1건 포함. size=${SIZE}에서 이 값이 size에 비례하면 N+1)"
