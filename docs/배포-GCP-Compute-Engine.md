# 배포 가이드 — GCP Compute Engine(Always Free) + Docker Compose

> 대상: Resumaker 프로토타입을 GCP에 **초기 비용 0**에 가깝게, 빠르게 올리기.
> 전제(이미 결정됨):
> - **배포 타깃:** Compute Engine `e2-micro`(Always Free) VM 1대 + 기존 `docker compose`
> - **HTTPS:** Cloudflare Tunnel (포트 개방·인증서 발급 불필요)
> - **배포 AI 인증:** `ANTHROPIC_API_KEY`(종량). 로컬은 무료 구독 토큰(`CLAUDE_CODE_OAUTH_TOKEN`)으로 분리 유지.
> - GCP 프로젝트는 생성 완료 상태.

---

## 0. 아키텍처 한눈에

```
[브라우저]
   │  HTTPS (Cloudflare가 종단)
   ▼
[Cloudflare Edge] ── Tunnel ──►  [e2-micro VM] (외부 포트 개방 없음)
                                   └ docker compose
                                       ├ frontend (nginx, :80)   ← app.<도메인>
                                       ├ backend  (Spring, :8082) ← api.<도메인>
                                       ├ postgres (:5432, 볼륨 영속)
                                       ├ redis    (:6379, 휘발)
                                       └ seed     (일회성)

이미지 빌드는 VM에서 하지 않음:
[Cloud Build] ──► [Artifact Registry] ──(pull)──► VM (BACKEND_IMAGE / FRONTEND_IMAGE)
```

**왜 이 구조인가 (핵심 제약):**
- `@Scheduled` 생성 워커 + 비동기 작업(≤120s) → **상시 가동 인스턴스 필요** → VM이 적합(서버리스 scale-to-zero 회피).
- `e2-micro`는 **RAM 1GB**로 빠듯함 → **이미지 빌드를 VM에서 하면 OOM**. 그래서 Cloud Build로 빌드해 Artifact Registry에 올리고 VM은 **pull만** 한다.
- 쿠키 인증(`Secure; SameSite=None`)은 **양쪽 HTTPS 필수** → Cloudflare Tunnel이 처리.

---

## 1. 역할 분담 — AI가 하는 일 vs 당신이 하는 일

| 구분 | AI(Claude 세션)가 대신 함 | 당신이 직접 해야 함 (대체 불가) |
|---|---|---|
| 빌드 파이프라인 | `cloudbuild.yaml`, Artifact Registry 푸시용 설정 작성 | Cloud Build/Artifact Registry **API 활성화**, 빌드 트리거 실행 |
| 배포 설정 | `compose.prod.yaml`(override), `.env.prod` 템플릿, VM 부트스트랩 스크립트 작성 | 시크릿 **실제 값 입력**(API 키·DB 비번), 스크립트 실행 |
| GCP 리소스 | `gcloud` 명령 시퀀스 **작성**(복붙용) | **결제 연결**, `gcloud auth login`, VM/방화벽 **생성** |
| HTTPS | Cloudflare Tunnel 설정(`config.yml`)·compose 연동 작성 | Cloudflare **계정·토큰 발급**, 도메인 연결, `cloudflared` 로그인 |
| AI 인증 | env 주입 방식 정리 | **Anthropic API 키 발급·결제수단 등록** |
| 운영/디버그 | 로그 붙여넣으면 원인 분석·수정안 제시 | 로그 수집(`docker compose logs`), 수정 반영 실행 |

> 요약: **자격증명·결제·대화형 로그인·콘솔 클릭·실행(run)** 은 당신 몫. **그 외 모든 텍스트 산출물(설정·스크립트·명령어·디버깅)** 은 AI가 만든다.

---

## 2. 당신이 직접 해야 하는 액션 (순서대로)

> 대화형 로그인(`gcloud auth login`, `cloudflared login`)은 이 세션 프롬프트에 **`! 명령어`** 형태로 입력하면 출력이 바로 세션에 들어와 진행이 매끄러워.

### Phase 0 — 사전 준비 (계정·키)

> **순서 주의:** `gcloud services enable`(API 활성화)은 **로그인 + 프로젝트 지정 + 결제 연결**이 먼저 돼 있어야 동작한다. 아래 1→5 순서를 지킬 것.
> **터미널:** 모든 `gcloud` 명령은 **로컬 터미널(Windows면 PowerShell, 또는 설치 시 추가되는 "Google Cloud SDK Shell")** 에서 실행. 로컬 설치 없이 하려면 GCP 콘솔의 브라우저 **Cloud Shell**에서 동일하게 실행해도 된다.
> ⚠️ **이 문서의 명령은 두 종류다.** ` ```powershell `로 표시된 블록은 **로컬 Windows PowerShell**에서, ` ```bash `로 표시된 블록은 **VM에 SSH로 접속한 뒤(Debian)** 실행한다. 헷갈리면 라벨을 보면 된다.
> ⚠️ **PowerShell에선 줄바꿈 `\`(역슬래시)가 안 먹힌다.** 그래서 로컬 명령은 전부 **한 줄**로 적어 뒀다(그대로 복붙). 직접 여러 줄로 쪼개야 하면 `\` 대신 줄 끝에 백틱(`` ` ``)을 쓴다. 반대로 VM(bash) 블록의 `\`·`&&`는 정상 동작한다.

- [ ] **1. gcloud CLI 설치**(Windows): Google Cloud SDK 설치 후 **새 PowerShell 창**에서 `gcloud --version`으로 인식 확인.
- [ ] **2. 결제 계정 연결**: Always Free도 프로젝트에 **결제 계정이 연결돼 있어야** 함. GCP 콘솔 → 결제에서 카드 등록·프로젝트 연결(브라우저). (무료 한도 내면 청구 0)
- [ ] **3. 로그인 + 프로젝트 지정**(로컬 PowerShell):
  ```powershell
  gcloud auth login                          # ← 대화형(브라우저). 세션에선 `! gcloud auth login`
  gcloud config set project <YOUR_PROJECT_ID>
  ```
- [ ] **4. API 활성화** (위 3까지 끝난 뒤라야 통과 / 로컬 PowerShell):
  ```powershell
  gcloud services enable compute.googleapis.com artifactregistry.googleapis.com cloudbuild.googleapis.com
  ```
- [ ] **5. Anthropic API 키 발급**: https://console.anthropic.com → API Keys에서 키 생성, 결제수단/잔액 등록. 키 문자열 안전 보관(나중에 `.env.prod`에 입력).
- [ ] **Cloudflare 무료 계정** 생성. (도메인 연결 방식은 Phase 4 참고)

### Phase 1 — 이미지 빌드 & 푸시 (Cloud Build → Artifact Registry)
> `cloudbuild.yaml`은 **작성 완료**(저장소 루트). 당신은 레지스트리 1회 생성 + 빌드 실행만.
- [ ] Artifact Registry 저장소 생성(1회 / 로컬 PowerShell):
  ```powershell
  gcloud artifacts repositories create resumaker --repository-format=docker --location=us-central1
  ```
- [ ] 빌드·푸시 실행(코드 변경 때마다 / 로컬 PowerShell):
  ```powershell
  gcloud builds submit --config cloudbuild.yaml .
  # 태그 지정 시:  gcloud builds submit --config cloudbuild.yaml --substitutions=_TAG=v1 .
  ```
  → `us-central1-docker.pkg.dev/<PROJECT_ID>/resumaker/backend:latest`, `.../frontend:latest` 생성. 이 경로를 `.env.prod`의 `BACKEND_IMAGE`/`FRONTEND_IMAGE`에 넣는다.
  > 푸시 권한 오류 시: 빌드 서비스 계정(`<번호>-compute@developer.gserviceaccount.com`)에 `roles/artifactregistry.writer` 부여(명령은 `cloudbuild.yaml` 주석에 있음).

### Phase 2 — VM 생성
- [ ] **반드시 무료 리전**(`us-west1`/`us-central1`/`us-east1`) 중 하나에 `e2-micro` 생성(로컬 PowerShell, 한 줄):
  ```powershell
  gcloud compute instances create resumaker --zone=us-central1-a --machine-type=e2-micro --image-family=debian-12 --image-project=debian-cloud --boot-disk-size=30GB
  ```
  > 외부 포트는 열지 않는다(Cloudflare Tunnel이 아웃바운드로 연결). 80/443/8082 방화벽 개방 불필요 → 공격면 축소.
- [ ] SSH 접속 확인(로컬 PowerShell): `gcloud compute ssh resumaker --zone=us-central1-a`

### Phase 3 — VM 부트스트랩 (Docker + 실행)
> 스크립트·설정 파일 **작성 완료**: `compose.prod.yaml`, `.env.prod.example`, `scripts/deploy/vm-setup.sh`, `scripts/deploy/deploy.sh`. 당신은 VM에 파일 복사 → 셋업 실행 → 시크릿 입력 → 배포.

- [ ] **필요한 파일만 VM으로 복사**(이미지는 prebuilt라 전체 소스 불필요). 로컬 PowerShell(저장소 루트에서 실행). **대상 폴더를 먼저 만든다** — Windows gcloud는 `pscp`를 쓰는데, 여러 항목을 한 대상으로 복사할 땐 그 폴더가 원격에 미리 있어야 한다(없으면 `not a directory` 오류). 또한 **scp 대상엔 `~/`(틸드)를 쓰지 말 것** — `pscp.exe`가 틸드를 확장하지 못해 같은 오류가 난다. 상대경로 `resumaker/`(원격 홈 기준)를 쓴다:
  ```powershell
  gcloud compute ssh resumaker --zone=us-central1-a --command="mkdir -p ~/resumaker"
  gcloud compute scp --zone=us-central1-a --recurse compose.yaml compose.prod.yaml .env.prod.example scripts resumaker:resumaker/
  ```
  > 그래도 `not a directory`면 틸드 대신 절대경로(`resumaker:/home/<원격사용자>/resumaker/`)로. 원격 사용자명은 `gcloud compute ssh resumaker --zone=us-central1-a --command="whoami"` 로 확인.
  > Windows 체크아웃이라 줄바꿈(CRLF) 깨질 수 있음 → VM에서 한 번: `sed -i 's/\r$//' ~/resumaker/scripts/deploy/*.sh`
- [ ] **VM 1회 셋업**(Docker + 2GB 스왑 + Artifact Registry 인증). VM SSH 후(아래는 VM의 bash):
  ```bash
  cd ~/resumaker && bash scripts/deploy/vm-setup.sh
  ```
  실행 후 **재접속**(docker 그룹 적용). pull 권한 오류 시 VM 서비스 계정에 `roles/artifactregistry.reader` 부여.
- [ ] **`.env.prod` 작성** — `.env.prod.example` 복사 후 **시크릿 직접 입력**:
  ```bash
  cd ~/resumaker && cp .env.prod.example .env.prod && nano .env.prod
  ```
  - `ANTHROPIC_API_KEY`(Phase 0 발급), `CLAUDE_CODE_OAUTH_TOKEN=`(비움), `DB_PASSWORD`(강한 값)
  - `BACKEND_IMAGE`/`FRONTEND_IMAGE`(`<PROJECT_ID>` 치환), `API_BASE`/`CORS_ALLOWED_ORIGINS`(도메인), `TUNNEL_TOKEN`(Phase 4)
- [ ] **배포**:
  ```bash
  cd ~/resumaker && bash scripts/deploy/deploy.sh
  ```
  (내부적으로 `docker compose -f compose.yaml -f compose.prod.yaml --env-file .env.prod pull && up -d --no-build`)

### Phase 4 — HTTPS (Cloudflare Tunnel)
> cloudflared는 **토큰 방식**으로 `compose.prod.yaml`에 이미 서비스로 들어있다(`TUNNEL_TOKEN`만 채우면 됨). compose 네트워크 안에서 `frontend:80`/`backend:8082`로 라우팅되므로 VM 포트 개방 불필요.
- **(권장) Cloudflare에 도메인 연결 + 명명 터널** → `app.<도메인>`/`api.<도메인>` 고정.
  - [ ] 도메인을 Cloudflare에 추가(네임서버 변경) — *도메인 보유 필요*.
  - [ ] Zero Trust 대시보드 → Networks → Tunnels → 터널 생성 → **Public Hostname 2개** 추가:
    - `app.<도메인>` → Service `http://frontend:80`
    - `api.<도메인>` → Service `http://backend:8082`
  - [ ] 터널 **토큰을 복사**해 `.env.prod`의 `TUNNEL_TOKEN`에 넣고 `deploy.sh` 재실행 → cloudflared 컨테이너가 상시 연결.
- **(도메인 없음 — 임시 데모)** compose의 `cloudflared` 서비스를 비활성(주석)하고, VM에서 quick tunnel 직접 실행:
  ```bash
  docker run --rm --network resumaker_default cloudflare/cloudflared tunnel --url http://frontend:80
  ```
  → `*.trycloudflare.com` 랜덤 URL. 단점: **재시작마다 URL 변경**, app/api 2개라 매번 `API_BASE`/`CORS` 갱신 필요 → **데모 한정**.

> ⚠️ 호스트명이 app/api로 갈리면 쿠키는 **교차 출처**라 `SameSite=None; Secure`가 필요(이미 그렇게 설계됨). 한 호스트명 뒤에서 nginx가 `/api`를 백엔드로 리버스 프록시하면 동일 출처가 되어 더 견고하지만, 그건 nginx 설정 변경(코드 작업)이라 **다른 세션 작업 종료 후 별도 진행** 권장.

### Phase 5 — 검증
- [ ] `https://api.<도메인>/`(헬스/핑) 200 확인.
- [ ] 프론트 접속 → 회원가입/로그인 시 **쿠키가 Secure로 설정**되는지 브라우저 DevTools에서 확인.
- [ ] 경험·목표·양식 CRUD 동작.
- [ ] **AI 생성 1건 실행** → 비동기 작업이 잡혀 산출물이 DB에 저장되는지(생성 워커·CLI·API 키 경로 실증).
- [ ] `docker compose logs -f backend` 로 CLI 호출 오류 없는지.

---

## 3. 생성된 배포 파일 (작성 완료)

아래 파일은 이미 만들어져 있어 바로 쓸 수 있다(앱 코드는 무수정):

| 파일 | 역할 |
|---|---|
| `cloudbuild.yaml` | 백엔드·프론트 이미지 빌드 → Artifact Registry 푸시 |
| `compose.prod.yaml` | 배포 override(pull·restart·JVM 힙 가드·cloudflared 서비스) |
| `.env.prod.example` | 배포 env 템플릿(복사해 `.env.prod`로 시크릿 입력) |
| `scripts/deploy/vm-setup.sh` | VM 1회 셋업(Docker·2GB 스왑·AR 인증) |
| `scripts/deploy/deploy.sh` | 릴리스마다 pull + up(멱등) |

운영 중 추가로 AI에게 시키면 좋은 것:
- "이 `docker compose logs` 붙여넣을게, 원인 분석해줘."
- (나중에) "nginx가 `/api`를 backend로 리버스 프록시하게 바꿔 동일 출처로 만들어줘" — 쿠키 인증을 더 견고하게(코드 변경이라 다른 세션 종료 후).

---

## 4. 비용 & 리스크 체크

| 항목 | 비용 | 메모 |
|---|---|---|
| e2-micro VM | **$0** | 무료 리전·월 1대 한도 내. 30GB 표준 디스크 무료. |
| 송신 트래픽 | ~$0 | 무료 1GB/월(북미). Cloudflare 경유라 대부분 커버. |
| Artifact Registry | ~$0 | 저장 0.5GB 무료. 이미지 정리하면 무료 유지. |
| Cloud Build | ~$0 | 일 120분 무료. |
| Cloudflare Tunnel | **$0** | 무료 플랜. |
| Anthropic API | **종량** | 유일한 실비. 생성량만큼 과금. 로컬은 무료 구독 토큰으로 분리. |

**가장 큰 리스크 — RAM 1GB.** JVM + Postgres + Redis + nginx 동시 구동이 빠듯하다. 완화책: ① 빌드는 VM 밖(Cloud Build), ② 2GB 스왑, ③ JVM `-Xmx` 제한. 그래도 OOM이 잦으면 `e2-small`(2GB, 월 약 $12)로 한 단계만 올리면 됨 — 이때도 compose는 그대로.

---

## 부록 — 로컬 vs 배포 설정 분리 원칙
- **Dockerfile은 분리하지 않는다**(양쪽 동일 이미지). 차이는 전부 **env 값**.
- 로컬: `docker compose up`(기본값) + `.env`(구독 토큰).
- 배포: `compose.yaml + compose.prod.yaml + .env.prod`(API 키·레지스트리 이미지·외부 도메인).
- 즉 AI 인증 차이는 **파일 분리가 아니라 `.env` 분리**로 해결된다.
