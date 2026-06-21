#!/usr/bin/env bash
# 자동 시드: 백엔드(Flyway)가 스키마를 만든 뒤, DB가 비어 있을 때만 seed.sql을 적용한다(멱등).
# compose의 seed 서비스가 호출한다. CRLF는 호출 측에서 제거 후 실행하므로 여기선 신경 쓰지 않는다.
set -euo pipefail

export PGPASSWORD="${DB_PASSWORD}"
PSQL="psql -h postgres -U ${DB_USERNAME} -d ${DB_NAME} -tAc"

echo "[seed] Flyway 스키마 생성 대기..."
# users 테이블이 생길 때까지(=백엔드 마이그레이션 완료) 폴링. 최대 120초.
for _ in $(seq 1 60); do
  if [ "$($PSQL "SELECT to_regclass('public.users')" 2>/dev/null || true)" = "users" ]; then
    break
  fi
  sleep 2
done

if [ "$($PSQL "SELECT to_regclass('public.users')" 2>/dev/null || true)" != "users" ]; then
  echo "[seed] 스키마를 찾지 못했습니다(백엔드 미기동?). 시드를 건너뜁니다." >&2
  exit 0
fi

count="$($PSQL "SELECT count(*) FROM users")"
if [ "$count" != "0" ]; then
  echo "[seed] users에 이미 ${count}건이 있어 시드를 건너뜁니다(멱등)."
  exit 0
fi

echo "[seed] seed.sql 적용..."
psql -v ON_ERROR_STOP=1 -h postgres -U "${DB_USERNAME}" -d "${DB_NAME}" -f /seed/seed.sql
echo "[seed] 완료."
