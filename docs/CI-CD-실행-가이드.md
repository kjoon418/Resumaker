# CI/CD 실행 가이드 — 내가 해야 할 일 (B안: 풀 기반)

> **목표:** `main`에 push → Cloud Build가 이미지 자동 빌드·푸시 → e2-micro VM이 2분마다 폴링해 자동 반영.
> **전제:** `docs/배포-GCP-Compute-Engine.md`로 이미 배포가 한 번 완료된 상태. AI 산출물(`scripts/deploy/auto-deploy.sh`, `resumaker-deploy.service`, `resumaker-deploy.timer`)은 저장소에 포함됨.
>
> ⚠️ **명령 블록은 두 종류다.**
> - ` ```powershell ` = **로컬 Windows PowerShell**에서 실행 (PowerShell엔 줄바꿈 `\`가 없으므로 전부 한 줄로 적어 둠. 그대로 복붙).
> - ` ```bash ` = **VM에 SSH로 접속한 뒤(Debian)** 실행.

---

## 동작 그림

```
[내 PC] --git push--> [GitHub main]
                          │ (Cloud Build GitHub 트리거)
                          ▼
                    [Cloud Build] --빌드--> [Artifact Registry] backend:latest / frontend:latest
                                                                      ▲
                            [VM] resumaker-deploy.timer (2분) --pull--┘
                              └ git pull + docker compose pull + up -d  → 새 이미지로 재기동
```

---

## 1단계 — 로컬 커밋을 GitHub에 push (0순위)

push가 없으면 트리거가 돌 게 없다. 가장 먼저 한다.

```powershell
git push origin main
```

> 인증 창이 뜨면 GitHub 계정으로 로그인. 이후 push는 코드를 바꿀 때마다 평소처럼 하면 된다 — 이게 곧 배포 트리거가 된다.

---

## 2단계 — Cloud Build ↔ GitHub 연결 (콘솔, 1회)

GitHub OAuth 승인이 필요해 **브라우저 콘솔이 가장 쉽다.**

1. GCP 콘솔 → **Cloud Build** → **Triggers(트리거)** → **Connect Repository(저장소 연결)**.
2. 소스 **GitHub (Cloud Build GitHub App)** 선택 → GitHub 로그인·승인 → `kjoon418/Resumaker` 선택.
3. "리포지토리 연결" 까지만 하고 트리거 생성 화면이 나오면 **다음 단계**로 (여기서 바로 만들어도 됨).

> 사전 확인(로컬 PowerShell): 로그인·프로젝트·API가 돼 있는지.
> ```powershell
> gcloud config list
> gcloud services enable cloudbuild.googleapis.com artifactregistry.googleapis.com
> ```

---

## 3단계 — 빌드 트리거 생성 (`main` push → `cloudbuild.yaml`)

**방법 A — 콘솔(권장).** 2단계에 이어:
- Event: **Push to a branch**
- Source: 연결한 `kjoon418/Resumaker`, Branch: `^main$`
- Configuration: **Cloud Build configuration file**, 위치 `/cloudbuild.yaml`
- **Create** 클릭.

**방법 B — gcloud 한 줄(로컬 PowerShell).** 2nd-gen 연결이면 `--repository` 형태라 콘솔이 더 쉽지만, 1st-gen 연결이라면:

```powershell
gcloud builds triggers create github --name=resumaker-main --repo-name=Resumaker --repo-owner=kjoon418 --branch-pattern=^main$ --build-config=cloudbuild.yaml
```

> (선택) wasm 빌드가 느리면 `cloudbuild.yaml`의 `options: machineType: E2_HIGHCPU_8` 주석을 풀어 빠르게(소액 과금) 할 수 있다.

---

## 4단계 — 빌드 트리거 동작 검증

더미 커밋을 밀어 빌드가 자동으로 도는지 본다.

```powershell
git commit --allow-empty -m "chore: CI 트리거 점검"
git push origin main
```

- GCP 콘솔 → Cloud Build → **History**에 빌드가 자동으로 떠야 한다.
- 끝나면 Artifact Registry의 `backend:latest`/`frontend:latest` **업데이트 시각**이 갱신됐는지 확인.
- 푸시 권한 오류가 나면 빌드 서비스계정(`<프로젝트번호>-compute@developer.gserviceaccount.com`)에 `roles/artifactregistry.writer` 부여(`cloudbuild.yaml` 주석에 명령 있음).

여기까지면 **빌드 절반**은 끝. 이제 VM이 새 이미지를 자동으로 받게 한다.

---

## 5단계 — VM을 git 체크아웃으로 전환 (SSH, 1회)

자동 배포는 VM에서 `git pull`을 하므로, 현재 scp 사본인 `~/resumaker`를 **git clone본**으로 바꾼다. `.env.prod`(시크릿)는 git에 없으니 반드시 보존한다.

먼저 VM 접속(로컬 PowerShell):

```powershell
gcloud compute ssh resumaker --zone=us-central1-a
```

접속 후 **VM 안에서(bash)**:

```bash
# .env.prod 백업 → 기존 디렉터리 보관 → clone → .env.prod 복원 → CRLF 제거
cp ~/resumaker/.env.prod ~/env.prod.bak
mv ~/resumaker ~/resumaker.scp-backup
git clone https://github.com/kjoon418/Resumaker.git ~/resumaker
cp ~/env.prod.bak ~/resumaker/.env.prod
sed -i 's/\r$//' ~/resumaker/scripts/deploy/*.sh
```

> **저장소가 private이면** 위 `git clone`이 자격을 요구한다. 두 방법 중 하나:
> - **(간단) fine-grained PAT를 URL에 넣어 clone** — GitHub → Settings → Developer settings → Fine-grained tokens에서 이 repo **Contents: Read-only** 토큰 발급 후:
>   ```bash
>   git clone https://<PAT>@github.com/kjoon418/Resumaker.git ~/resumaker
>   ```
>   (토큰이 `~/resumaker/.git/config`에 남으니 VM 외부 노출 주의. 만료 시 갱신 필요.)
> - **(권장) read-only deploy key** — VM에서 `ssh-keygen`으로 키 생성 → 공개키를 GitHub repo → Settings → Deploy keys에 등록 → `git clone git@github.com:kjoon418/Resumaker.git`.
>
> public이면 위 추가작업 없이 그대로 된다.

---

## 6단계 — systemd 타이머 등록·기동 (SSH, 1회)

유닛 파일의 플레이스홀더(`__DEPLOY_USER__`/`__APP_DIR__`)를 현재 사용자/경로로 치환하며 설치한다. **VM 안에서(bash)**:

```bash
# service: 사용자/경로 치환 + CR 제거 후 설치
sed -e "s|__DEPLOY_USER__|$(whoami)|g" -e "s|__APP_DIR__|$HOME/resumaker|g" -e 's/\r$//' \
  ~/resumaker/scripts/deploy/resumaker-deploy.service | sudo tee /etc/systemd/system/resumaker-deploy.service >/dev/null

# timer: CR 제거 후 설치
sed -e 's/\r$//' ~/resumaker/scripts/deploy/resumaker-deploy.timer | sudo tee /etc/systemd/system/resumaker-deploy.timer >/dev/null

sudo systemctl daemon-reload
sudo systemctl enable --now resumaker-deploy.timer

systemctl list-timers | grep resumaker          # 다음 실행 시각 확인
sudo systemctl start resumaker-deploy.service    # 즉시 1회 실행해 동작 확인
journalctl -u resumaker-deploy -n 50 --no-pager  # 방금 실행 로그 확인
```

> 로그에 `git pull` → `deploy.sh` → `자동 배포 완료`가 보이면 정상.

---

## 7단계 — 전체 흐름 최종 검증

실제 코드 한 줄을 바꿔 끝까지 자동으로 흐르는지 본다.

```powershell
# 로컬: 아무 코드나 작게 바꾼 뒤
git commit -am "test: 자동 배포 점검"
git push origin main
```

1. (수 분) Cloud Build History에 빌드 성공.
2. 이후 **2분 폴링 주기 내**에 VM 컨테이너가 새 이미지로 재기동되는지 확인 — VM(bash):
   ```bash
   journalctl -u resumaker-deploy -f
   docker compose -f ~/resumaker/compose.yaml -f ~/resumaker/compose.prod.yaml --env-file ~/resumaker/.env.prod ps
   ```
   `ps`의 backend/frontend **CREATED 시각**이 방금으로 바뀌면 성공.
3. 서비스 URL(`https://app.<도메인>`)에서 변경이 반영됐는지 확인.

여기까지 통과하면 **앞으로는 `git push origin main` 한 번이면 배포가 끝까지 자동**으로 흐른다.

---

## 부록 — 운영 메모

| 하고 싶은 것 | 명령 (VM, bash) |
|---|---|
| 배포 로그 실시간 보기 | `journalctl -u resumaker-deploy -f` |
| 지금 즉시 1회 배포 | `sudo systemctl start resumaker-deploy.service` |
| 자동 배포 일시 중지 | `sudo systemctl disable --now resumaker-deploy.timer` |
| 다시 켜기 | `sudo systemctl enable --now resumaker-deploy.timer` |
| 폴링 주기 변경 | `/etc/systemd/system/resumaker-deploy.timer`의 `OnUnitActiveSec=2min` 수정 → `sudo systemctl daemon-reload && sudo systemctl restart resumaker-deploy.timer` |

- **시크릿(`.env.prod`)** 은 절대 git에 넣지 않는다(이미 `.gitignore`). VM 로컬 파일로만 유지된다. `LLM_PROVIDER`·`ANTHROPIC_API_KEY`도 여기.
- **DB 마이그레이션**: 새 backend가 뜨면 Flyway가 미적용 `V*.sql`을 자동 적용한다(운영 `ddl-auto=validate`). 파괴적 변경은 `V2+` 마이그레이션으로만.
- **롤백**: 현재 `:latest` 태그라 롤백이 어렵다. 필요해지면 커밋 SHA 태그 전략으로 개선(현 단계엔 과함).
- **즉시성이 꼭 필요하면(2분 대기 싫으면)**: push 기반(A안)으로 바꿀 수 있으나 포트 22 개방·IAM이 필요해 공격면이 커진다. 솔로 프로토타입엔 B안 권장.
