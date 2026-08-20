#!/usr/bin/env bash
# 실험 3: (deleted_at, post_id) 복합 인덱스 효과 측정.
# 커서 쿼리를 인덱스 추가 전/후로 EXPLAIN ANALYZE 비교한다.
#
# 두 가지 쿼리 형태로 교차 확인한다:
#   [B] post 컬럼만 (조인 없음)  — 인덱스로 인한 테이블 룩업 발생 여부를 깨끗이 관측
#   [A] 작성자 join 포함         — 실제 프로덕션 목록 쿼리와 동일한 형태
#
# 주의: 초기 버전은 `SELECT post_id`만 조회해 커버링 인덱스가 성립했으나,
#       실제 목록 쿼리는 title·content·created_at·user_id + user 조인이 필요하므로
#       커버링이 성립하지 않는다. 그래서 아래 두 형태로 다시 측정한다.
#
# 사용법: ./benchmark/index_experiment.sh [size]
#   ./benchmark/index_experiment.sh 20
#
# 전제: seed.sh로 데이터 시딩됨. 실행 후 인덱스는 유지되므로 필요 시 DROP(마지막 안내 참고).
set -euo pipefail
SIZE="${1:-20}"
# benchpass: 로컬 벤치 전용 컨테이너의 더미 비밀번호 (실서비스와 무관, 노출돼도 위험 없음)
MYSQL="docker exec -i -e MYSQL_PWD=benchpass bench-mysql mysql -uroot ktb_community"
IDX="idx_post_deleted_id"

MAXID=$($MYSQL -N -e "SELECT MAX(post_id) FROM post;")
# 얕은/중간/깊은 커서 지점 (post_id 값)
CURSORS=($MAXID $((MAXID/2)) 100000)

# [B] post 컬럼만 — 조인 비용 없이 인덱스 영향만 관측
sql_b() {
  echo "SELECT p.post_id, p.title, p.content, p.created_at, p.user_id
        FROM post p
        WHERE p.deleted_at IS NULL AND p.post_id < $1
        ORDER BY p.post_id DESC LIMIT ${SIZE}"
}

# [A] 작성자 join 포함 — 실제 프로덕션 목록 쿼리 형태
sql_a() {
  echo "SELECT p.post_id, p.title, p.content, p.created_at, p.user_id, u.nickname, u.profile_image_url
        FROM post p JOIN user u ON u.user_id = p.user_id
        WHERE p.deleted_at IS NULL AND p.post_id < $1
        ORDER BY p.post_id DESC LIMIT ${SIZE}"
}

# 워밍업 1회(버림) 후 EXPLAIN ANALYZE 측정 → 콜드 캐시 노이즈 제거
run() {
  local sql="$1"
  $MYSQL -N -e "$sql;" >/dev/null
  $MYSQL -e "EXPLAIN ANALYZE $sql;"
}

measure_phase() {
  local label="$1"
  echo
  echo "================= ${label} ================="
  for c in "${CURSORS[@]}"; do
    echo "--- [B] post 컬럼만 | cursor=${c} ---"
    run "$(sql_b "$c")"
    echo "--- [A] 작성자 join | cursor=${c} ---"
    run "$(sql_a "$c")"
  done
}

echo ">>> size=${SIZE}, max_post_id=${MAXID}"
echo ">>> [B] post 컬럼만 / [A] 작성자 join 포함 — 두 형태 교차 확인"

echo
echo "================= 현재 인덱스 상태 ================="
$MYSQL -e "SHOW INDEX FROM post;" | awk '{print $3, $5}'

# 이전 실행에서 만든 인덱스가 있으면 제거하고 순수 BEFORE부터 시작
$MYSQL -e "DROP INDEX ${IDX} ON post;" 2>/dev/null && echo "(기존 ${IDX} 제거 후 시작)" || true

measure_phase "BEFORE (PK 단독)"

echo
echo "================= 인덱스 추가 ================="
$MYSQL -e "CREATE INDEX ${IDX} ON post (deleted_at, post_id);"
echo "생성됨: ${IDX} (deleted_at, post_id)"

measure_phase "AFTER (복합 인덱스)"

echo
echo ">>> 비교 포인트"
echo "    1) 실행계획이 'using PRIMARY' → 'using ${IDX}'로 바뀌는가 (옵티마이저 채택 여부)"
echo "    2) 채택됐다면 'Covering index'가 아니라 인덱스 스캔 + 테이블 룩업 형태인지"
echo "    3) actual time이 실제로 줄어드는가 (룩업 비용으로 되레 늘 수도 있음)"
echo "    4) [B]와 [A]의 경향이 일치하는가 (교차 확인)"
echo ">>> 인덱스 제거: docker exec -e MYSQL_PWD=benchpass bench-mysql \\"
echo "    mysql -uroot ktb_community -e \"DROP INDEX ${IDX} ON post;\""
