#!/usr/bin/env bash
# 서버(docker compose)에서 합성 데이터 측정. 저장소 루트(docker-compose.yml 이 있는 곳)에서 실행한다.
#   bash mysql_code/perf/server_measure.sh seed       # daonil_perf 스키마에 합성 3만 건 생성 (서비스 DB daonil 은 읽기만 함: 구조 복사)
#   bash mysql_code/perf/server_measure.sh base       # 인덱스 없이 측정
#   bash mysql_code/perf/server_measure.sh index      # 인덱스 추가(03_indexes.sql) 후 측정
#   bash mysql_code/perf/server_measure.sh cleanup    # daonil_perf 삭제
# DB 비밀번호는 컨테이너의 MYSQL_ROOT_PASSWORD 환경변수를 쓰므로 직접 입력하지 않는다.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
N="${N:-30000}"
REPEAT="${REPEAT:-5}"

mysql_exec() {  # stdin 으로 SQL 을 받는다. 두 번째 인자(옵션)는 DB 이름
  docker compose exec -T mysql sh -c "mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" --default-character-set=utf8mb4 -r -N ${1:-}" 2> >(grep -v 'Using a password' >&2 || true)
}

measure() {
  local label="$1"
  mkdir -p "$HERE/results"
  echo "query,median_ms,min_ms,max_ms" > "$HERE/results/server_result_${label}.csv"
  printf '%-32s %10s %8s %8s\n' query median_ms min_ms max_ms
  for f in "$HERE"/queries/*.sql; do
    local name; name="$(basename "$f" .sql)"
    local times=()
    for ((i = 0; i < REPEAT; i++)); do
      local out t
      out="$({ printf 'EXPLAIN ANALYZE '; cat "$f"; } | mysql_exec daonil_perf)"
      t="$(printf '%s' "$out" | grep -oE 'actual time=[0-9.]+\.\.[0-9.]+' | head -1 | sed 's/.*\.\.//')"
      [ "$i" -eq 0 ] && printf '%s\n' "$out" > "$HERE/results/server_plan_${label}_${name}.txt"
      times+=("$t")
    done
    local sorted; sorted="$(printf '%s\n' "${times[@]}" | sort -n)"
    local med min max
    med="$(printf '%s\n' "$sorted" | sed -n "$(( (REPEAT + 1) / 2 ))p")"
    min="$(printf '%s\n' "$sorted" | head -1)"
    max="$(printf '%s\n' "$sorted" | tail -1)"
    printf '%-32s %10s %8s %8s\n' "$name" "$med" "$min" "$max"
    echo "$name,$med,$min,$max" >> "$HERE/results/server_result_${label}.csv"
  done
}

case "${1:-}" in
  seed)
    { echo "SET @N=${N};"; cat "$HERE/01_seed_synthetic.sql"; } | mysql_exec ;;
  base)
    echo "== baseline (daonil_perf 의 현재 인덱스 상태)"; mysql_exec daonil_perf <<< "SHOW INDEX FROM job_posting;" | awk '{print $3}' | sort -u | tr '\n' ' '; echo
    measure "base_${N}" ;;
  index)
    mysql_exec < <(cat "$HERE/03_indexes.sql")
    echo "== 인덱스 적용 후"; measure "indexed_${N}" ;;
  cleanup)
    mysql_exec <<< "DROP DATABASE IF EXISTS daonil_perf;" ; echo "daonil_perf 삭제 완료" ;;
  *) echo "usage: bash $0 seed|base|index|cleanup"; exit 1 ;;
esac
