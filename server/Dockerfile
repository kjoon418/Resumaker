# syntax=docker/dockerfile:1

# ── 빌드 스테이지: JDK 21로 실행 가능한 bootJar 생성 ──
# 빌드 컨텍스트는 저장소 루트(compose.yaml의 backend.build.context: .)다.
# :server는 독립 JVM 모듈이라 :server:bootJar만 패키징하면 된다(KMP 프론트 모듈은 빌드하지 않음).
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY . .
# gradlew CRLF 방어(Windows 체크아웃) + 실행 권한 부여 후 서버 모듈만 패키징한다.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew \
 && ./gradlew :server:bootJar -x test --no-daemon

# ── 런타임 스테이지: JRE 21 + Node 20 + Claude CLI ──
# 산출물 AI 생성/양식 해석은 백엔드가 `claude` 실행 파일을 셸 호출(ProcessBuilder)하므로
# 런타임 이미지에 Node + @anthropic-ai/claude-code 를 설치하고 ANTHROPIC_API_KEY 로 인증한다.
# (AI 기능을 쓰지 않으면 키 없이도 계정/경험/목표/양식 CRUD는 정상 동작한다.)
FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates gnupg \
 && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
 && apt-get install -y --no-install-recommends nodejs \
 && npm install -g @anthropic-ai/claude-code \
 && npm cache clean --force \
 && apt-get purge -y gnupg \
 && apt-get autoremove -y \
 && apt-get clean \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/server/build/libs/server.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
