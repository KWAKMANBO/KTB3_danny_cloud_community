#!/usr/bin/env bash
# GET /posts?size=20 1회 호출 동안 MySQL이 실행한 실제 쿼리를 그대로 캡처한다.
# N+1 개선 Before/After의 "실물 증거" 아티팩트 생성용.
#
# 사용법: ./benchmark/capture_queries.sh <label> [size]
#   ./benchmark/capture_queries.sh before 20   → benchmark/nplus1_before.log
#   ./benchmark/capture_queries.sh after  20   → benchmark/nplus1_after.log
set -euo pipefail
LABEL="${1:?label 필요 (before|after)}"
SIZE="${2:-20}"
BASE="${BASE:-http://localhost:8080/api}"
# 실행 위치(CWD)와 무관하게 이 스크립트가 있는 디렉토리에 저장
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="${SCRIPT_DIR}/nplus1_${LABEL}.log"
# benchpass: 로컬 벤치 전용 컨테이너의 더미 비밀번호 (실서비스와 무관, 노출돼도 위험 없음)
MYSQL="docker exec -i -e MYSQL_PWD=benchpass bench-mysql mysql -N -uroot ktb_community"

# 1) 로그인 → 토큰
LOGIN=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"benchuser@test.com","password":"benchpass1!"}')
TOKEN=$(printf '%s' "$LOGIN" | grep -o '"access_token":"[^"]*"' | head -1 | sed 's/.*:"//;s/"//')
[ -z "$TOKEN" ] && { echo "!! 로그인 실패: $LOGIN"; exit 1; }

# 2) general_log를 파일로 켜기 (컨테이너 내부 /tmp/gq.log)
$MYSQL -e "SET GLOBAL general_log_file='/tmp/gq.log'; SET GLOBAL general_log='ON';" >/dev/null
docker exec bench-mysql sh -c ': > /tmp/gq.log' 2>/dev/null || true   # 로그 초기화

# 3) 측정 대상 호출 (이 구간만 로깅됨)
curl -s "$BASE/posts?size=${SIZE}" -H "Authorization: Bearer $TOKEN" >/dev/null

# 4) 로깅 끄기
$MYSQL -e "SET GLOBAL general_log='OFF';" >/dev/null

# 5) 로그에서 실제 애플리케이션 쿼리만 추출 → 호스트 파일로 저장
#    - Query 이벤트만, 로그 prefix 제거
#    - Hibernate의 /* ... */ 주석은 제외가 아니라 "떼어내고" 유지 (use_sql_comments=true)
#    - 관리/핸드셰이크 쿼리(@@, SET/SHOW, information_schema 등)만 제외
docker exec bench-mysql sh -c "cat /tmp/gq.log" \
  | grep -aE '[[:space:]]Query[[:space:]]' \
  | sed -E 's/^.*[[:space:]]Query[[:space:]]+//' \
  | sed -E 's#/\*[^*]*\*/##g; s/^[[:space:]]+//' \
  | grep -iE '(select|insert|update|delete)' \
  | grep -viE '@@|information_schema|performance_schema|^select 1[[:space:]]*$|^(set|show|commit|rollback|flush)' \
  > "$OUT" || true

TOTAL=$(wc -l < "$OUT" | tr -d ' ')
echo ">>> [$LABEL] 캡처 완료 → $OUT"
echo ">>> 캡처된 쿼리 수: $TOTAL"
echo ">>> 종류별 집계:"
sort "$OUT" | sed -E 's/[0-9]+/?/g; s/IN \([^)]*\)/IN (...)/g' | sort | uniq -c | sort -rn | head -20
