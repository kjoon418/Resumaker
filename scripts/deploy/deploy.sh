#!/usr/bin/env bash
# ── 릴리스 배포 ── 최신 이미지를 pull 해서 (재)기동한다. 릴리스마다 재실행(멱등).
#
# 사용:  앱 디렉터리(compose.yaml·compose.prod.yaml·.env.prod·scripts/seed 가 있는 곳)에서
#          bash scripts/deploy/deploy.sh
# 전제:  vm-setup.sh 완료 + .env.prod 작성 완료.
set -euo pipefail

# 앱 루트로 이동(이 스크립트는 scripts/deploy/ 아래에 있다)
cd "$(dirname "$0")/../.."

if [ ! -f .env.prod ]; then
  echo "ERROR: .env.prod 가 없습니다. .env.prod.example 을 복사해 값을 채우세요." >&2
  exit 1
fi

COMPOSE=(docker compose -f compose.yaml -f compose.prod.yaml --env-file .env.prod)

echo "[1/3] 이미지 pull"
"${COMPOSE[@]}" pull

echo "[2/3] 기동(--no-build: VM에서 빌드하지 않음)"
"${COMPOSE[@]}" up -d --no-build

echo "[3/3] 상태"
"${COMPOSE[@]}" ps

echo "로그 확인:  ${COMPOSE[*]} logs -f backend"
