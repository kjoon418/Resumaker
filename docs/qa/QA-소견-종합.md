# Resumaker QA 소견 종합 (2026-06-27)

로컬 QA 사이클 결과. 브라우저 직접 조작이 불가한 환경이라 **(a) 백엔드 E2E 테스트 실행으로 런타임 동작 관찰 + (b) 프런트 Compose 화면·AI 파이프라인 코드의 "의도(도메인·UX·설계 가이드 문서) vs 실제 동작" 정적 추적**으로 수행했다. 모든 소견은 코드/테스트 증거 기반이며 위치는 `파일:라인`으로 정박했다.

소견 분류: **Blocker**(출시/비용 가드레일을 직접 깸·핵심 흐름 차단) · **Major**(중요 흐름·품질 훼손, 회복 가능) · **Minor**(일관성·국소 품질).

> 영역별 상세는 본 문서 한 곳에 통합했다(에이전트의 개별 `findings-*.md` 파일 쓰기는 하니스가 차단해 인라인으로 회수·통합).

---

## 0. 한눈에 — 우선순위 보드

| 우선 | ID | 영역 | 제목 | 심각도 |
|---|---|---|---|---|
| ★1 | B1 | 백엔드 | 항목 재생성 일일 한도가 사실상 무제한(REGEN 키가 버전마다 새 SectionId) | **Blocker** |
| ★2 | UX-01 | 프런트 | 품질 점검 실패 시 무한 스켈레톤 — 에러·재시도 도달 불가 | **Blocker** |
| ★3 | B2 | 백엔드 | 품질 개선 한도 우회 — 워커가 처리 시 재점검 없이 차감만 | Major |
| ★4 | B3 | 백엔드 | 생성 워커 비원자 완료 — 크래시 시 성공 산출물+재시도 이중 차감 | Major |
| ★5 | AI-05 | AI | 1차 생성 프롬프트에 간결성·강한동사·어조·길이 지시 부재(기획된 (가) 보완 미구현) | Major |
| ★6 | AI-02 | AI | 사실 토큰 "전부 보존" 불변식이 길이/압축 처치와 충돌 → 처치 no-op | Major |
| ★7 | AI-06 | AI | 결정적 사전 매칭이 substring 기반 → 거짓 양성 다발(유료 처치 오발) | Major |
| ★8 | B4 | 백엔드 | 재생성 AI 실패가 400(INVALID_REQUEST) — 일시적 실패를 입력오류로 표기 | Major |
| ★9 | UX-02/03/04 | 프런트 | 생성 진입·실패 카드·비활성 버튼의 막다른 길/단서 부재 | Major |
| ★10 | AI-01/03/04/07/08/09 | AI | 진단·처치 카탈로그/매칭/대조의 품질 결함군 | Major |
| 런타임 | RT-1 | 테스트 | 핵심 E2E 플래키(수동 poll()과 @Scheduled 경합) | Major |

---

## 1. 백엔드 기능 정합성 (10건: Blocker 1 · Major 3 · Minor 6)

### [B1] 항목 재생성 일일 한도가 사실상 무제한 — REGEN 스코프 키가 버전마다 새 SectionId (Blocker) ✔검증완료
- **위치:** `CountingGenerationQuotaGuard.kt:130`(`REGEN:${sectionId.value}`), `SectionRegenerationService.kt:72`(check), `ArtifactSection.kt:144`(`copyForNewVersion` → `SectionId(newId())`), `Artifact.kt:136-151`(`adoptSection`).
- **현상:** 재생성 성공 시 `adoptSection`이 새 버전을 만들고 그 항목은 **새 SectionId**를 받는다. 옛 SectionId는 새 활성 버전에 없어 404라 재사용 불가 → 다음 재생성은 반드시 새 SectionId로 전송 → 매번 **count 0인 새 키**에서 시작해 상한(기본 5/항목/일)에 영원히 도달 못함. 한 논리 항목을 하루 무제한 재생성(매번 실 LLM 비용). `CountingGenerationQuotaGuardTest`는 같은 sectionId 5회만 검증해 교차-버전 우회를 못 잡음.
- **의도:** 도메인 §449·수용 기준 15 — '항목'은 버전 가로지르는 논리 항목(같은 산출물·같은 definitionKey)이지 물리 행이 아니다.
- **개선안:** 재생성 쿼터 키를 버전 불변 식별자(`REGEN:{artifactId}:{definitionKey}`)로 변경. `check/recordRegeneration` 시그니처에 artifactId+definitionKey 전달. 교차-버전 회귀 테스트 추가.

### [B2] 품질 개선 일일 한도 우회 — 워커가 처리 시 재점검 없이 차감(생성 워커와 비대칭) (Major)
- **위치:** 접수 점검만 `QualityImprovementJobService.kt:46`; 차감 `QualityImprovementJobWorker.kt:137`(이 경로에 check 없음). 대조 `ArtifactGenerationService.kt:71`(워커가 tx1에서 재점검).
- **현상:** 점검은 접수 시점, 차감은 워커 성공 시점이라 시차에 여러 건을 빠르게 접수하면 모두 count 0에서 통과 → 순차 성공·차감해 상한 초과.
- **개선안:** `process` tx2에서 `recordQualityImprovement` 직전 `checkQualityImprovement` 재호출, 초과 시 차감 없이 FAILED(QUOTA_EXCEEDED).

### [B3] 생성 워커 비원자 완료 — 크래시 시 성공 산출물 + IN_PLACE 재시도(이중 차감) (Major)
- **위치:** `GenerationJobWorker.kt:84-127`; 산출물 영속+차감은 `ArtifactGenerationService.kt:101-109` tx2, 이후 워커가 `markSucceeded`+`save(job)` 별도 수행.
- **현상:** tx2 커밋과 `save(job)` 사이 크래시 시 작업 RUNNING 잔존 → 5분 후 `recoverStale`이 FAILED(IN_PLACE). 사용자는 성공 산출물 + "다시 만들기" 실패 작업을 동시에 보고, 재시도 시 2번째 산출물+2번째 차감. (품질 워커는 한 tx2 원자 처리 — 생성 워커만 비원자.)
- **개선안:** 멱등 키(jobId)를 산출물에 기록해 재시도/회수가 기존 산출물 발견 시 SUCCEEDED로 수렴. 최소한 IN_PLACE 재시도 전 "이 작업 산출물 존재 여부" 확인.

### [B4] 재생성 AI 실패가 400(INVALID_REQUEST) — 일시적 실패를 입력 오류로 표기 (Major)
- **위치:** `SectionRegenerationService.kt:178-181`(`resolved==null`→DomainValidationException), 매핑 `GlobalExceptionHandler.kt:41-45`(400).
- **현상:** 외부 LLM이 항목을 못 만든 AI 일시 실패인데 400 INVALID_REQUEST. 같은 의미를 `ClaudeCliException`은 503/RETRY_LATER로 분리하는데 경로에 따라 갈림.
- **개선안:** `resolved==null`을 503 계열(`AI_GENERATION_UNAVAILABLE`/`RETRY_LATER`)로 던져 진짜 입력성 거부(근거 0)와 구분.

### [B5] 전 항목 실패가 항상 NO_CONTENT→EDIT_INPUTS — 일시적 실패에도 입력 수정 화면 유도 (Minor)
- **위치:** `ArtifactGenerationService.kt:263-267`, `GenerationJobWorker.kt:117-119`, `GenerationJob.kt:140`.
- **개선안:** "근거 0 실체화 불가"(입력성)와 "전 항목 일시 실패"를 구분 코드로, 후자는 IN_PLACE.

### [B6] EmptyExperienceSelectionException 워커 catch 누락 → Throwable로 오분류(IN_PLACE) (Minor)
- **위치:** `GenerationJobWorker.kt:111-126`; `DomainException.kt:30`(DomainValidationException 미상속).
- **현상:** 현재 제출 DTO `@NotEmpty`로 실전 도달은 어려우나 분류 사다리가 의미와 어긋나 회귀 취약.
- **개선안:** 워커에 해당 예외 catch 추가(입력성→EDIT_INPUTS) 또는 입력성 도메인 예외 공통 상위로 묶음.

### [B7] 생성 제출 시 경험 존재·소유 미검증 — 목표(동기 404)와 비대칭 (Minor)
- **위치:** `GenerationJobService.kt:42-72`(목표만 검증), 경험은 워커에서야 `SOURCE_MISSING`.
- **개선안:** 제출 시 경험 id 배치 조회로 존재·소유 검증(목표와 동형) 또는 두 입력 모두 비동기 실패로 일관·문서화.

### [B8] 품질 차감 tx 실패 시 후보까지 롤백 — 처치 성공 유실 (Minor)
- **위치:** `QualityImprovementJobWorker.kt:123-140`(saveAll+record가 한 tx2).
- **개선안:** 차감을 후보 영속과 분리(보상 단계)하거나 `loadUser` 부재를 차감 전 선검증.

### [B9] 생성·산출물 목록 무페이지네이션 — 종료 작업 누적 (Minor · 인지된 백로그 §304)
- **위치:** `GenerationJobRepository.kt:37`, `ArtifactController.kt:73-75`.

### [B10] 재생성 동시 거절이 인메모리 락 — 다중 인스턴스 미보장 (Minor · 인지된 백로그 §304)
- **위치:** `SectionRegenerationLocks.kt:31-45`.

---

## 2. AI 출력 품질 (13건: Major 9 · Minor 4)

기준: 도메인 §459~487(신뢰성 가드레일), `이력서 품질 기준과 자동 개선 기획.md`, `경험 작성 AI 보조 기획.md`.

### [AI-01] VAGUE_METRIC 사전이 기획 ★핵심 안티패턴(AP5 "200% 증가/30% 개선") 미검출 (Major)
- **위치:** `QualityCriteriaProperties.kt:44-47`, `QualityCriteriaDictionary.kt:30-31`. 시드 사전이 순수 규모어만 담아 모호 수치형을 못 잡음.
- **개선안:** 모호 수치 정규식(`\d+%\s*(증가|개선|향상|단축)`, "N배", "수십·수백")을 사전과 합집합으로.

### [AI-02] 사실 토큰 "전부 보존" 불변식이 길이/압축 처치와 충돌 → 거의 no-op (Major)
- **위치:** `FactTokenPreservationValidator.kt:52-54`, `QualityImprovementProcessor.kt:49`.
- **현상:** `원본 수치 ⊆ 후보 수치` 강제라 압축(파생 수치 제거)이 항상 보존 실패→원본 유지. "길어요" 소견 보고 다듬어도 안 바뀜.
- **개선안:** 수치는 "후보 ⊆ 원본(새 수치 0건)"이면 통과로 완화(축약 허용), 고유명사만 전수 보존. QC4 문구도 "수치 변형·날조 금지"로 좁힘.

### [AI-03] DUPLICATION 처치가 짝 항목 미전달로 구조적으로 중복 제거 불가 (Major)
- **위치:** `QualityReviewService.kt:147-150`, `QualityImprovementJobWorker.kt:174-187`, `ClaudeCliQualityImprovementAdapter.kt:54-55`.
- **개선안:** C3를 SUGGESTION으로 강등하거나 처치 입력에 짝 항목 본문(`duplicatedWith`)을 실어 함께 보고 통합.

### [AI-04] 처치 입력이 라벨만 전달, evidenceText(구체 위반 토큰) 폐기 → 부정확·과도 재작성 (Major)
- **위치:** `QualityImprovementJobWorker.kt:166-188,243`, `ClaudeCliQualityImprovementAdapter.kt:58-59`.
- **개선안:** `criteria`에 evidenceText를 함께 실어 "이 표현('담당했다')을" 식으로 정박.

### [AI-05] 1차 생성 프롬프트에 간결성·강한동사·어조·길이 지시 부재 — 기획된 (가) 보완 미구현 (Major)
- **위치:** `ClaudeCliArtifactGenerationAdapter.kt:92-105`, `GroundingPromptParts.kt:18-25`. 기획 §217·§220이 "저비용 변경"으로 명시했으나 미구현.
- **현상:** 1차 생성물이 "담당했다" 나열·버즈워드·장황체로 나오기 쉬워 곧바로 다수 소견→유료 처치 재요구.
- **개선안:** `appendResumeInstructions`에 행동·성취 동사, 버즈워드 절제, 항목당 길이 상한, 어조 일관 지시 추가(신뢰성 규칙 하위).

### [AI-06] 결정적 사전 매칭이 substring(`contains`) 기반 → 거짓 양성 다발(유료 처치 오발) (Major)
- **위치:** `QualityCriteriaDictionary.kt:22-35`. "소통"↔"의사소통", "복잡한"↔"복잡한 레거시를 모듈화", "있었다"↔"경쟁력이 있었다".
- **개선안:** 어절/종결어미 경계 매칭 전환. 최소한 AUTO_REWRITE 유발 기준엔 경계 매칭.

### [AI-07] 수치 대조가 아라비아 숫자만·단위 무시 → 수사적 숫자 거짓 양성(드롭)+단위 충돌 거짓 음성 (Major)
- **위치:** `FactTokenExtractor.kt:18-21,146`, `DeterministicGroundingValidator.kt:52-58`.
- **현상(FP):** 경험엔 "여러", 산출물 "3개" → 토큰 3 미근거 → 검증실패·드롭. **(FN):** "40%"가 경험 "40명"으로 통과(날조 누수).
- **개선안:** 단위 없는 수량/순서 수사는 대조 제외 또는 단위 있는 수치만 NUMERIC; 흔한 단위(%,명,ms,건,원)는 값+단위 쌍 비교.

### [AI-08] 한글 고유명사 미추출로 가드레일 구멍 — 처치가 고유명사 일반화·삭제해도 통과 (Major)
- **위치:** `FactTokenExtractor.kt:28-30,76-85`, `FactTokenPreservationValidator.kt:60-68`.
- **현상:** "토스","카프카"(따옴표 없는 한글 고유명사) 미추출 → "토스 결제"→"한 핀테크 결제"로 흐려도 검증·보존 통과.
- **개선안:** 보존 검증 사전에 경험 제목·본문의 한글 고유명사 후보(영문 인접·반복 등장 토큰) 추가. 과보존은 안전 실패라 허용.

### [AI-09] 품질 점검 카탈로그가 기획 안티패턴 다수 미구현 — 진단 빈약 (Major)
- **위치:** `QualityCriterion.kt:18-37`(7종) vs 기획 AP1~13. I3 성과 가시화·K1/K2 일관·C4 가독성·F1/F2 목표 적합성 누락.
- **개선안:** K1(종결 패턴 통계)·K2(표기 변형)·I3(경험 결과 있는데 미반영)을 결정적 검사기로 추가. F1/F2는 작성 전략 키워드 등장으로 점검(전략이 스냅샷 보존 — `ArtifactGenerationService.kt:375`).

### [AI-10] hasNumericEvidence가 무관 숫자(연도·인원)도 근거로 인정 → 유료 AUTO_REWRITE 오라우팅 (Minor)
- **위치:** `QualityReviewService.kt:121-133,160-167`.
- **개선안:** 근거 판정을 모호 표현과 같은 문장/결과 칸 등 국소 범위로 좁히거나 연도·인원 패턴 제외.

### [AI-11] 중복 검출(char 6-gram Jaccard≥0.5)이 의미 중복을 놓침 (Minor)
- **위치:** `QualityCriteriaDictionary.kt:48-65`, `QualityCriteriaProperties.kt:27-28`.
- **개선안:** 어절 토큰 자카드/코사인 병행, 임계 하향, 짧은 항목 작은 shingle.

### [AI-12] VAGUE_METRIC 안내 문구가 비수치 규모어에도 "수치 객관화"로 떠 혼란 (Minor)
- **위치:** `QualityReviewService.kt:127-131`, `ExperienceReviewService.kt:54`.
- **개선안:** 규모어 유형 분리해 메시지 분기(수치형→"구체 값", 역할/규모 형용사→"어떤 행동·기술로 그렇게 판단했는지").

### [AI-13] GENERATION_FAILED 빈 항목이 열람 응답에 그대로 실려 빈약하게 보임 (Minor)
- **위치:** `SectionRegenerationProcessor.kt:30-32`, `ArtifactGenerationService.kt:322-338`, `ArtifactReadServiceMapper.kt:78-86`.
- **개선안:** 빈 content 실패 항목을 본문 자리 대신 "보강 고지"로 분리 노출 또는 응답 DTO에 빈 항목 여부 명시.

---

## 3. 프런트 UX·흐름 (11건: Blocker 1 · Major 3 · Minor 7)

기준: `UX 핵심 가이드.md`, `UX 에러 응답 가이드.md`.

### [UX-01] 품질 점검 실패 시 무한 스켈레톤 — 에러·재시도 도달 불가 (Blocker) ✔검증완료
- **위치:** `QualityReviewViewModel.kt:228-231`(실패→step=IDLE+errorMessage), `QualityReviewScreen.kt:97-102`(IDLE/REVIEWING은 SkeletonList만), `:104-111`(ErrorBanner는 FINDINGS 분기 안에만).
- **현상:** 점검 API 실패 시 화면이 영원히 스켈레톤에 머물고 에러·재시도가 절대 안 보임. 진입 시 1회 호출이라 자동 복구도 없음.
- **개선안:** 실패 시 step을 FINDINGS(또는 ERROR 단계)로 두거나, IDLE/REVIEWING 분기에서 `errorMessage != null`이면 `ErrorBanner(onRetry=startReview)`를 먼저 렌더.

### [UX-02] 생성 화면: 목표 없을 때 추가 경로 없음(막다른 길) (Major)
- **위치:** `ArtifactCreateScreen.kt:177-190`; `onAddTarget`은 빈 경험 분기(`:109-118`)에서만 사용.
- **개선안:** 목표 EmptyHint 자리에 `목표 추가하기` 버튼(`onAddTarget`) 노출.

### [UX-03] 생성 실패 카드: 복구 액션이 표식 없는 탭 뒤에 숨음 (Major)
- **위치:** `ArtifactListScreen.kt:162-177,219-247`(펼쳐야 복구 액션 노출, 접힘 단서 없음). 완성 카드는 ChevronRight 단서 제공.
- **개선안:** 실패 카드에 펼침 단서 추가 또는 액션 항상 노출.

### [UX-04] 비활성 "만들기" 버튼: 무엇이 부족한지 미표시 (Major)
- **위치:** `ArtifactCreateScreen.kt:224-243`, `ArtifactCreateViewModel.kt:81-87`.
- **개선안:** 비활성 시 미충족 조건 caption("경험을 1개 이상 고르고 지원할 목표를 선택해 주세요").

### [UX-05] 전략 "분석 중" 카피가 자동 폴링과 모순 (Minor)
- **위치:** `TargetDetailScreen.kt:208-220`, `TargetDetailViewModel.kt:86-96`. "다시 열면 확인" 안내인데 3초 자동 폴링.
- **개선안:** "분석이 끝나면 자동으로 보여드릴게요."

### [UX-06] 경험 점검 패널 "저장 후 갱신" 의도-코드 불일치 (Minor)
- **위치:** `ExperienceEditViewModel.kt:44-48,99,110-117,184`. 저장 후 갱신 미구현, 저장 즉시 목록 복귀.
- **개선안:** 주석을 실제 동작에 맞추거나 저장 후 loadReview 재호출.

### [UX-07] 생성 실패 ADD_EXPERIENCE 힌트가 CTA로 미연결 (Minor)
- **위치:** `ArtifactCreateViewModel.kt:56-60`, `ArtifactCreateScreen.kt:214-222`. 화면이 generationAction 미사용.
- **개선안:** ADD_EXPERIENCE면 재시도 대신 "경험 다시 고르기" CTA 분기, 또는 미사용 필드/주석 정리.

### [UX-08] 실패 생성 기록만 확인 없이 즉시 삭제(파괴적 동작 일관성) (Minor)
- **위치:** `ArtifactListScreen.kt:242-245` vs 경험·목표·양식의 ConfirmDialog.
- **개선안:** 무확인 유지 근거 문서화 또는 경량 확인/실행취소.

### [UX-09] 로그인 실패를 사라지는 토스트로만 전달 (Minor)
- **위치:** `SessionViewModel.kt:118-122`, `SessionScreen.kt:51-56`. 가입은 인라인, 로그인은 토스트.
- **개선안:** 로그인 실패도 지속 인라인 메시지로.

### [UX-10] 후보 전부 제외된 개선 결과의 약한 막다른 길 (Minor)
- **위치:** `QualityImprovementScreen.kt:85-119`, `QualityReviewViewModel.kt:288-312`. 후보 0개 시 뒤로가기 외 출구 없음.
- **개선안:** 후보 0개일 때 "직접 편집하러 가기"(열람 복귀) 버튼 노출.

### [UX-11] "경험 보강하러 가기"가 점검 진행 상태를 버림 (Minor)
- **위치:** `QualityReviewScreen.kt:50-51,123-132,268-285`. 이탈 후 복귀 시 점검 결과 소실, 안내 없음.
- **개선안:** 별도 진입으로 열거나 복귀 시 직전 점검 결과 복원("보강 후 다시 점검" 명시).

---

## 4. 런타임 관찰 (테스트 실행)

### [RT-1] 핵심 가치 흐름 E2E가 플래키 — 수동 poll()과 @Scheduled 워커의 큐 클레임 경합 (Major)
- **위치:** `CoreValueFlowE2ETest.kt:351-361`(awaitArtifactId가 poll() 20틱 수동 구동), `GenerationJobWorker.kt:42`(@Scheduled poll도 동시 구동).
- **현상:** 전체 스위트(+부하)에서 `포트폴리오도_경험별_항목으로_생성되어_열람된다`가 "제한 시간 초과"로 실패. 단독 재실행은 통과 → 플래키. 원인: 테스트가 `poll()`을 수동 호출하는 동안 `@Scheduled` 워커가 같은 전역 큐를 클레임해, 부하 시 수동 틱이 우리 작업을 못 집고 20틱 소진. 부수적으로 **단일 인스턴스 처리량(폴 주기당 1건)** 한계도 드러남.
- **개선안:** E2E에서 스케줄러 비활성(`spring.task.scheduling.enabled=false` 또는 프로파일 분리) 후 수동 poll만 구동해 결정성 확보. 처리량은 운영 백로그(다중 인스턴스 §304)로 추적.

---

## 5. 권고 처리 순서 (협업 로드맵)

1. **즉시(Blocker):** B1(재생성 한도 우회), UX-01(무한 스켈레톤). 둘 다 증거 확정, 변경 국소·고가치.
2. **비용 가드레일 정합(Major):** B2, B3, B4.
3. **AI 1차 품질 상향(Major, 저비용 고효과):** AI-05(프롬프트 보완), AI-02(보존 완화), AI-06(경계 매칭).
4. **UX 막다른 길 제거(Major):** UX-02/03/04.
5. **테스트 신뢰성:** RT-1.
6. **AI 진단 정밀도(Major→Minor):** AI-01/03/04/07/08/09, 이후 Minor군.
7. **백로그(인지된 트레이드오프):** B9, B10 — 다중 인스턴스 단계에서.

---

## 6. 수정 사이클 결과 (2026-06-27, 협업 처리 완료)

**처리 범위:** Blocker 2 + 핵심 Major 9 = **12건**. 서버 워크스트림(개발자)·프런트 워크스트림(디자이너+개발자)·독립 리뷰 패스(검토자)로 협업. 각 항목 테스트 주도·원자 커밋.

| ID | 커밋 | 처리 |
|---|---|---|
| B1 | `48ebb83` | 재생성 쿼터 키를 `REGEN:{artifactId}:{definitionKey}` 버전 불변 키로 — 교차버전 누적 회귀 테스트 |
| B2 | `b916bff` | 품질 워커 tx2 차감 직전 한도 재점검 — 시차 우회 차단 |
| B3 | `716ed57` | 작업 완료를 생성 tx2 `onPersisted` 훅으로 원자화 — 크래시 이중차감 구조적 차단(스키마 무변경) |
| B4 | `a8eab64` | 재생성 항목 누락을 503 RETRY_LATER로 — 입력성 거부(400)와 분리 |
| AI-05 | `8ae7866` | 1차 생성 프롬프트에 강한동사·간결·어조 지침 보강 |
| AI-02 | `a9cfe25` | 수치 보존을 "변형·날조 금지"로 좁혀 압축 처치 no-op 해소(고유명사 전수보존 유지) |
| AI-06 | `21800f6` | 사전 매칭을 어절 시작 경계로 — 거짓양성 제거 |
| RT-1 | `a32f83b` | E2E 스케줄러 no-op으로 차단 — 핵심 흐름 결정성 확보 |
| UX-01 | `2919ed6` | 품질 점검 실패 시 무한 스켈레톤 대신 에러·재시도 노출 |
| UX-02 | `cd2f4db` | 생성 화면 목표 0건일 때 목표 추가 경로 제공 |
| UX-03 | `924c70d` | 생성 실패 카드 복구 액션 항상 노출 |
| UX-04 | `4e82bd6` | 비활성 만들기 버튼에 미충족 조건 안내 |

**검증:** `:server:test` 연속 2회 그린(+팀장 독립 재확인), `:app:shared:jsNodeTest` 신규 ViewModel 테스트 그린(실패 2건은 문서화된 skiko 렌더 제약). **독립 리뷰 패스 판정: 승인**(Blocker/Major 0).

**리뷰가 남긴 후속 백로그(비차단):**
- B4 잔여: 재생성 항목 누락에 `ClaudeCliException`(인프라 예외)을 재사용 → 후속으로 `GenerationUnavailableException` 도메인 예외 도입 권장. 또한 핸들러가 메시지를 고정 문구로 덮어 "재생성 실패"가 "AI 생성을 사용할 수 없어요"로 표시(문구 정밀화 여지).
- B2 잔여: 처리시점 재점검은 단일 워커 직렬 기준 정확. 다중 인스턴스 병렬에선 동일 count 동시 read 여지 — 백로그 H1(다중 인스턴스)과 함께.

**미처리(1차 범위 밖):** 백엔드 B5~B10, AI-01·03·04·07·08·09·10~13, UX-05~11. → 아래 §7 2차 사이클에서 대부분 처리.

---

## 7. 2차 수정 사이클 결과 (2026-06-27, 잔여 백로그 처리)

**처리 범위:** 1차에서 미룬 잔여 Major·Minor 22건 + 1차 리뷰 후속 2건 = **24건**. 영역별 워크스트림(서버 AI 품질 → 서버 백엔드 → 프런트 UX)을 git/gradle 경합 회피로 순차 실행하고, 독립 코드리뷰 패스로 승인받았다.

### 7.1 AI 출력 품질 10건 (커밋)
| ID | 커밋 | 처리 |
|---|---|---|
| AI-07 | `f587aa0` | 수치 대조를 단위 인지로 — 흔한 단위(%·명·ms·건·원·배)는 값+단위 쌍 비교, 단위 충돌(40%↔40명) 근거없음, 단위 없는 수사 제외 |
| AI-08 | `81ba49e` | 보존 검증 사전에 한글 고유명사 후보(영문 인접·반복 등장) 추가 — "토스 결제"→"한 핀테크" 흐림을 보존 실패로(과보존=안전, grounding 불변) |
| AI-01 | `809941d` | AP5 모호수치 정규식("200% 증가"·"N배"·"수십·수백")을 시드 사전과 합집합 |
| AI-09 | `51a4824` | 카탈로그에 K1(종결 단조)·K2(표기 변형)·I3(result 미반영) 추가, 모두 SUGGESTION(유료 AUTO_REWRITE 남발 방지) |
| AI-10 | `1dc6eef` | hasNumericEvidence에서 연도·순수 인원 제외 — 무관 숫자의 유료 AUTO_REWRITE 오라우팅 차단 |
| AI-11 | `09d25c3` | 중복 검출에 어절 토큰 자카드 병행(재배열 의미중복), 최소 어절 가드·높은 임계로 오탐 억제 |
| AI-12 | `f6728f5` | VAGUE_METRIC 안내를 수치형(구체 값)·형용사형(행동·기술 근거)으로 분기(품질·경험 점검 양쪽) |
| AI-03 | `d56b017` | 중복 처치 입력에 짝 항목 본문(duplicatedWith)을 실어 함께 보고 통합(스키마 무변경) |
| AI-04 | `9c8b32b` | 처치 라벨에 evidenceText(구체 위반 토큰) 정박 — 과도 재작성 축소 |
| AI-13 | `deab4e2` | 응답 DTO에 empty 플래그 — 빈 실패 항목을 본문 대신 보강 고지로 분리 가능 |

### 7.2 백엔드 정합성 Minor 5건 (커밋)
| ID | 커밋 | 처리 |
|---|---|---|
| B4 잔여 | `98c13bd` | `GenerationUnavailableException` 도메인 예외 도입 — 인프라 예외 재사용 제거, 503/RETRY_LATER 매핑+도메인 문구 보존 |
| B5 | `8d78cb0` | 전 항목 일시 실패를 `GenerationUnavailableException`(IN_PLACE)으로, 근거 0(NO_CONTENT·EDIT_INPUTS)과 분리 |
| B6 | `eb01bf4` | 빈 경험 선택 예외를 SOURCE_MISSING(EDIT_INPUTS) catch — Throwable 오분류(IN_PLACE) 제거 |
| B7 | `a232eb4` | 생성 제출 시 경험 존재·소유 배치 검증(목표와 대칭, 404) |
| B8 | `d6c014a` | 차감을 후보 영속과 분리(보상 단계) — 차감 실패 시 처치 유실 방지 |

### 7.3 프런트 UX Minor 7건 (커밋)
| ID | 커밋 | 처리 |
|---|---|---|
| UX-05 | `49e5d98` | 전략 "분석 중" 카피를 자동 폴링과 일치("끝나면 자동으로 보여드릴게요") |
| UX-06 | `40df619` | 경험 점검 패널 주석을 실제 동작(진입 시 1회 점검·저장 즉시 복귀)으로 정합 |
| UX-07 | `352e707` | 죽은 generationAction 해소 — ADD_EXPERIENCE는 재시도 대신 "경험 추가하러 가기" CTA |
| UX-08 | `5a8e319` | 실패 생성 기록 삭제를 ConfirmDialog 경량 확인으로 일관화 |
| UX-09 | `edb70fd` | 로그인 실패를 사라지는 토스트 대신 지속 인라인 에러로 통일 |
| UX-10 | `3fefb6d` | 후보 0건 시 "직접 편집하러 가기" 출구 노출(막다른 길 제거) |
| UX-11 | `3c61dce` | 점검 상태 보존은 구조상 과함 → 최소 개선으로 "다시 열면 재점검" 안내 카피 |

### 7.4 독립 리뷰 + 리뷰 후속 처리
**판정: 승인**(Blocker 0·Major 0, Minor 5·Nit 2). `:server:test` 그린, `:app:shared:jsNodeTest` 기존 skiko 2건 외 그린. 리뷰가 "첫 후속"으로 지목한 2건을 즉시 처리:
- **AI-01 경계(`da8adb7`):** 모호수치 정규식이 raw find라 "보수만큼/접수만"의 `수만`, "1024배열"의 `배`를 단어 가운데서 오탐 → 유료 AUTO_REWRITE 오발. `수(십\|백\|천\|만)` lookbehind·`\d+배` lookahead로 어절 경계 앵커(사전 매칭 AI-06과 같은 규율).
- **B8 원자성(`9eacde9`):** B8의 보상 단계 분리가 "커밋과 차감 사이 크래시 시 미차감 성공"이라는 비원자 완료(생성에서 B3가 닫은 것)를 품질에 재개방. 차감의 유일한 선행 실패원인(loadUser 부재)이 같은 tx2 checkQualityImprovement에서 이미 검증되므로, 차감을 tx2로 되돌려 후보 영속·차감·작업완료를 원자 완료(record→markSucceeded 순서로 실패 시 SUCCEEDED 미고착).

### 7.5 잔여 백로그 (의도된 트레이드오프 — 운영 텔레메트리 후 재조정)
리뷰가 남긴 Minor 중 **recall/precision 튜닝**은 운영 데이터 없이 손대면 thrash라 백로그로 둔다(리뷰어도 "monitor precision" 권고):
- **AI-07 잔여:** 단위 없는 수치는 grounding 대조에서 전면 제외 → 수사적 날조("핵심 5가지")의 recall 손실. 정밀도 우선의 의도된 수용. 필요 시 단위 없는 수치를 값 대조 유지 + 소수 수사 허용리스트로 좁히는 방향.
- **AI-08 잔여:** "2+음절 한글 2회 등장" + 조사 부착 토큰의 과보존이 길이/압축 처치를 일부 no-op으로(AI-02 효과 희석). 안전 방향(날조 무통과)이나 처치 효능 저하 — 반복 휴리스틱을 길이≥3/불용어로 제한 검토.
- **AI-11 잔여:** 어절 자카드 OR 결합이 유료 DUPLICATION을 넓힘 — 정밀도 모니터링 후 C3를 SUGGESTION 강등 또는 임계 상향 검토.
- **B9/B10:** 생성·산출물 목록 무페이지네이션, 재생성 인메모리 락 → 다중 인스턴스 단계 인프라 과제(인지된 트레이드오프, §304).
- **B2 잔여:** 처리시점 재점검은 단일 워커 기준 정확, 다중 인스턴스 동시 read 여지 → H1(다중 인스턴스)과 함께.

