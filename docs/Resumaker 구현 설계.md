이 문서는 'Resumaker'의 도메인 명세(`Resumaker 도메인 이해.md`)를 실제 구현으로 옮기기 위한 설계를 담는다. 도메인 명세가 "무엇을·왜"라면, 이 문서는 "어떻게"다. 도메인 규칙이 바뀔 때와 구현이 바뀔 때를 분리해 추적하기 위해 둘을 다른 문서로 둔다.

작성·구현 시 다음 가이드를 우선 적용한다.
- `Spring 아키텍처 가이드.md` (DDD Rich Domain Model, VO, 계층 협력)
- `Spring 검증 로직 배치 가이드.md` (검증 위치)
- `Spring 트랜잭션 분리 가이드.md` (외부 호출과 트랜잭션 경계)
- `Spring 테스트 가이드.md`, `Spring 컨벤션 가이드.md`
- `UX 에러 응답 가이드.md` (에러 응답)

> 표기 규칙: 도메인 용어는 명세서의 용어를 그대로 쓴다(이력서/포트폴리오/산출물/생성 항목/버전/생성 근거 등). 코드 식별자는 영문 도메인 용어를 따른다.

---

# 1. 기술 스택 결정

| 영역 | 선택 | 근거 |
|---|---|---|
| 백엔드 | Kotlin + Spring Boot (Spring MVC) | CLAUDE.md 확정. 아키텍처 가이드가 MVC 3계층 전제. |
| 영속성 | Spring Data JPA + **PostgreSQL** | 데이터가 강한 관계형(사용자–경험 기록–산출물–버전–생성 항목–생성 근거)이고, 소유 격리·버전 스냅샷·생성의 정합성 경계가 트랜잭션을 요구한다. 문서 지향 DB는 버전 스냅샷 간 정합성·소유 격리 쿼리에 불리. |
| AI 생성 | 외부 LLM(공급자 추상화한 포트) | 호출당 비용·실패 확률이 높음 → 포트로 추상화하고 트랜잭션 밖에서 호출(7장). |
| 웹 클라이언트 | Compose Multiplatform(Wasm) | CLAUDE.md 확정. 본 문서는 서버 도메인에 집중하고, 클라이언트는 API 계약·에러 표현만 다룬다. |

> DB 선택은 CLAUDE.md가 "확정 후 명세 수정 필요"로 남긴 항목이다. 본 문서에서 PostgreSQL로 확정하며, CLAUDE.md의 Database 항목도 이에 맞춰 갱신해야 한다. (DB 종속 코드는 Repository 인터페이스 뒤로 격리하므로 추후 교체 비용은 제한적이다.)

---

# 2. 모듈·패키지 구조

기존 멀티모듈(`core`, `app/shared`, `app/webApp`, `server`)을 유지한다. 도메인 모델과 비즈니스 로직은 `server`에 둔다.

```
server/src/main/kotlin/watson/resumaker/
  account/          # 사용자·인증·계정 삭제
  experience/       # 경험 기록, 경험 묶음
  target/           # 목표 정보
  artifact/         # 산출물·버전·생성 항목·생성 근거·채택
  generation/       # AI 생성 포트, 생성 유스케이스, 자동 검증
  guardrail/        # 비용 가드레일 카운터
  common/           # 공통 VO, 예외, 에러 응답, 시간/식별자
```

각 패키지는 아키텍처 가이드에 따라 `domain`(엔티티·VO·도메인 서비스), `application`(Service 유스케이스), `presentation`(Controller·Mapper·DTO), `infrastructure`(Repository 구현·외부 어댑터)로 나눈다.

---

# 3. 도메인 모델

설계 원칙(아키텍처 가이드): 비즈니스 로직을 도메인 모델 안에 캡슐화하고, 원시 값은 의미 있는 개념일 때 VO로 포장하며, 서비스는 VO만 다룬다.

**엔티티 생성/복원 패턴(검증 가이드):** 모든 엔티티는 주생성자를 private으로 두고, 신규 작성은 `create(...)`, DB 복원은 `retrieve(...)` 정적 팩터리로 분리한다. 생성 맥락별로 검증 규칙과 식별자 처리가 다르기 때문이다(신규는 식별자 발급·전체 불변식, 복원은 저장값 신뢰). 아래 엔티티 표기는 필드 구조를 보이기 위한 것이며 실제 구현은 이 팩터리 패턴을 적용한다.

## 3.1 식별자

모든 애그리거트 루트는 타입 안전 식별자 VO를 가진다(예: `UserId`, `ExperienceRecordId`, `ArtifactId`, `VersionId`, `SectionId`). **식별자는 도메인이 생성 시점에 발급한다(UUID 기반).** 따라서 `create(...)` 팩터리가 식별자를 발급해 보유하고(엔티티의 `id`는 non-null), `retrieve(...)`는 저장된 식별자로 복원한다. (DB 시퀀스 발급으로 바꾸려면 신규 생성 시 식별자를 나중에 채우는 변형이 필요해지므로, 발급 일관성과 `create` 경로의 단순성을 위해 도메인 발급을 택한다.)

## 3.2 account 패키지

```kotlin
class User(
    val id: UserId,
    val credential: Credential,      // 인증 수단(이메일/비밀번호 등) — 수단 세부는 인프라
    val timeZone: UserTimeZone,      // 비용 가드레일 리셋 경계(달력일) 계산용 (도메인 이해 §비용 가드레일)
)
```

- **소유 규칙:** 모든 경험 기록·목표 정보·산출물·버전은 정확히 한 `User`에 귀속된다. 조회·수정·삭제 시 소유자 일치를 강제한다(권한 없는 접근 차단 — 도메인 이해 기능 7).
- **계정 삭제:** 사용자의 모든 귀속 데이터를 함께 삭제한다. "되돌릴 수 없음" 확인은 클라이언트 흐름 + 서버의 명시적 삭제 유스케이스로 처리. 삭제는 정합성이 최우선(부분 삭제 금지)이므로 단일 트랜잭션으로 수행한다 — 데이터 양에 따른 롱 트랜잭션 우려보다 '되돌릴 수 없는 완전 삭제'의 정합성을 우선한다(트랜잭션 분리 가이드: 정합성 최우선).

VO: `UserTimeZone`(유효 시간대 문자열 검증).

## 3.3 experience 패키지

```kotlin
class ExperienceRecord(
    val id: ExperienceRecordId,
    val ownerId: UserId,
    var title: ExperienceTitle,          // 필수
    var type: ExperienceType,            // 필수 (enum)
    var body: ExperienceBody,            // 필수
    var detail: ExperienceDetail,        // 선택 항목 묶음 (STAR·기간·역량)
)

enum class ExperienceType { PROJECT, JOB, EXTRACURRICULAR, AWARD, LEARNING }
```

- **불변식:** 제목·유형·본문은 비어 있을 수 없다. 본문이 비면 생성 재료가 없으므로 거부(기능 1 실패 케이스).
- `ExperienceDetail`은 선택 값 묶음(상황/행동/결과/기간/사용 역량). 모두 비어도 유효.

VO와 검증(검증 가이드: VO 생성자에서 검증):

| VO | 검증 규칙 |
|---|---|
| `ExperienceTitle` | 공백 불가, 최대 길이 |
| `ExperienceBody` | 공백 불가(필수), 최대 길이 |
| `SkillTag` | 공백 불가, 길이 제한 |
| `Period` | 시작 ≤ 종료 |

> 경험 묶음(`ExperienceLibrary`)은 별도 엔티티가 아니라 "한 사용자의 경험 기록 집합"을 가리키는 조회 개념으로 구현한다(전용 Repository 쿼리). 별도 애그리거트로 만들 책임이 없다.

> **정적 회상 보조**(기능 1)는 AI 호출이 없는 정적 텍스트(예시 플레이스홀더·유도 질문)이므로 서버 도메인 로직이 아니라 클라이언트 표현 계층 또는 정적 리소스로 제공한다. 서버는 관여하지 않는다.

## 3.4 target 패키지

```kotlin
class TargetBrief(
    val id: TargetBriefId,
    val ownerId: UserId,
    var recruitDirection: RecruitDirection,  // 필수: 채용 방향 텍스트
    var company: CompanyName?,                // 선택
    var job: JobTitle?,                       // 선택
)
```

- **불변식:** 채용 방향 텍스트는 비어 있을 수 없다(빈 방향 거부 — 기능 2 실패 케이스).
- 저장·재사용 가능(목록 조회).

VO: `RecruitDirection`(공백 불가, 최대 길이), `CompanyName`/`JobTitle`(길이 제한).

## 3.5 artifact 패키지 (핵심 애그리거트)

도메인 이해 "산출물 구조와 상태 모델"을 그대로 옮긴다.

```kotlin
class Artifact(                       // 애그리거트 루트
    val id: ArtifactId,
    val ownerId: UserId,
    val kind: ArtifactKind,           // RESUME | PORTFOLIO
    private val versions: MutableList<Version>,
    private var activeVersionId: VersionId,
) {
    // 항목 채택: 직전 활성 버전을 복제하고 해당 항목만 교체한 새 버전을 만들어 활성으로 전환
    fun adoptSection(sectionId: SectionId, adopted: SectionContent): Version { ... }
    fun activeVersion(): Version = versions.first { it.id == activeVersionId }
    // 버전 보관 상한 초과 시 가장 오래된 버전 정리. 단, 활성 버전은 정리 대상에서 제외한다
    // (도메인 이해 §292 "산출물은 항상 하나의 활성 버전을 가리킨다" 불변식 보존). 사전 고지는 application/presentation에서.
    fun pruneOldestIfExceeds(limit: Int): Version? { ... }
}

class Version(
    val id: VersionId,
    val createdAt: Instant,
    val sections: List<ArtifactSection>,   // 이 시점 산출물 전체 스냅샷
)

class ArtifactSection(
    val id: SectionId,
    val sectionKind: SectionKind,          // 이력서: SUMMARY|CAREER / 포트폴리오: EXPERIENCE_NARRATIVE
    var content: SectionContent,
    var status: SectionStatus,             // GENERATING | GENERATED | GENERATION_FAILED | VALIDATION_FAILED
    val sourceExperienceIds: List<ExperienceRecordId>,  // 생성 근거 층위1 (항목 출처 연결)
    val factGroundings: List<FactGrounding>,            // 생성 근거 층위2 (고정밀 사실 근거)
)

class FactGrounding(                        // 생성 근거 층위2
    val token: FactToken,                   // 산출물에 등장한 수치/고유명사
    val kind: FactKind,                     // NUMERIC | PROPER_NOUN
    val sourceExperienceId: ExperienceRecordId,
    val evidenceText: String,               // 근거가 된 경험 기록 내 문자열
)
```

**카디널리티:** `User 1 — N Artifact`, `Artifact 1 — N Version`, `Version 1 — N ArtifactSection`, `ArtifactSection 1 — N FactGrounding`. 항목 출처 연결은 `ArtifactSection`이 보유한 경험 식별자 목록(N:M은 식별자 목록으로 표현).

**스냅샷 격리(중요):** 생성 항목은 경험 기록의 내용을 **복제**해 보존하고, 경험은 **식별자로만 소프트 참조**한다(하드 FK·cascade 금지). 그래야 경험 기록 삭제 시 과거 산출물 버전이 무너지지 않는다(기능 7). 즉 `sourceExperienceIds`/`FactGrounding.sourceExperienceId`는 삭제된 경험을 가리킬 수 있으며, 이는 정상이다.

**생성 항목의 종류와 경험 카디널리티(도메인 이해):**
- 이력서 `SUMMARY`(요약형): 여러 경험 종합, 특정 경험에 매이지 않음(N:M).
- 이력서 `CAREER`(경력): 하나 이상의 경험에 근거.
- 포트폴리오 `EXPERIENCE_NARRATIVE`: 선택 경험과 1:1.

**항목 후보(`SectionCandidate`):** 재생성 결과는 채택 전까지 버전이 아니다. 후보는 영속 상태이되 버전에 포함되지 않는 임시 엔티티로 두고, 채택 시 `adoptSection`이 새 버전을 만들며 후보를 소비한다. (후보 보관/만료는 인프라 정책.) **후보는 AI 재생성 결과에만 쓴다.** 사용자 직접 편집은 후보 단계를 거치지 않고 편집 내용으로 곧바로 `adoptSection`을 호출해 새 버전을 만든다(1콜).

## 3.6 상태 전이 (ArtifactSection.status)

```
GENERATING ──► GENERATED
           ├─► GENERATION_FAILED   (AI 호출 실패/타임아웃 → 재시도 대기)
           └─► VALIDATION_FAILED   (자동 검증 실패 → 자동 1회 재생성)

VALIDATION_FAILED ──[자동 1회 재생성]──► GENERATED | VALIDATION_FAILED(사용자 안내)
GENERATION_FAILED / VALIDATION_FAILED ──[사용자 재시도]──► (후보 생성) ──[채택]──► 새 버전의 GENERATED 항목
```

- 부분 실패 버전도 정식 버전이다(성공 항목 보존). `Version`은 일부 항목이 `*_FAILED`여도 저장된다.
- **동시성:** 한 생성 항목은 동시에 하나의 진행 중 생성만 가진다. 같은 항목 중복 재생성 요청은 거절(409 — 9장). 항목 단위 낙관적 잠금 또는 진행 상태 플래그로 강제.

---

# 4. 애그리거트 경계와 영속성

- **애그리거트:** `User`, `ExperienceRecord`, `TargetBrief`, `Artifact`(Version·Section·FactGrounding 포함). 애그리거트 간 참조는 식별자로만 한다(예: `Artifact.ownerId`).
- **Repository(도메인 인터페이스, 인프라 구현):**
  - `UserRepository`, `ExperienceRecordRepository`, `TargetBriefRepository`, `ArtifactRepository`, `SectionCandidateRepository`, `GenerationQuotaRepository`.
- **소유 격리:** 모든 조회 쿼리는 `ownerId`를 조건에 포함한다. 서비스는 조회 결과의 소유자를 재검증한다(권한 없는 접근 차단).
- **테이블 개요(PostgreSQL):** `users`, `experience_records`(+ detail 컬럼/임베디드), `target_briefs`, `artifacts`, `versions`, `artifact_sections`, `fact_groundings`, `section_candidates`, `generation_quotas`. 스냅샷 보존을 위해 `artifact_sections`는 경험 기록에 FK 제약을 걸지 않고 식별자 컬럼만 둔다.

> DB·매핑·식별자 발급 등 세부는 인프라 구현 사항이며 도메인/애플리케이션 계층을 오염시키지 않는다.

---

# 5. AI 생성 포트와 트랜잭션 경계

트랜잭션 분리 가이드: 외부 시스템 호출(높은 실패 확률·긴 지연)은 DB 트랜잭션 안에서 수행하지 않는다.

```kotlin
interface ResumeGenerationPort {
    fun generate(material: GenerationMaterial): GenerationOutput   // 동기 호출, 외부 LLM 어댑터
}

// 생성 결과: 각 항목 내용 + 생성 근거(층위1·2)를 구조적으로 함께 받는다 (도메인 이해 §생성 근거)
data class GenerationOutput(
    val sections: List<GeneratedSection>, // content, sourceExperienceIds, factGroundings(token/kind/evidence)
)
```

**유스케이스 흐름(예: 이력서 1차 생성):**
1. (트랜잭션) 입력 검증·재료 적재: 경험 묶음 적재, 빈 묶음이면 거부(기능 3 실패). 목표 정보 채용 방향 필수 확인.
2. (트랜잭션) 비용 가드레일 사전 점검: 1차 생성 잔여 횟수 확인(초과면 거부).
3. **(트랜잭션 밖)** `ResumeGenerationPort.generate(...)` 호출. 항목 단위로 성공/실패 수집(부분 실패 허용).
4. **(트랜잭션 밖)** 자동 검증 수행(6장): 수치·고유명사 근거 대조. 실패 항목은 자동 1회 재생성 시도.
5. (트랜잭션) 결과 영속화: 성공·실패 항목을 함께 담은 새 `Version` 저장, 활성 버전 설정. 가드레일 카운트 차감(최소 한 항목 성공 시 1회). **Response DTO 변환도 이 트랜잭션 내부에서 수행한다**(아키텍처 가이드: Entity를 트랜잭션 범위 밖에서 다루지 않음 → JPA 지연 로딩 경계 보존).

> 핵심: 외부 호출(3·4)은 트랜잭션 밖. DB 트랜잭션은 짧게 유지(1·2 점검, 5 영속화·변환). 부분 실패도 5에서 하나의 버전으로 커밋된다.

---

# 6. 생성 근거·자동 검증 설계

도메인 이해 "자동 검증 규칙"을 결정적으로 구현한다.

- **검증 대상:** 정량 수치, 고유명사 두 범주만 자동 실패 판정. '성과 주장' 등 자유 서술은 제외(생성 지시·사용자 검토).
- **판정 주체:** 결정적 검사(추출 + 문자열 대조). LLM 심사 사용 금지.
- **독립 추출:** AI가 신고한 층위2 근거를 신뢰하지 않고, **산출물 텍스트에서 직접** 수치·고유명사를 추출한 뒤 해당 항목의 출처 경험 기록 본문과 대조한다.

```kotlin
interface GroundingValidator {
    fun validate(section: GeneratedSection, sources: List<ExperienceRecord>): SectionValidationResult
}
// NUMERIC: 산출물에서 수치 토큰 추출 → 동일 숫자값이 출처 본문에 존재하는지 대조
//          (표기 차이 "40%"·"40 퍼센트"는 동일 값으로 간주; 세부 정규화는 구현 세부)
// PROPER_NOUN: 회사명·기술명·프로젝트명 등 추출 → 출처 본문에 문자적 존재 대조
```

- **흐름:** 근거 없는 수치·고유명사가 검출된 항목 → `VALIDATION_FAILED` → 자동 1회 재생성 → 재실패 시 사용자 안내·재시도 경로(부분 실패와 동일 회복).
- **직접 편집 제외:** 자동 검증은 AI 생성·재생성 결과에만 적용. 사용자 직접 편집 내용은 검증하지 않는다(최종 책임 사용자).
- **저장된 층위2의 역할 분리:** AI가 생성 시점에 함께 신고한 `factGroundings`(층위2)는 사용자 탐색·표시용으로 저장하며, 자동 검증의 판정 근거로는 쓰지 않는다(판정은 위 독립 추출 결과로만 수행). 이렇게 역할을 분리해, AI가 토큰을 누락 신고해도 검증 구멍이 생기지 않게 한다.

> 추출 알고리즘(정규식/형태소·고유명사 사전)과 문자 정규화 규칙은 구현 세부다. 도메인 계약은 "수치·고유명사는 출처 본문에 근거가 있어야 통과"이며, 이를 단위 테스트로 1:1 검증한다(수용 기준 17·18).

---

# 7. 비용 가드레일 설계

도메인 이해 "비용 가드레일"의 카운트 대상·차감·리셋을 구현한다.

```kotlin
class GenerationQuota(
    val ownerId: UserId,
    // 1차 생성: 사용자당, 사용자 시간대 달력일 리셋
    // 항목 재생성: 생성 항목당
    // 버전 보관: 산출물당 (Artifact.pruneOldestIfExceeds)
)
```

- **1차 생성:** 사용자당 카운트, 리셋 = 사용자 시간대 기준 달력일(자정). 최소 한 항목 성공 시 1회 차감, 전 항목 실패 시 미차감.
- **항목 재생성:** 생성 항목당 카운트. 사용자 요청 재생성은 최종 성공 시 1회 차감. 검증실패로 인한 자동 재시도는 미차감.
- **버전 보관:** 산출물당 상한. 초과 시 가장 오래된 버전부터 정리하며 정리 전 고지.
- 구체 수치는 운영 설정값(`@ConfigurationProperties`)으로 외부화. 도메인은 "상한 존재 + 카운트 의미"만 고정.

---

# 8. API 설계

계층 협력(아키텍처 가이드): Controller는 원시 값으로 요청을 받고 Mapper로 VO 변환 후 Service 호출. Service는 VO만 다루고 Response DTO 변환(Service Mapper)을 책임진다.

| 동작 | 메서드·경로(예시) | 비고 |
|---|---|---|
| 회원가입 / 로그인 | `POST /auth/signup`, `POST /auth/login` | 인증 수단 세부는 인프라 |
| 계정 삭제 | `DELETE /me` | 귀속 데이터 전체 삭제, 확인 토큰 |
| 경험 기록 CRUD | `POST/GET/PATCH/DELETE /experiences` | 본문 누락 시 검증 에러 |
| 목표 정보 CRUD | `POST/GET/PATCH/DELETE /targets` | 채용 방향 필수 |
| 이력서 생성 | `POST /artifacts/resume` | 경험 선택 + 목표 정보, 빈 묶음 거부, 부분 실패 버전 반환 |
| 포트폴리오 생성 | `POST /artifacts/portfolio` | 경험별 항목 |
| 항목 재생성 | `POST /artifacts/{id}/sections/{sectionId}/regenerate` | 개선 지시 옵션, 동시 1개(중복 409) |
| 채택 | `POST /artifacts/{id}/sections/{sectionId}/adopt` | 후보/편집 결과 → 새 버전·활성 |
| 직접 편집 | `PATCH /artifacts/{id}/sections/{sectionId}` | 편집=즉시 채택(1콜), 새 버전 생성. 후보 단계 없음 |
| 버전 목록·비교·복원 | `GET /artifacts/{id}/versions`, `POST .../versions/{vid}/restore` | 복원 = 활성 전환 |
| 열람·복사 | `GET /artifacts/{id}` | 전체/항목 텍스트 제공(복사는 클라이언트) |

- 모든 산출물·경험·목표 엔드포인트는 인증 주체의 `ownerId`로 소유 격리.
- 요청/응답 DTO는 presentation 계층에 둔다. Service는 Response DTO 변환을 Service Mapper에 위임.

---

# 9. 에러 응답 설계

UX 에러 가이드(예방·해결 방법 제시·사용자 언어·부정 감정 최소화)를 응답 계약에 반영한다.

- **공통 에러 응답 포맷:** `code`(클라이언트 분기용), `message`(사용자 언어, 해결 방법 포함), 필요한 경우 `field`/`action`(예: 재시도, 경험 추가 유도).
- **도메인 예외 → HTTP 매핑(글로벌 핸들러):**
  - 검증 실패(VO·필수 누락) → 400, 어느 항목이 비었는지 안내.
  - 소유/권한 위반 → 403/404(존재 노출 최소화).
  - 빈 경험 묶음 생성 시도 → 409/422 + 경험 기록 유도 액션.
  - 비용 상한 초과 → 429 + 남은 횟수·회복 시점·대안(직접 편집).
  - 동시 재생성 충돌 → 409 + 진행 중 안내.
  - AI 생성 실패(부분) → 200(부분 성공) + 실패 항목 상태·재시도 액션.
- **예방형 처리:** 빈 묶음·빈 방향 등은 버튼 비활성/사전 안내로 에러 자체를 줄인다(클라이언트 협력).

---

# 10. 테스트 전략

테스트 가이드 + 아키텍처 가이드(도메인 단위 테스트 우선)를 따른다.

- **도메인 단위 테스트(모킹 없이):**
  - VO 검증(경험 본문 공백 거부, 채용 방향 필수, 기간 순서 등).
  - `Artifact.adoptSection`: 한 항목 채택 시 다른 항목 불변·새 버전 생성·활성 전환(수용 기준 10·19).
  - 버전 보관 상한 정리(수용 기준 11).
  - 상태 전이(부분 실패 버전 보존, 검증실패→재생성).
- **검증 로직 단위 테스트:** `GroundingValidator` — 근거 있는 수치/고유명사 통과, 없는 토큰 검출(수용 기준 17·18). 표기 차이 동치 케이스 포함.
- **가드레일 단위 테스트:** 카운트 대상·차감 시점·달력일 리셋(수용 기준 15).
- **유스케이스/통합 테스트:** 생성 흐름의 트랜잭션 경계(외부 포트는 테스트 더블), 부분 실패 시 성공분 보존(수용 기준 9), 소유 격리(수용 기준 13·14), Response DTO 변환의 지연 로딩 경계(트랜잭션 내부 변환 확인).
- **동시성 테스트(슬라이스로 못 잡음 → 별도 통합 테스트):** 같은 항목 동시 재생성 거절·낙관적 잠금 충돌(수용 기준 20), 가드레일 카운트 동시 차감 정합성. (테스트 가이드: `@Retryable`/동시성은 별도 통합 테스트.)
- **E2E:** 핵심 가치 흐름(기록→겨냥→생성→다듬기→활용→재사용)과 재로그인 보존·계정 삭제(수용 기준 12·14).
- **수동/탐색 검증:** "다른 목표→다른 강조점"(수용 기준 5), 포트폴리오 서사성(기능 4) 등 LLM 품질 의존 항목은 자동 테스트 대상에서 분리.

---

# 11. 구현 순서 (태스크 분해)

차단 결함과 무관한 영역부터 착수한다(개발 팀장 권고).

1. **계정·경험 기록·목표 정보** — VO·엔티티·Repository·CRUD·소유 격리. (수용 기준 1~5, 13~14)
2. **산출물 도메인 모델 골격** — `Artifact`/`Version`/`ArtifactSection`/`FactGrounding`/후보 + 상태 전이·채택·복제·버전 정리. AI 없이 도메인 단위 테스트로 검증. (수용 기준 7, 10, 11, 19, 20)
3. **AI 생성 포트 + 생성 근거 동시 산출** — `ResumeGenerationPort` 어댑터, 항목 단위 정합성, 부분 실패 버전, 트랜잭션 경계. (수용 기준 6, 8, 9)
4. **결정적 자동 검증 + 검증실패 회복** — `GroundingValidator`, 자동 1회 재생성. (수용 기준 16, 17, 18)
5. **재생성·직접 편집·동시성·버전 정리** — 후보·채택·중복 거절. (수용 기준 10, 11, 20)
6. **비용 가드레일 카운터** — 카운트·차감·리셋. (수용 기준 15)
7. **결과물 활용(열람·복사) + E2E** — 전 흐름 통합. (수용 기준 12)

각 태스크는 CLAUDE.md 버전 관리 규칙(서비스가 온전히 동작하는 단위, 프로덕션 코드+통과하는 테스트, AngularJS 커밋 컨벤션)을 따른다.

---

# 12. 열린 질문·후속 결정

- **인증 수단 구체화:** 이메일/비밀번호 vs 소셜 로그인. 도메인은 "사용자 식별·소유"만 요구하므로 수단은 인프라에서 선택(MVP 단순화 권장: 이메일+비밀번호).
- **AI 공급자·모델 선택:** `ResumeGenerationPort` 어댑터 구현 시 결정(비용·품질). 포트 추상화로 교체 가능.
- **고유명사 추출 방식:** 사전 기반 vs 형태소 분석기. 자동 검증 정밀도/구현 비용 트레이드오프.
- **도메인 명세의 열린 질문 3종**(비용 상한 수치, 무료/유료 경계, 경험 유형 최종 목록)은 운영/제품 결정 후 설정값·enum에 반영.
- **CLAUDE.md 갱신:** Database를 PostgreSQL로 확정 반영 필요.
