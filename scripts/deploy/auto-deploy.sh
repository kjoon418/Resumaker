#!/usr/bin/env bash
# ── 자동 배포(B안: 풀 기반) ── systemd 타이머가 주기적으로 호출한다.
#
# 하는 일:
#   1) 저장소를 git pull(--ff-only)로 갱신한다.        → compose.yaml·스크립트 변경 반영
#   2) deploy.sh 를 실행한다(docker compose pull + up). → :latest 이미지 갱신 반영
#
# 특징:
#   - flock 으로 폴링이 겹쳐도 중복 실행되지 않는다(이전 주기가 길어지면 이번 주기는 건너뜀).
#   - 모든 로그는 stdout 으로 남겨 journald(`journalctl -u resumaker-deploy`)에 쌓인다.
#   - 커밋 변화가 없어도 deploy.sh 는 실행한다. Cloud Build 가 :latest 를 갱신했어도
#     git HEAD 는 그대로일 수 있으므로, 이미지 pull 은 항상 의미가 있다(멱등이라 무해).
set -euo pipefail

# 저장소 루트(이 스크립트는 scripts/deploy/ 아래에 있다)
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOCK_FILE="/tmp/resumaker-deploy.lock"

log() { echo "[$(date -Is)] $*"; }

# ── 중복 실행 방지 ── 락을 못 잡으면(이전 배포 진행 중) 조용히 빠져나간다.
exec 9>"$LOCK_FILE"
if ! flock -n 9; then
  log "이전 배포가 진행 중 — 이번 주기 건너뜀"
  exit 0
fi

cd "$REPO_DIR"

before="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
log "git pull --ff-only (현재 ${before})"
git pull --ff-only
after="$(git rev-parse HEAD 2>/dev/null || echo unknown)"

if [ "$before" != "$after" ]; then
  log "코드 갱신: ${before} -> ${after}"
else
  log "코드 변화 없음(이미지 갱신만 반영될 수 있음)"
fi

log "deploy.sh 실행"
bash scripts/deploy/deploy.sh

log "자동 배포 완료"
