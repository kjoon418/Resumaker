#!/bin/sh
# 컨테이너 기동 시 API_BASE·ADS_ENABLED·ADSENSE_CLIENT 환경변수로 config.js를 다시 굽는다(런타임 구성 — 이미지 재빌드 없이 환경마다 변경).
# nginx:alpine 엔트리포인트가 /docker-entrypoint.d/*.sh 를 nginx 기동 전에 실행한다.
set -e
: "${API_BASE:=http://localhost:8082}"
: "${ADS_ENABLED:=true}"
# AdSense 퍼블리셔 ID(예: ca-pub-1234567890). 미설정(빈 값)이면 AdSense 관련은 전부 무동작(graceful).
: "${ADSENSE_CLIENT:=}"
cat > /usr/share/nginx/html/config.js <<EOF
window.__RESUMAKER_API_BASE__ = "${API_BASE}";
window.__RESUMAKER_ADS_ENABLED__ = "${ADS_ENABLED}";
window.__RESUMAKER_ADSENSE_CLIENT__ = "${ADSENSE_CLIENT}";
EOF

# ads.txt — AdSense 무효 트래픽·도용 방지를 위해 퍼블리셔를 선언한다. ID가 있을 때만 유효 행을 굽고,
# 없으면 주석만 둔다(빈/잘못된 ads.txt로 인한 경고 방지). ads.txt의 ID는 'ca-' 접두를 뗀 pub-… 형식.
if [ -n "${ADSENSE_CLIENT}" ]; then
  pubid=$(echo "${ADSENSE_CLIENT}" | sed 's/^ca-//')
  echo "google.com, ${pubid}, DIRECT, f08c47fec0942fa0" > /usr/share/nginx/html/ads.txt
else
  echo "# ads.txt — ADSENSE_CLIENT 미설정. AdSense 퍼블리셔 ID 발급 후 환경변수로 주입하면 유효 행이 자동 생성됩니다." > /usr/share/nginx/html/ads.txt
fi

echo "[40-api-base] config.js API_BASE=${API_BASE} ADS_ENABLED=${ADS_ENABLED} ADSENSE_CLIENT=${ADSENSE_CLIENT:-(unset)}"
