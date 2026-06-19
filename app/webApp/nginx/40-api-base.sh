#!/bin/sh
# 컨테이너 기동 시 API_BASE 환경변수로 config.js를 다시 굽는다(런타임 구성 — 이미지 재빌드 없이 환경마다 주소 변경).
# nginx:alpine 엔트리포인트가 /docker-entrypoint.d/*.sh 를 nginx 기동 전에 실행한다.
set -e
: "${API_BASE:=http://localhost:8082}"
cat > /usr/share/nginx/html/config.js <<EOF
window.__RESUMAKER_API_BASE__ = "${API_BASE}";
EOF
echo "[40-api-base] config.js API_BASE=${API_BASE}"
