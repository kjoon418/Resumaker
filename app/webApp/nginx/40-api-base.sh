#!/bin/sh
# 컨테이너 기동 시 API_BASE·ADS_ENABLED·ADSENSE_CLIENT 환경변수로 config.js를 다시 굽는다(런타임 구성 — 이미지 재빌드 없이 환경마다 변경).
# nginx:alpine 엔트리포인트가 /docker-entrypoint.d/*.sh 를 nginx 기동 전에 실행한다.
set -e
: "${API_BASE:=http://localhost:8082}"
: "${ADS_ENABLED:=true}"
# AdSense 퍼블리셔 ID(예: ca-pub-1234567890). 미설정(빈 값)이면 AdSense 관련은 전부 무동작(graceful).
: "${ADSENSE_CLIENT:=}"
# 공개 콘텐츠 표면(가이드/소개)의 절대 URL 기준. robots.txt·sitemap.xml·canonical/og 태그에 박힌다. 운영은 프런트 공개 오리진.
: "${SITE_ORIGIN:=http://localhost:8081}"
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

# index.html의 AdSense 소유권 확인 메타 태그 토큰을 치환한다. ID가 있으면 정적 <meta>로, 없으면 빈 값(태그 제거).
# 메타 태그는 정적 HTML에 박혀야 크롤러가 JS 실행 없이 읽는다(소유권 확인 = ads.txt와 병행하면 더 빠름).
if [ -n "${ADSENSE_CLIENT}" ]; then
  meta="<meta name=\"google-adsense-account\" content=\"${ADSENSE_CLIENT}\">"
else
  meta=""
fi
sed -i "s|<!--__ADSENSE_META__-->|${meta}|" /usr/share/nginx/html/index.html

# 공개 콘텐츠 표면의 __SITE_ORIGIN__ 토큰을 실제 오리진으로 치환한다(canonical·og·robots·sitemap의 절대 URL).
# 대상 파일만 정확히 돈다(webApp.js·wasm 등 대용량 자산엔 손대지 않음). 빠진 파일이 있어도 멈추지 않는다.
root=/usr/share/nginx/html
for f in robots.txt sitemap.xml guide.html guide-resume-basics.html guide-achievement.html \
         guide-portfolio.html guide-tailoring.html about.html privacy.html; do
  [ -f "${root}/${f}" ] && sed -i "s|__SITE_ORIGIN__|${SITE_ORIGIN}|g" "${root}/${f}"
done

echo "[40-api-base] config.js API_BASE=${API_BASE} ADS_ENABLED=${ADS_ENABLED} ADSENSE_CLIENT=${ADSENSE_CLIENT:-(unset)} SITE_ORIGIN=${SITE_ORIGIN}"
