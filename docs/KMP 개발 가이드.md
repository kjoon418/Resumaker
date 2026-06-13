# KMP 개발 가이드

---

## 📚 1. KMP 프로젝트 구조 이해하기

KMP는 **"비즈니스 로직은 공유하고(Shared), UI와 플랫폼 기능은 네이티브하게(Native)"** 개발하는 방식입니다.

### 폴더 구조 요약
* **`shared/` (핵심)**: 안드로이드, iOS, 서버에서 공유할 수 있는 코드가 위치합니다.
    * `commonMain`: 순수 Kotlin 코드 (DTO, 도메인 모델, 공통 비즈니스 로직). **모든 플랫폼이 공유합니다.**
    * `androidMain`, `iosMain`: 공통 로직 중 플랫폼별 구현이 필요한 코드 (예: 블루투스, 파일 시스템 접근 등).
* **`server/`**: Spring Boot + Kotlin 기반의 백엔드 서버 모듈입니다.
* **`composeApp` (또는 `androidApp`)**: 안드로이드 앱 프로젝트입니다.
* **`iosApp/`**: iOS 앱 프로젝트입니다. (Xcode 프로젝트 구조)
* **`gradle/libs.versions.toml`**: 프로젝트의 모든 라이브러리 버전과 의존성을 관리하는 파일입니다.

-----

## 📂 2. shared 디렉터리에 무엇을 넣어야 하나요?

### 서버 개발자

#### 1\. DTO

API 통신에 사용하는 Request/Response 모델입니다. 이것을 공유하면 **API 스펙 문서(Swagger 등)를 보고 코드를 칠 필요가 사라집니다.**

* **배치 위치:** `shared/src/commonMain/kotlin/.../model/dto`

#### 2\. 상수

서버와 클라이언트가 공통으로 사용하는 상태 값이나 설정 값입니다.

* **배치 위치:** `shared/src/commonMain/kotlin/.../model/type`

#### 3\. 공통 검증 로직

클라이언트와 서버 양쪽에서 똑같이 검증해야 하는 로직입니다.

* **예시:**
    * `PasswordValidator`: "비밀번호는 8자 이상, 특수문자 포함" 규칙 검사.
        * \-\> 모바일: 입력 시 실시간 붉은색 경고 표시용.
        * \-\> 서버: API 요청 들어왔을 때 최종 방어용.

#### 4\. ❌ `shared`에 넣으면 안 되는 것

* **JPA Entity:** DB와 강하게 결합되므로 `server` 모듈에 있어야 합니다.
* **Spring Bean / Service:** 프레임워크 의존성이므로 공유 불가합니다.

-----

### 모바일 개발자

"화면(UI)을 제외한 모든 앱의 기능"을 넣으면 됩니다.

#### 통신 로직

서버와 통신하는 로직입니다.

* **배치 위치:** `shared/src/commonMain/kotlin/.../network`
* **역할:** `HttpClient`를 생성하고, API를 호출하여 DTO를 받아옵니다.
* **장점:** 안드로이드와 iOS에서 각각 통신 로직을 구성할 필요가 없어집니다.

#### Local Database & Caching

앱 내부에 데이터를 저장하는 로직입니다.

* **배치 위치:** `shared/src/commonMain/kotlin/.../database`
* **역할:** 서버에서 받아온 데이터를 로컬에 캐싱하거나, 오프라인 모드를 지원합니다.

#### ViewModels (Presentation Logic)

화면에 보여줄 데이터를 가공하는 로직입니다. (KMP용 ViewModel 사용)

* **배치 위치:** `shared/src/commonMain/kotlin/.../viewmodel`
* **역할:** `StateFlow`를 통해 UI 상태를 방출(Emit)합니다.
    * Android(Compose)는 이를 `collectAsState()`로 구독.
    * iOS(SwiftUI)는 이를 `SKIE`나 `ObservableObject`로 구독.

#### 플랫폼 특화 기능

공유하고 싶지만, 구현 방법이 OS마다 다른 기능입니다.
- commonMain에 공통 인터페이스를 작성하고, adroidMain과 iosMain에 각각 구현체를 작성합니다.

-----

### 📂 디렉터리 구조 예시

```text
shared/src/commonMain/kotlin/.../
├── model/                  <-- [공통] 서버/모바일 모두 사용
│   ├── dto/                (LoginRequest, UserDto)
│   ├── type/               (UserRole Enum, Constants)
│   └── domain/             (순수 User 객체)
│
├── validation/             <-- [공통] 비즈니스 규칙
│   ├── EmailValidator.kt   (정규식 검사 등)
│   └── PolicyChecker.kt
│
├── network/                <-- [모바일 주도] API 통신
│   ├── AuthApi.kt          (Ktor API Call)
│   └── ApiClient.kt
│
├── database/               <-- [모바일 주도] 로컬 저장소
│   └── AppDatabase.kt      (Room/SQLDelight)
│
└── viewmodel/              <-- [모바일 주도] UI 상태 관리
    └── LoginViewModel.kt   (로그인 로직, StateFlow)
```

-----

### 🚀 개발 워크플로우(요약)

1.  **기획 단계:** 서버/모바일 개발자가 모여 **DTO와 Enum**을 정의하고 `shared`에 작성합니다.
2.  **서버 개발:** `server` 모듈에서 `shared`의 DTO를 가져와 컨트롤러(`@RequestBody`)에서 사용합니다.
3.  **모바일 개발:** `shared`의 `network` 코드를 이용해 서버에 요청을 보내고, `viewmodel`에서 로직을 처리하여 UI에 뿌립니다.

---

## 🔑 4. 환경변수 설정 (Environment Variables)

이 프로젝트는 보안상 민감한 정보(DB 비밀번호, API Key 등)를 코드에 직접 포함하지 않고, 환경변수(.env)를 통해 관리합니다.
서버는 build.gradle.kts 설정에 의해 bootRun시 자동으로 환경변수를 로드합니다.
모바일은 gradle.properties 혹은 local.properties를 통해 별도로 주입해 로드합니다.

### 설정 순서

1. application.yml에 환경변수를 선언합니다. (ex_ `secret_key: ${SECRET_KEY}`)
2. 프로젝트 루트에 있는 `.env.example` 파일을 복사하여 `.env` 파일을 생성합니다.
3. `.env` 파일에 실제 값을 입력합니다.

> **주의:** 팀원과 공유해야 할 새로운 환경변수가 생기면, 반드시 `.env.example` 파일에도 반영해주세요.

-----

## 📦 5. 라이브러리 의존성 추가 방법

이 프로젝트는 **Version Catalog (`libs.versions.toml`)** 방식을 사용하여 의존성을 관리합니다. `build.gradle.kts`에 직접 버전을 적지 마세요.

### Step 1. Version Catalog에 정의하기

`gradle/libs.versions.toml` 파일을 열고 버전과 라이브러리를 정의합니다.

```toml
[versions]
myLibrary = "1.2.3"

[libraries]
# 사용할 라이브러리 이름 = { module = "그룹:아티팩트", version.ref = "위에서_정의한_버전키" }
my-awesome-lib = { module = "com.example:awesome-lib", version.ref = "myLibrary" }
```

### Step 2. 모듈에 적용하기

사용하려는 모듈(예: `shared`, `server`)의 `build.gradle.kts` 파일을 열고 의존성을 추가합니다.

**Case A: 모든 플랫폼에서 사용하는 로직 (Shared/commonMain)**

```kotlin
// shared/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.my.awesome.lib) // . 대신 - 는 . 으로 자동 변환됨
        }
    }
}
```

**Case B: 서버에서만 사용하는 라이브러리 (Server)**

```kotlin
// server/build.gradle.kts
dependencies {
    implementation(libs.my.awesome.lib)
}
```
