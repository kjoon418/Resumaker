# syntax=docker/dockerfile:1

# ── 빌드 스테이지: Compose Wasm 프로덕션 배포물 생성 ──
# 빌드 컨텍스트는 저장소 루트. KMP/Wasm 툴체인 + yarn 의존성 다운로드에 네트워크가 필요하다.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY . .
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew \
 && ./gradlew :app:webApp:wasmJsBrowserDistribution --no-daemon

# ── 런타임 스테이지: nginx 정적 서빙 ──
FROM nginx:1.27-alpine AS runtime
COPY --from=build /workspace/app/webApp/build/dist/wasmJs/productionExecutable /usr/share/nginx/html
COPY app/webApp/nginx/default.conf /etc/nginx/conf.d/default.conf
# 기동 시 API_BASE로 config.js를 굽는다(nginx 엔트리포인트가 자동 실행).
COPY app/webApp/nginx/40-api-base.sh /docker-entrypoint.d/40-api-base.sh
RUN chmod +x /docker-entrypoint.d/40-api-base.sh
EXPOSE 80
