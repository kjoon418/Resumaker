# Resumaker

기록한 경험·경력을 바탕으로 목표 기업에 맞춘 이력서/포트폴리오를 만들어 주는 서비스.

- **Web**: Compose Multiplatform (Wasm/Canvas) — 포트 **8081**
- **Backend/API**: Kotlin Spring Boot — 포트 **8082**
- **DB**: PostgreSQL — 포트 **5432**

프로젝트 규칙·도메인 명세는 [`CLAUDE.md`](CLAUDE.md)와 [`docs/`](docs/)를 참고하세요.

---

## 사전 준비

| 무엇을 할 때 | 필요한 것 |
|---|---|
| `docker compose`로 백엔드 띄우기 | Docker Desktop(엔진) |
| 호스트에서 `bootRun`/프런트 띄우기 | JDK 21 |
| AI 생성(이력서·포트폴리오 자동완성) 사용 | `ANTHROPIC_API_KEY` |

AI 기능을 안 쓰면 키 없이도 계정·경험·목표·양식 CRUD는 정상 동작합니다.

### 환경변수(.env)

```bash
cp .env.example .env
# .env 를 열어 ANTHROPIC_API_KEY 등 필요한 값을 채운다 (.env 는 커밋되지 않음)
```

---

## 로컬 실행

### 방법 1 — `docker compose up` (전체 스택: 프런트+백엔드+DB+Redis)

```bash
docker compose up            # 프런트(8081)+백엔드(8082)+PostgreSQL+Redis가 함께 뜬다 (-d 로 백그라운드)
```

브라우저에서 <http://localhost:8081> 접속. 확인:

```bash
curl http://localhost:8082/   # 백엔드 응답 확인
docker compose logs -f backend
docker compose down           # 종료(데이터 유지) / down -v 는 데이터까지 삭제
```

> 코드를 고치면 `docker compose up --build` 로 다시 빌드해 반영합니다.

### 방법 2 — 호스트 개발 루프 (코드 자주 고칠 때, hot reload)

인프라만 컨테이너로 띄우고 서버·프런트는 Gradle로 돌리는 편이 빠릅니다.

```bash
docker compose up -d postgres redis    # DB·Redis만 먼저 기동
./gradlew :server:bootRun              # 백엔드 8082 (코드 변경 시 재시작)
# 별도 터미널 — 백엔드 완전 기동 후:
./gradlew :app:webApp:wasmJsBrowserDevelopmentRun --continuous   # 프런트 8081(hot reload)
```

브라우저 <http://localhost:8081> → 웹앱이 `http://localhost:8082`(백엔드)를 호출합니다.

> **두 Gradle 작업을 연달아 띄울 때 주의:** daemon 경합으로 먼저 뜬 프로세스가 죽을 수 있습니다.
> 백엔드가 완전히 기동된 뒤 프런트를 띄우세요.

### 포트 한눈에

| 구성요소 | 포트 | 띄우는 법 |
|---|---|---|
| 프런트(웹앱) | 8081 | `docker compose up`(nginx) 또는 `:app:webApp:wasmJsBrowserDevelopmentRun`(dev) |
| 백엔드 API | 8082 | `docker compose up` 또는 `:server:bootRun` |
| PostgreSQL | 5432 | `docker compose up`(자동) 또는 `docker compose up -d postgres` |
| Redis(세션 토큰) | 6379 | `docker compose up`(자동) 또는 `docker compose up -d redis` |

---

## 배포

같은 `compose.yaml`을 사용합니다. 배포 환경에 운영값을 `.env`(또는 셸 환경변수)로 주입하고:

```bash
docker compose up -d --build      # 서버에서 직접 빌드해 기동
```

레지스트리에 올린 이미지를 쓰면 빌드 없이 더 가볍게 띄울 수 있습니다:

```bash
# 한 번 빌드해 푸시(백엔드·프런트)
docker build -f server/Dockerfile     -t registry.example.com/resumaker-backend:1.0.0 .
docker build -f app/webApp/Dockerfile -t registry.example.com/resumaker-frontend:1.0.0 .
docker push registry.example.com/resumaker-backend:1.0.0
docker push registry.example.com/resumaker-frontend:1.0.0

# 배포 서버 .env: BACKEND_IMAGE / FRONTEND_IMAGE 지정
docker compose pull && docker compose up -d
```

배포 시 최소한 다음 값을 운영용으로 덮어쓰세요(`.env.example` 참고):

- `DB_PASSWORD`, (외부 DB면) `DB_URL`
- `API_BASE` → 브라우저가 호출할 **실제 백엔드 도메인**(예: `https://api.example.com`). 프런트 nginx가 기동 시 `config.js`로 주입한다.
- `CORS_ALLOWED_ORIGINS` → 실제 프런트 도메인으로 좁힘(예: `https://app.example.com`)
- AI 기능을 쓰면 `ANTHROPIC_API_KEY`

> **쿠키 인증 주의(중요):** 인증 쿠키는 `Secure`+`SameSite=None`이라 **운영은 HTTPS 필수**입니다(프런트·백엔드 모두).
> 프런트와 백엔드가 다른 도메인이면 위 `API_BASE`·`CORS_ALLOWED_ORIGINS`를 반드시 실제 도메인으로 지정하세요.
> 스키마는 Flyway가 관리하므로(`ddl-auto: validate`) 운영 DB는 기동 시 마이그레이션이 자동 적용됩니다.

---

## 트러블슈팅

| 증상 | 원인 / 해결 |
|---|---|
| 웹앱에 "지금은 서버와 연결할 수 없어요" | 백엔드(8082)가 안 떠 있음. 방법 1/2로 백엔드를 먼저 기동. `docker compose up`은 DB만이 아니라 백엔드까지 띄운다. |
| `docker compose` 가 DB만 띄움 | 예전 동작. 현재는 `backend` 서비스가 포함돼 함께 뜬다. 캐시면 `docker compose up --build`. |
| AI 생성만 실패(다른 기능은 정상) | `ANTHROPIC_API_KEY` 미설정. `.env`에 채우고 `docker compose up -d` 재기동. |
| 포트 충돌(8082) | 다른 백엔드 프로세스가 점유 중. 호스트 bootRun과 컨테이너 백엔드를 동시에 띄우지 말 것. |
