# CI/CD 계획 — 커밋 → 자동 빌드 → VM 자동 반영

> 상태: **B안(풀 기반) 산출물 생성 완료**. AI 산출물(`scripts/deploy/auto-deploy.sh`, `resumaker-deploy.service`, `resumaker-deploy.timer`)은 저장소에 포함됨. 남은 것은 Phase 0~1·2-B 의 **[당신]** 항목(push·Cloud Build 연결·트리거·VM git 전환·systemd 등록).
> 전제 인프라는 `docs/배포-GCP-Compute-Engine.md`(이미 배포 완료된 상태)를 잇는다.

## 0. 목표와 현재 상태

**목표:** `main`에 커밋이 올라가면 → 이미지 자동 빌드·푸시 → e2-micro VM에 자동 반영.

| 항목 | 현재 |
|---|---|
| 원격 | GitHub `https://github.com/kjoon418/Resumaker.git` |
| ⚠️ 미push | 로컬이 `origin/main`보다 **19커밋 앞섬** — CI는 push가 있어야 도므로 **0순위로 push 필요** |
| 빌드 | `cloudbuild.yaml` 존재(backend·frontend → Artifact Registry `:latest`). 수동 `gcloud builds submit`만 해 옴 |
| 배포 | `scripts/deploy/deploy.sh` = `docker compose pull && up -d`(멱등). VM에서 수동 실행 |
| VM 네트워크 | **인그레스 포트 0개**(Cloudflare 터널 아웃바운드). 22도 안 열림 |
| GitHub Actions | 없음(`.github/workflows` 부재) |

---

## 1. 아키텍처: 빌드 절반 + 배포 절반

### 빌드 절반(공통, 쉬움) — Cloud Build GitHub 트리거
`main` push → Cloud Build가 `cloudbuild.yaml` 실행 → `backend:latest`/`frontend:latest`를 Artifact Registry에 푸시. VM 접근 불필요. **A·B 공통.**

### 배포 절반(분기) — 새 이미지를 VM에 반영하는 법

| | **B안: 풀(pull) 기반 — VM 폴링** (권장) | **A안: 푸시 기반 — Cloud Build가 SSH 배포** |
|---|---|---|
| 방식 | VM의 타이머가 주기적으로 `git pull` + `deploy.sh` | Cloud Build 마지막 단계에서 `gcloud compute ssh`로 VM에 들어가 `deploy.sh` |
| 인그레스 포트 | **안 엶**(현 보안태세 유지) | **22를 IAP 대역(35.235.240.0/20)에 개방** 필요 |
| IAM | 거의 없음(VM이 스스로 pull) | Cloud Build SA에 `roles/compute.osLogin`+IAP tunnel user 등 |
| 반영 지연 | 폴링 주기(예: 2분) | 즉시 |
| compose/스크립트 변경 반영 | ○ (git pull로 같이 옴) | ○ (git pull 단계 추가 시) |
| 복잡도 | VM에 systemd 유닛 2개 | cloudbuild 단계+방화벽+IAM |

**권장: B안.** 이유 — 프로젝트가 "개방 포트 0 + Cloudflare 터널"로 공격면을 최소화한 설계라(`docs/배포-GCP-Compute-Engine.md`), 포트 22를 여는 A안은 그 태세를 깬다. 솔로 프로토타입엔 2분 지연이 무해. 즉시성이 꼭 필요하면 A안.

> 두 안 모두 **GitOps-lite**: VM이 저장소를 git clone으로 갖고, 배포 시 `git pull`(=compose·스크립트 갱신) + `docker compose pull`(=이미지 갱신)을 함께 한다. 그래야 코드(이미지)뿐 아니라 `compose.yaml` 변경도 반영된다. **`.env.prod`는 git에 없고(시크릿) VM 로컬 파일로 유지.**

---

## 2. 선결정 사항 (재개 시 먼저 답하면 AI가 산출물 생성)

- [x] **배포 절반 A/B 선택** → **B안(풀 기반)** 채택. 산출물 생성 완료.
- [ ] **저장소 공개 여부** — `kjoon418/Resumaker`가 private면 VM의 `git pull`에 **읽기 전용 자격**(fine-grained PAT 또는 deploy key)이 필요. public이면 불필요. (확인: GitHub repo Settings → Visibility)
- [x] **폴링 주기**(B안) → **2분**(`resumaker-deploy.timer`의 `OnUnitActiveSec=2min`). 바꾸려면 timer 파일 한 줄 수정 후 `daemon-reload`.
- [ ] **빌드 머신** — wasm 빌드가 느림. `cloudbuild.yaml`의 `E2_HIGHCPU_8` 주석을 풀어 빠르게(소액 과금) 할지.

---

## 3. 실행 체크리스트

> 표기: **[당신]** = 콘솔 클릭·IAM·push 등 사람만 가능 / **[AI]** = 설정·스크립트 산출물 생성(재개 시 요청).

### Phase 0 — 선행(공통)
- [ ] **[당신] 로컬 19커밋 push**: `git push origin main` (이게 돼야 트리거가 의미를 가짐)
- [ ] **[당신] Cloud Build ↔ GitHub 연결**: GCP 콘솔 → Cloud Build → 저장소 연결(2nd-gen, GitHub 앱 설치/승인)
- [ ] **[당신] Cloud Build 서비스계정 권한**: Artifact Registry Writer (이미 1회 빌드했다면 부여됨). 트리거 빌드도 동일 SA 사용 확인

### Phase 1 — 빌드 트리거(A·B 공통)
- [ ] **[당신] 트리거 생성**(콘솔 또는 아래 gcloud 한 줄). `main` push에 `cloudbuild.yaml` 실행:
  ```bash
  gcloud builds triggers create github \
    --name=resumaker-main \
    --repo-name=Resumaker --repo-owner=kjoon418 \
    --branch-pattern=^main$ \
    --build-config=cloudbuild.yaml
  ```
  > PowerShell이면 한 줄로(백슬래시 제거). 2nd-gen 연결을 썼다면 `--repository` 형태가 될 수 있음 — 콘솔 UI가 가장 쉬움.
- [ ] **[당신] 검증**: 더미 커밋 push → Cloud Build 히스토리에 빌드가 자동으로 뜨고 `:latest`가 갱신되는지.

### Phase 2-B — 배포(풀 기반, **B안**)
- [x] **[AI] 산출물 생성 완료**(저장소에 포함됨):
  - `scripts/deploy/auto-deploy.sh` — flock 락(중복 실행 방지) + `git pull --ff-only` + `deploy.sh`. 로그는 journald 로. 커밋 변화가 없어도 `:latest` 갱신 반영 위해 deploy.sh 는 항상 실행(멱등).
  - `scripts/deploy/resumaker-deploy.service` — systemd oneshot. **사용자로 실행**(`User=__DEPLOY_USER__`)해 vm-setup.sh 가 구성한 Artifact Registry 인증을 그대로 사용. 등록 시 `__DEPLOY_USER__`/`__APP_DIR__` 가 실제 값으로 치환됨.
  - `scripts/deploy/resumaker-deploy.timer` — `OnBootSec=2min`, `OnUnitActiveSec=2min`(2분 폴링).
- [ ] **[당신] VM을 git 체크아웃으로 전환**(1회): 현재 `~/resumaker`는 scp 사본. 깔끔히:
  ```bash
  # VM에서. .env.prod는 보존
  cp ~/resumaker/.env.prod ~/env.prod.bak
  mv ~/resumaker ~/resumaker.scp-backup
  git clone https://github.com/kjoon418/Resumaker.git ~/resumaker   # private면 자격 필요(선결정 참고)
  cp ~/env.prod.bak ~/resumaker/.env.prod
  sed -i 's/\r$//' ~/resumaker/scripts/deploy/*.sh
  ```
- [ ] **[당신] systemd 등록·기동**(플레이스홀더를 현재 사용자/경로로 치환 + CRLF 제거하며 설치):
  ```bash
  # service: __DEPLOY_USER__/__APP_DIR__ 치환 + CR 제거 후 설치
  sed -e "s|__DEPLOY_USER__|$(whoami)|g" -e "s|__APP_DIR__|$HOME/resumaker|g" -e 's/\r$//' \
    ~/resumaker/scripts/deploy/resumaker-deploy.service | sudo tee /etc/systemd/system/resumaker-deploy.service >/dev/null
  # timer: CR 제거 후 설치
  sed -e 's/\r$//' ~/resumaker/scripts/deploy/resumaker-deploy.timer | sudo tee /etc/systemd/system/resumaker-deploy.timer >/dev/null

  sudo systemctl daemon-reload && sudo systemctl enable --now resumaker-deploy.timer
  systemctl list-timers | grep resumaker      # 다음 실행 시각 확인
  sudo systemctl start resumaker-deploy.service  # (선택) 즉시 1회 실행해 동작 확인
  ```
- [ ] **[당신] 검증**: 코드 한 줄 바꿔 push → (빌드 ~몇 분) → 폴링 주기 내 VM 컨테이너가 새 이미지로 재기동되는지(`docker compose ... ps`의 CREATED 시각). `journalctl -u resumaker-deploy -f` 로 배포 로그.

### Phase 2-A — 배포(푸시 기반, **A안, 대안**)
- [ ] **[당신] 방화벽**: IAP SSH 허용 — 22를 `35.235.240.0/20`에만 개방:
  ```bash
  gcloud compute firewall-rules create allow-iap-ssh \
    --direction=INGRESS --action=ALLOW --rules=tcp:22 --source-ranges=35.235.240.0/20
  ```
- [ ] **[당신] IAM**: Cloud Build SA에 `roles/compute.osLogin`(+ 프로젝트에 IAP `roles/iap.tunnelResourceAccessor`)
- [ ] **[AI] 산출물**: `cloudbuild.yaml`에 마지막 deploy 단계 추가(`gcloud compute ssh ... --tunnel-through-iap --command="cd ~/resumaker && git pull --ff-only && bash scripts/deploy/deploy.sh"`). 또는 빌드용/배포용 트리거 분리.
- [ ] **[당신] 검증**: push → Cloud Build가 빌드 후 SSH 배포까지 한 번에.

---

## 4. 주의·한계
- **시크릿**: `.env.prod`는 절대 git에 넣지 않는다(이미 `.gitignore`). VM 로컬 파일로만 유지. `LLM_PROVIDER=api`·`ANTHROPIC_API_KEY`도 여기.
- **`:latest` 태그 전략**: 롤백이 어렵다. 나중에 커밋 SHA 태그(`_TAG=$SHORT_SHA`)로 바꾸고 `.env.prod`의 이미지 태그를 갱신하는 방식으로 개선 가능(현 단계 과함).
- **DB 마이그레이션**: 자동 배포가 새 backend를 띄우면 Flyway가 미적용 V*.sql을 자동 적용한다(운영 `ddl-auto=validate`). 파괴적 변경은 V2+ 마이그레이션으로만(메모리/가이드 준수).
- **동시 실행 방지**(B안): 폴링이 겹치지 않게 `auto-deploy.sh`에 flock 락을 둔다(AI 산출물에 포함).
- **첫 배포 지연/실패**: 빌드가 끝나기 전 폴링하면 옛 이미지를 받고, 다음 폴링에 새 이미지를 받는다(무해).

---

## 5. 재개하는 법
1. 위 **선결정 사항**에 답을 정한다(특히 A/B, repo 공개 여부).
2. AI에게 "**CI/CD B안으로 진행, 산출물 생성**"이라고 한다 → AI가 `auto-deploy.sh`·systemd 유닛(또는 A안의 cloudbuild deploy 단계)을 만든다.
3. 체크리스트의 **[당신]** 항목(push·콘솔 연결·트리거·방화벽/IAM·systemd 등록)을 따라 실행한다.
