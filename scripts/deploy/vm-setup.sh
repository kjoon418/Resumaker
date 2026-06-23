#!/usr/bin/env bash
# ── VM 1회 셋업 ── (Debian 12 / GCE e2-micro 기준)
# 스왑 + Docker Engine/compose + Artifact Registry docker 인증을 구성한다.
#
# 사용:  bash vm-setup.sh
# 주의:  Windows에서 체크아웃해 scp로 옮겼다면 CRLF로 깨질 수 있다. 먼저 한 번:
#          sed -i 's/\r$//' vm-setup.sh deploy.sh
set -euo pipefail

REGION="${REGION:-us-central1}"

# 1) 2GB 스왑 — e2-micro RAM 1GB 보호(빌드는 안 하지만 JVM+DB 동시 구동 여유 확보)
if ! sudo swapon --show 2>/dev/null | grep -q /swapfile; then
  echo "[1/3] 2GB 스왑 생성"
  sudo fallocate -l 2G /swapfile 2>/dev/null || sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
else
  echo "[1/3] 스왑 이미 존재 — 건너뜀"
fi

# 2) Docker Engine + compose 플러그인
if ! command -v docker >/dev/null 2>&1; then
  echo "[2/3] Docker 설치"
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker "$USER"
  echo ">>> docker 그룹 적용을 위해 재접속(로그아웃 후 재SSH) 필요. 그 전엔 'newgrp docker'로 임시 적용 가능."
else
  echo "[2/3] Docker 이미 설치됨 — 건너뜀"
fi

# 3) Artifact Registry pull 인증 — VM의 서비스 계정 토큰을 docker가 쓰도록 credential helper 구성
#    pull이 권한 오류면, VM 서비스 계정에 roles/artifactregistry.reader 부여 필요(가이드 참고).
echo "[3/3] Artifact Registry docker 인증 구성 (${REGION}-docker.pkg.dev)"
gcloud auth configure-docker "${REGION}-docker.pkg.dev" -q

echo "완료. 재접속 후 앱 디렉터리에서 deploy.sh 실행."
