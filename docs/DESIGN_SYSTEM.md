# Resumaker 디자인 시스템 (Design System)

> 이 문서는 개발자가 **이 문서만 보고** Compose Multiplatform(Web/Wasm) 클라이언트를 구현할 수 있도록 작성된 계약(contract)이다.
> 모든 값은 `design/` 레퍼런스에서 추출했거나 명확한 근거를 함께 적었다. 임의 추정 값은 사용하지 않는다.
>
> 참고 문서: 팀 브리프(`.omc/handoffs/team-brief.md`), 도메인 명세(`docs/Resumaker 도메인 이해.md`), KMP 가이드(`docs/KMP 개발 가이드.md`).
>
> **레퍼런스 적응 원칙(필독):** 레퍼런스는 모바일(390px) + 다른 기능(커리어 매니저/면접관 페르소나)이다. **비주얼 스타일만** 차용하고, 백엔드에 없는 기능(회원가입 이름 필드·소셜 로그인·면접관 페르소나·아바타 업로드·학력/경력/자격증 분리 섹션 등)은 이 시스템에 **포함하지 않는다**.

---

## 0. 토큰을 담는 곳 (구현 진입점)

모든 디자인 토큰은 코드에서 단일 출처로 관리한다. 개발자는 아래 객체/테마를 만들고, **화면 코드에서 raw hex·raw dp를 절대 쓰지 않는다**(검토 게이트: "임의 색/간격/라운드 사용 0건").

```
app/shared/src/commonMain/kotlin/watson/resumaker/ui/theme/
 ├─ Color.kt        // RmColors (아래 §2)
 ├─ Type.kt         // RmType  (아래 §3)
 ├─ Dimens.kt       // RmSpacing / RmRadius / RmElevation (아래 §4)
 ├─ ExperienceStyle.kt // 경험유형 5종 매핑 (아래 §6)
 └─ Theme.kt        // RmTheme { MaterialTheme(colorScheme=..., typography=...) } + CompositionLocal
```

권장 패턴: Material3 `MaterialTheme`를 베이스로 깔되, 브랜드 고유 토큰(경험유형 액센트, surface 단계 등 Material 스킴에 1:1로 안 맞는 것)은 `CompositionLocal`(`LocalRmColors`, `LocalRmSpacing`)로 추가 노출한다.

---

## 1. 디자인 원칙 (Design Principles)

레퍼런스 전반(흰 표면 + slate 텍스트 + 단일 블루 액센트 + rounded-2xl + 넉넉한 패딩)에서 도출한 톤이다. 모든 컴포넌트 결정은 이 원칙을 우선한다.

1. **신뢰감 있는 미니멀 (Trustworthy Minimal).** 화면은 흰 표면과 옅은 slate 위에 단 하나의 블루 액센트만 강하게 둔다. 색을 아껴 정보가 스스로 위계를 갖게 한다. — 도메인의 "정직성/신뢰성"(P3 가드레일)을 시각 언어로 옮긴 것.
2. **부드러운 라운드 (Soft Rounded).** 카드·입력·버튼은 `rounded-2xl`(16dp), 칩은 `rounded-xl`(12dp)로 통일한다. 날카로운 모서리를 피해 위협적이지 않고 다가가기 쉬운 인상을 준다.
3. **넉넉한 여백 (Generous Whitespace).** 콘텐츠 가로 패딩 20~24dp, 섹션 간 32~40dp. 빈 공간이 "백지의 공포"를 줄이는 게 아니라, 채워야 할 칸을 또렷이 드러내 회상에 집중하게 한다(도메인 목적 2).
4. **한 화면 한 행동 (One Primary Action).** 화면마다 블루 Primary 버튼은 하나만. 보조 행동은 Ghost/Secondary로 위계를 낮춘다. 레퍼런스의 하단 고정 CTA 패턴을 따른다.
5. **정직한 상태 (Honest State).** "준비 중"·빈 상태·에러를 숨기지 않고 명확한 컴포넌트로 보여준다. 가짜로 동작하는 척하지 않는다(브리프의 신뢰성 가드레일). Empty/Loading/Error는 1급 컴포넌트로 취급한다(§5).

---

## 2. 색상 토큰 (Color Tokens)

### 2.1 색상 통일 결정 (불일치 해소 — 근거 필수)

레퍼런스는 두 갈래의 브랜드 색을 쓴다.

| 출처 | 사용 색 | 위치 |
|------|---------|------|
| 회원가입 화면 / 로그인 화면(png) / 포트폴리오 생성 화면들 | **blue-600 `#2563EB`** | 로고 "Resumaker", Primary 버튼, focus ring, 진행 단계 |
| 홈 화면(커리어 매니저) | indigo-500/600 `#6366F1`/`#4F46E5` | 섹션 아이콘, "관리" 버튼 |

**결정: 브랜드 Primary = `blue-600 #2563EB`로 단일화한다. indigo는 폐기한다.**

근거:
- 사용자가 **처음 만나는 진입 화면(로그인 png·회원가입)** 과 **핵심 흐름(포트폴리오 생성 단계바·CTA)** 이 모두 blue-600이다. indigo는 홈(커리어 매니저, 우리 기능과 다름) 한 화면에만 등장한다 → 다수·핵심 동선이 blue-600.
- 로그인 화면 png(실제 우리 앱에 가장 가까운 레퍼런스)의 로고·버튼이 blue-600 계열이다.
- 단일 액센트 원칙(원칙 1)상 브랜드색은 하나여야 한다. indigo와 blue를 둘 다 쓰면 "어느 게 우리 색인가"가 흐려진다.

홈의 indigo 자리는 모두 blue-600으로 치환한다. 홈에서 indigo가 "섹션 구분 액센트"로 쓰였던 역할은, 우리 화면에선 **경험유형 액센트 팔레트**(§6)가 대신한다.

### 2.2 핵심 팔레트 (Tailwind slate/blue 기준 — 레퍼런스에서 직접 추출)

| 토큰 | 역할 | Hex | Compose `Color(...)` | 추출 근거 |
|------|------|-----|----------------------|-----------|
| **primary** | 브랜드/주요 액션 | `#2563EB` | `Color(0xFF2563EB)` | blue-600 (로고·Primary 버튼) |
| primaryPressed | Primary 눌림 | `#1D4ED8` | `Color(0xFF1D4ED8)` | blue-700 (포트폴리오1 `hover:bg-blue-700`) |
| primaryContainer | 옅은 강조 배경 | `#EFF6FF` | `Color(0xFFEFF6FF)` | blue-50 (AI 안내 박스·배지 배경) |
| onPrimaryContainer | 위 배경 위 텍스트/아이콘 | `#1D4ED8` | `Color(0xFF1D4ED8)` | blue-700 (blue-50 박스 안 텍스트) |
| primaryBorder | primaryContainer 테두리 | `#DBEAFE` | `Color(0xFFDBEAFE)` | blue-100 (`border-blue-100`) |
| onPrimary | Primary 위 텍스트 | `#FFFFFF` | `Color(0xFFFFFFFF)` | white |
| **background** | 앱 캔버스 배경 | `#F8FAFC` | `Color(0xFFF8FAFC)` | slate-50 (홈/마이/완료 `bg-slate-50`,`#F8FAFC`) |
| **surface** | 카드·표면 | `#FFFFFF` | `Color(0xFFFFFFFF)` | white (모든 카드 `bg-white`) |
| surfaceSubtle | 입력칸·옅은 표면 | `#F8FAFC` | `Color(0xFFF8FAFC)` | slate-50 (`bg-slate-50` 인풋) |
| **border** | 기본 테두리(입력) | `#E2E8F0` | `Color(0xFFE2E8F0)` | slate-200 (`border-slate-200` 인풋) |
| borderSubtle | 카드 테두리/디바이더 | `#F1F5F9` | `Color(0xFFF1F5F9)` | slate-100 (`border-slate-100`) |
| **textPrimary** | 본문 제목/주텍스트 | `#0F172A` | `Color(0xFF0F172A)` | slate-900 |
| **textSecondary** | 보조 설명 | `#64748B` | `Color(0xFF64748B)` | slate-500 |
| **textTertiary** | 비활성/캡션/플레이스홀더 | `#94A3B8` | `Color(0xFF94A3B8)` | slate-400 |
| textLabel | 폼 라벨 | `#334155` | `Color(0xFF334155)` | slate-700 (`text-slate-700` 라벨) |
| textBody | 카드 본문 설명 | `#475569` | `Color(0xFF475569)` | slate-600 (`text-slate-600`) |

### 2.3 상태색 (Status)

| 토큰 | 역할 | Hex (전경) | Compose | 옅은 배경 Hex | Compose(배경) | 근거 |
|------|------|-----------|---------|---------------|----------------|------|
| success | 성공/완료 | `#059669` | `Color(0xFF059669)` | `#ECFDF5` | `Color(0xFFECFDF5)` | emerald-600/50 (홈 이력서 아이템 액센트) |
| warning | 주의 | `#D97706` | `Color(0xFFD97706)` | `#FFFBEB` | `Color(0xFFFFFBEB)` | amber-600/50 (마이 수상 트로피 amber) |
| danger | 위험/삭제/탈퇴 | `#EF4444` | `Color(0xFFEF4444)` | `#FEF2F2` | `Color(0xFFFEF2F2)` | red-500계. 마이의 회원탈퇴 `text-red-400`(`#F87171`)보다 한 단계 진하게 해 명도 대비(AA) 확보 — 근거: 파괴적 행동은 충분히 식별돼야 함(도메인 §7 탈퇴 고지) |
| dangerText | 탈퇴 등 위험 텍스트(저강도) | `#F87171` | `Color(0xFFF87171)` | — | — | red-400 (레퍼런스 원본 톤, 비파괴 표기용) |
| info | 정보 안내 | `#2563EB` | `Color(0xFF2563EB)` | `#EFF6FF` | `Color(0xFFEFF6FF)` | blue-600/50 (포트폴리오 안내 박스) = primary와 동일 |

### 2.4 Material3 ColorScheme 매핑

`lightColorScheme(...)`에 아래처럼 매핑한다(다크 테마는 MVP 범위 밖 — light만 제공).

```kotlin
val RmLightColorScheme = lightColorScheme(
    primary            = Color(0xFF2563EB), // primary
    onPrimary          = Color(0xFFFFFFFF),
    primaryContainer   = Color(0xFFEFF6FF), // blue-50
    onPrimaryContainer = Color(0xFF1D4ED8), // blue-700
    secondary          = Color(0xFF64748B), // slate-500 (보조 텍스트/저강조 액션)
    onSecondary        = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF1F5F9), // slate-100 (Secondary 버튼 배경)
    onSecondaryContainer = Color(0xFF334155), // slate-700
    background         = Color(0xFFF8FAFC), // slate-50
    onBackground       = Color(0xFF0F172A), // slate-900
    surface            = Color(0xFFFFFFFF),
    onSurface          = Color(0xFF0F172A),
    surfaceVariant     = Color(0xFFF8FAFC), // 입력칸/옅은 표면
    onSurfaceVariant   = Color(0xFF64748B), // 보조 텍스트
    outline            = Color(0xFFE2E8F0), // slate-200 (입력 테두리)
    outlineVariant     = Color(0xFFF1F5F9), // slate-100 (카드 테두리/디바이더)
    error              = Color(0xFFEF4444),
    onError            = Color(0xFFFFFFFF),
    errorContainer     = Color(0xFFFEF2F2),
    onErrorContainer   = Color(0xFFB91C1C), // red-700
)
```

> 경험유형 액센트(§6)와 success/warning은 Material 스킴 슬롯에 1:1로 안 들어가므로 `RmColors`(CompositionLocal)로 별도 노출한다. Material 슬롯만으로 부족할 때 raw가 새지 않도록 하는 장치다.

---

## 3. 타이포그래피 (Typography)

### 3.1 폰트: Pretendard

레퍼런스 전 화면이 Pretendard를 쓴다(`pretendard.min.css`, `font-family:'Pretendard'`). 한글 가독성·웨이트 범위가 넓어 그대로 채택한다.

**웹(Wasm)에서 Pretendard 로딩 방법:**
- **권장(오프라인·안정):** Pretendard `.ttf`(또는 가변폰트 `PretendardVariable.ttf`)를 `app/webApp/src/webMain/composeResources/font/`(Compose Resources)로 번들하고 `Font(Res.font.pretendard_regular, ...)`로 `FontFamily`를 구성한다. CDN 의존(네트워크 실패 시 깨짐)을 피한다.
- **대안(빠른 부트스트랩):** `webApp`의 `index.html` `<head>`에 레퍼런스와 동일한 CDN 링크(`cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/.../pretendard.min.css`)를 넣고, Compose에서는 `FontFamily.Default`로 두되 CSS가 캔버스 외 텍스트엔 적용되게 한다. **단, Wasm Canvas는 자체 폰트 래스터라이즈를 하므로 CDN CSS만으로는 캔버스 텍스트에 반영되지 않을 수 있다 → 권장안(번들)이 정답.**
- **Fallback 체인:** Pretendard 미로딩 시 `FontFamily.SansSerif`(시스템). 한글이 깨지지 않게 시스템 산세리프로 떨어진다.

웨이트 4종 번들: Regular(400) / Medium(500) / SemiBold(600) / Bold(700). 레퍼런스가 `font-medium/semibold/bold`만 사용하므로 이 4종으로 충분하다.

### 3.2 타입 스케일

레퍼런스의 실제 클래스(text-2xl/xl/lg/base/sm/xs/[15px]/[13px]/[11px]/[10px])에서 추출. (Tailwind: 2xl=24, xl=20, lg=18, base=16, sm=14, xs=12.)

| 토큰 | size(sp) | weight | lineHeight(sp) | 용도 / 레퍼런스 근거 |
|------|----------|--------|----------------|----------------------|
| `displayL` | 24 | Bold(700) | 32 | 화면 대제목 "나만의 커리어를…"(`text-2xl font-bold leading-tight`) |
| `titleL` | 20 | Bold(700) | 28 | 헤더 제목/섹션 큰 제목(`text-xl font-bold`) |
| `headingM` | 18 | Bold(700) | 26 | 섹션 제목 "내 이력서"(`text-lg font-bold`) |
| `headingS` | 17 | Bold(700) | 24 | 탑바 타이틀(`text-[17px] font-bold`) |
| `bodyL` | 16 | Regular(400) | 24 | 본문 설명(`text-base`/포트폴리오 안내문) |
| `bodyM` | 15 | SemiBold(600) | 22 | 카드 아이템 제목(`text-[15px] font-semibold`) |
| `bodyM-r` | 15 | Regular(400) | 22 | 폼 입력 텍스트/select(`text-sm`~`[15px]` 입력값) |
| `bodyS` | 14 | Medium(500) | 20 | 보조 본문/링크(`text-sm`) |
| `label` | 14 | SemiBold(600) | 20 | 폼 라벨 "이메일"(`text-sm font-semibold`) |
| `caption` | 12 | Medium(500) | 16 | 메타/캡션 "최종 수정"(`text-xs`) |
| `captionBold` | 12 | Bold(700) | 16 | 배지/태그 텍스트(`text-[10px]~xs font-bold`) — 실사용 시 11~12 |
| `overline` | 11 | SemiBold(600) | 14 | 마이페이지 라벨(`text-[11px] uppercase tracking-wider`), 자간 +0.5sp |

### 3.3 Material3 Typography 매핑

```kotlin
val RmTypography = Typography(
    displaySmall = TextStyle(fontFamily=Pretendard, fontWeight=Bold,   fontSize=24.sp, lineHeight=32.sp),
    titleLarge   = TextStyle(fontFamily=Pretendard, fontWeight=Bold,   fontSize=20.sp, lineHeight=28.sp),
    titleMedium  = TextStyle(fontFamily=Pretendard, fontWeight=Bold,   fontSize=18.sp, lineHeight=26.sp),
    titleSmall   = TextStyle(fontFamily=Pretendard, fontWeight=Bold,   fontSize=17.sp, lineHeight=24.sp),
    bodyLarge    = TextStyle(fontFamily=Pretendard, fontWeight=Regular,fontSize=16.sp, lineHeight=24.sp),
    bodyMedium   = TextStyle(fontFamily=Pretendard, fontWeight=Regular,fontSize=15.sp, lineHeight=22.sp),
    bodySmall    = TextStyle(fontFamily=Pretendard, fontWeight=Medium, fontSize=14.sp, lineHeight=20.sp),
    labelLarge   = TextStyle(fontFamily=Pretendard, fontWeight=SemiBold,fontSize=14.sp, lineHeight=20.sp), // 버튼 텍스트는 Bold 오버라이드
    labelMedium  = TextStyle(fontFamily=Pretendard, fontWeight=Medium, fontSize=12.sp, lineHeight=16.sp),
    labelSmall   = TextStyle(fontFamily=Pretendard, fontWeight=SemiBold,fontSize=11.sp, lineHeight=14.sp, letterSpacing=0.5.sp),
)
```

> 버튼 라벨은 레퍼런스가 `font-bold`이므로 컴포넌트에서 `fontWeight=Bold`로 오버라이드한다.

---

## 4. 간격 / 그리드 / 라운드 / 그림자 / 모션

### 4.1 Spacing (4dp 배수)

| 토큰 | dp | 용도 근거 |
|------|----|-----------| 
| `space1` | 4 | 미세 간격(라벨-아이콘 gap-1) |
| `space2` | 8 | 칩 내부/작은 gap(gap-2) |
| `space3` | 12 | 카드 내부 행 간(gap-3, space-y-3) |
| `space4` | 16 | 카드 패딩 `p-4`, 입력 좌우 패딩 `px-4` |
| `space5` | 20 | 콘텐츠 가로 패딩 `px-5`, 카드 패딩 `p-5` |
| `space6` | 24 | 콘텐츠 가로 패딩 `px-6`(회원가입), 폼 필드 간 `space-y-6` |
| `space8` | 32 | 섹션 간 `space-y-8`, 폼 그룹 간 |
| `space10` | 40 | 큰 섹션 분리 `mb-10` |

화면 표준: **콘텐츠 가로 패딩 20dp**(`px-5` 다수)를 기본으로, 회원가입/포트폴리오 폼처럼 입력 위주 화면은 24dp(`px-6`)를 허용한다. 한 앱 내 혼용을 피하기 위해 **기본 20dp로 통일**하고 폼 화면도 20dp 권장.

### 4.2 그리드 / 컨테이너 (웹 반응형 카드 그리드)

웹 타깃이므로 **단일 컬럼 고정 규칙은 폐기**한다. 리스트(경험/목표/홈 미리보기)는 반응형 카드 그리드로 펼친다.

- **카드 그리드:** `LazyVerticalGrid`로 구현. 카드 최소 셀폭 **320dp**(`RmSize.gridCellMin`) 기준, 가용 콘텐츠 폭으로 열 수를 계산한 뒤 브레이크포인트 상한으로 클램프한다.
  - Compact(<600px): **1열**
  - Medium(600~1024px): **2열**
  - Expanded(>1024px): **2~3열**(1120dp 컨테이너 ÷ 320dp ≈ 3열)
  - 열 수 계산은 `gridColumnsFor(windowSize, contentWidth)`(테스트 보유)로 단일화한다.
- 그리드 셀·리스트 아이템 간격 가로/세로 모두 `space3`(12dp).
- **폼·세션 화면은 그리드를 쓰지 않는다** — 단일 컬럼 + 640dp 좁은 컨테이너(읽기/입력 가독성). §7 참조.
- 자세한 반응형 컨테이너·브레이크포인트 규칙은 §7.

### 4.3 Radius

| 토큰 | dp | 적용 | 근거 |
|------|----|------|------|
| `radiusSm` | 8 | 작은 배지/select(`rounded-lg`) | `rounded-lg` |
| `radiusChip` | 12 | 아이콘칩(경험유형)(`rounded-xl`) | `rounded-xl` 아이콘칩 |
| `radiusCard` | 16 | 카드·입력·버튼 기본(`rounded-2xl`) | `rounded-2xl` 카드/인풋/버튼 |
| `radiusFull` | 9999 | 알약형 태그·둥근 아바타·진행 점(`rounded-full`) | `rounded-full` |

> 핵심: **카드·입력·버튼은 모두 16dp(`radiusCard`).** 칩(아이콘 배경)은 12dp. 태그/배지는 `radiusFull`.

### 4.4 Elevation / Shadow

Compose Wasm는 `Modifier.shadow(elevation, shape)`를 쓴다. 레퍼런스는 `shadow-sm`(카드)·`shadow-lg`(Primary 버튼)을 사용.

| 토큰 | elevation(dp) | 용도 | 근거 |
|------|---------------|------|------|
| `elevCard` | 1 | 카드 기본(`shadow-sm`) | 카드 `shadow-sm` |
| `elevButton` | 4 | Primary 버튼 떠 있는 느낌(`shadow-lg shadow-blue-600/20`) | 버튼 그림자(블루 틴트) |
| `elevSheet` | 8 | 하단 고정 액션바/스낵바 | `shadow-2xl`/하단 그라데이션 영역 |

> 색 틴트 그림자(`shadow-blue-600/20`)는 Compose에서 `shadow(elevButton, shape, ambientColor=primary.copy(alpha=.2f), spotColor=...)`로 근사. 과하면 elevation만 적용해도 무방.

### 4.5 모션 (Motion)

| 토큰 | 값 | 적용 | 근거 |
|------|----|----|------|
| `pressScale` | 0.98 | 버튼/카드 눌림(`active:scale-[0.98]`) | 전 화면 공통 |
| `pressScaleStrong` | 0.96 | 강조 CTA(`active:scale-[0.96]`) | 홈 "새 이력서 작성하기" |
| `durFast` | 120ms | 색/스케일 트랜지션(`transition`) | `transition-transform/colors` |
| `durBase` | 200ms | 진입/포커스 전환(`transition-all`) | 입력 포커스 |
| easing | `FastOutSlowInEasing` | 기본 | Compose 기본 |

구현: 눌림 스케일은 `Modifier.pointerInput` + `animateFloatAsState(if(pressed) 0.98f else 1f, tween(durFast))` → `graphicsLayer{scaleX=…;scaleY=…}`. 공통 `Modifier.pressScale()` 확장으로 추출 권장.

---

## 5. 컴포넌트 스펙 (Components)

각 컴포넌트는 **치수·색·상태(default/pressed/disabled/error)·Compose 구현 가이드**를 가진다. 모든 색/치수는 §2~§4 토큰만 참조한다.

### 5.1 버튼

#### PrimaryButton
- 치수: `fillMaxWidth`, height **56dp**(`h-14`), radius `radiusCard`(16), 텍스트 `labelLarge`+Bold, `onPrimary`.
- default: bg `primary`, shadow `elevButton`(blue tint).
- pressed: bg `primaryPressed`(#1D4ED8), `scale pressScale(0.98)`.
- disabled: bg `primary` alpha 0.4, 텍스트 `onPrimary` alpha 0.7, shadow 제거, 클릭 불가.
- loading: 텍스트 자리에 16dp `CircularProgressIndicator`(`onPrimary`), 클릭 차단.
- Compose: `Button(colors=ButtonDefaults.buttonColors(containerColor=primary), shape=RoundedCornerShape(16.dp), modifier=Modifier.fillMaxWidth().height(56.dp).pressScale())`. 내부 텍스트 weight=Bold.

#### SecondaryButton (Neutral)
- 홈의 "관리"/마이 "저장하기"(`bg-slate-100 text-slate-700`) 톤.
- 치수: height 56dp(또는 인라인 시 40dp), radius 16, 텍스트 `labelLarge` SemiBold `onSecondaryContainer`(slate-700).
- default: bg `secondaryContainer`(slate-100). pressed: bg slate-200(#E2E8F0), scale 0.98. disabled: alpha 0.4.
- Compose: `FilledTonalButton(colors=…secondaryContainer)`.

#### GhostButton (Outline)
- 포트폴리오1 "건너뛰기"(`border-gray-300 text-gray-700`), 회원가입 소셜버튼 스타일(소셜 자체는 제외).
- 치수: height 56dp, radius 16, `border` 1dp `border`(slate-200), 텍스트 `textLabel`(slate-700) SemiBold, bg transparent.
- pressed: bg `surfaceSubtle`(slate-50). disabled: 텍스트/테두리 alpha 0.4.
- Compose: `OutlinedButton(border=BorderStroke(1.dp, border), …)`.

#### TextLink
- "로그인하기"/"회원가입"(`text-blue-600 font-bold`).
- 텍스트 `bodyS`+Bold, `primary`. pressed: alpha 0.7. (밑줄은 선택; 레퍼런스 일부만 `border-b`)

### 5.2 텍스트필드 (RmTextField)

레퍼런스 입력: `h-14 px-4 bg-slate-50 border border-slate-200 rounded-2xl placeholder:text-slate-400 focus:ring-2 ring-blue-500/20 focus:border-blue-500`.

구조(세로 스택, gap `space2`=8dp):
```
[ label (label 토큰, textLabel, ml 4dp) ]
[ input box: h 56dp ]
[ helper 또는 error (caption) ]
```
- input box: height **56dp**(`h-14`), 좌우 패딩 16dp, radius 16, bg `surfaceSubtle`(slate-50), border 1dp `border`(slate-200), 입력 텍스트 `bodyM-r`(slate-900), placeholder `textTertiary`(slate-400).
- focus: border `primary`(2dp 느낌은 border 1.5~2dp + 외곽 ring) — Compose는 border 색만 `primary`로 바꾸고, 옅은 ring은 `primary.copy(alpha=0.2)` 1dp 외곽선 또는 생략.
- error: border `danger`, helper 자리에 error 메시지(`caption`, `danger`). 아이콘 선택.
- disabled: bg slate-100, 텍스트 `textTertiary`, 클릭 불가.
- 비밀번호: 우측 끝에 24dp eye 토글 아이콘(`textTertiary`), `visualTransformation` 전환.
- multiline(본문/채용방향): 동일 스타일, `minHeight` 100~120dp, top-align, `imeAction` 기본.
- Compose: `OutlinedTextField` 커스텀 또는 `BasicTextField` + `decorationBox`. 권장: `BasicTextField`로 정확한 56dp·색 제어. 라벨/헬퍼는 바깥 Column에서 별도 Text로(레퍼런스가 라벨을 input 위에 분리 배치).

#### SelectField (드롭다운)
- 포트폴리오2 select(`h-[52px] bg-slate-50 border-slate-200 rounded-xl` + chevron-down). 우리 화면에선 **경험유형 선택**에 사용.
- 치수 height 52dp, radius `radiusChip`(12) 또는 16(통일 위해 16 권장), 우측 chevron `textTertiary`.
- Compose: `ExposedDropdownMenuBox` 또는 커스텀 + `DropdownMenu`. 경험유형은 칩 선택(§5.4) 방식도 가능 — 화면 명세(§8)에서 칩 토글 채택.

### 5.3 카드 (RmCard)

레퍼런스 카드: `bg-white p-4 rounded-2xl border border-slate-100 shadow-sm`.
- bg `surface`(white), padding 16dp(`p-4`)~20dp(`p-5`), radius 16, border 1dp `borderSubtle`(slate-100), shadow `elevCard`.
- 클릭 가능 카드(리스트 아이템): `pressScale(0.98)` + ripple. 우측 `chevron-right`(`textTertiary`).
- 변형:
  - **ListItemCard**(경험/목표 리스트): 좌측 아이콘칩(48dp) + 중앙 제목(`bodyM`)·메타(`caption`,`textTertiary`) + 우측 chevron. (홈 resume-item 구조)
  - **InfoCard**(안내/팁): bg `primaryContainer`(blue-50), border `primaryBorder`(blue-100), 좌측 info/lightbulb 아이콘 `primary`, 텍스트 `onPrimaryContainer`. (포트폴리오 `bg-blue-50` 박스)

### 5.4 아이콘칩 (ExperienceIconChip) — 경험유형

레퍼런스: `w-12 h-12 bg-{color}-50 rounded-xl flex items-center justify-center text-{color}-600`(홈 resume 아이콘), 작은 변형 `w-10 h-10 rounded-full`(persona).
- 표준: 48×48dp, radius `radiusChip`(12), bg = 유형 액센트의 옅은배경, 아이콘 24dp = 유형 액센트 전경색(§6).
- 소형(태그 옆): 40×40dp.
- 리스트/생성 폼의 유형 표시에 사용.

### 5.5 배지 / 태그 (Badge, Tag)

- **Badge**(상태/유형 라벨): 알약형 `radiusFull`, padding 좌우 8dp 상하 2dp, 텍스트 `captionBold`(11sp). 색은 맥락색의 `(전경, 옅은배경)` 쌍. 예: 유형 배지 = 경험유형 액센트 쌍, "준비 중" 배지 = (warning, warning-bg). (홈 persona `text-[10px] bg-blue-50 text-blue-600 rounded-full`)
- **SkillTag**(역량/스킬 태그): `#태그` 알약형, bg `primaryContainer` 또는 slate-100, 텍스트 `caption` `onPrimaryContainer`/slate-700, 좌우 12dp. 삭제 가능 시 우측 x(12dp). (포트폴리오2 `#데이터분석` 칩)
- **AddChip**: 점선 테두리(`border-dashed border-slate-300`) 알약, `+ 추가`, 텍스트 `textTertiary`. 스킬 태그 추가용.

### 5.6 헤더 (웹) — PageHeader / AppHeader / BrandTopBar

웹 전환으로 **56dp 모바일 중앙 타이틀 탑바(RmTopBar)는 64dp 전체폭 웹 헤더로 통합**한다. 헤더 막대는 전체폭 `surface` + 하단 1dp `borderSubtle`, 콘텐츠는 본문 컨테이너 폭(1120/640dp)으로 중앙 제한해 본문과 좌우 정렬을 맞춘다.

- **AppHeader**(리스트/홈 — §5.7): 좌측 로고 "Resumaker"(`headingS`+Bold `primary`) + 좌측 정렬 홈/경험/목표 탭 + 우측 끝 계정(마이) 아이콘. height **64dp**. 활성 탭 `primary`+Bold + `primaryContainer` 알약 배경, 비활성 `textSecondary`+Medium. 웹 hover/press 피드백.
- **PageHeader**(폼·세션·상세): 좌측 back chevron(20dp) + 좌측 정렬 타이틀(`headingS`, `textPrimary`) + 우측 옵션 텍스트 액션(`bodyS`+Bold `primary`, 예 "저장"). height 64dp. 콘텐츠 max-width 640dp 중앙. 편집 화면 제목은 헤더 타이틀로, 본문 영역 상단에 보조 페이지 제목(`titleL`)을 둘 수 있다.
- **BrandTopBar**(진입/세션): 중앙 로고 "Resumaker"(`headingS`+Bold `primary`). 세션은 좁은 컨테이너(640dp) 위 단순 브랜드 헤더.
- (구 `RmTopBar`는 컴포넌트로 남기되 화면에서는 미사용 — 헤더로 대체.)

### 5.7 상단 헤더 내비게이션 (AppHeader) — 바텀내비 대체

웹 전환으로 **모바일 바텀 내비게이션은 폐기**하고 **상단 헤더 내비(AppHeader)**로 대체한다.

- **탭 구성:** 로고 + `홈` / `경험` / `목표` 3탭 + 우측 끝 **계정(마이)** 아이콘. 마이는 탭이 아니라 우측 계정 메뉴로 분리한다(4탭 → 3탭 + 계정).
- 활성 탭 `primary`+Bold(+ `primaryContainer` 알약 배경), 비활성 `textSecondary`+Medium. 탭은 좌측 정렬(로고 우측), 계정은 우측 끝.
- 계정 아이콘 = `Person`(user) 24dp, `contentDescription="계정 메뉴"`. 클릭 시 마이페이지로 이동.
- **Compact(<600px) 폴백(선택, P2):** 헤더 탭을 좁은 간격으로 유지하거나, 필요 시 하단 고정 내비/햄버거로 폴백할 수 있다. MVP는 상단 내비로 통일한다. (구 `RmBottomNav`/`RmTab` 컴포넌트는 Compact 폴백 전용으로만 남기고 기본 화면에서는 미사용.)
- Compose: 전체폭 `Row`(64dp) + 텍스트 탭(`pressScale`) + `IconButton`(계정). indicator는 알약 배경으로 표현.

### 5.8 빈 상태 (EmptyState)

도메인의 "백지의 공포 최소화"를 화면에서 직접 책임지는 1급 컴포넌트.
- 구성: 중앙 정렬, 상단 아이콘(48~64dp, `surfaceSubtle` 원형 배경 + `textTertiary` 아이콘), 제목(`headingM`,`textPrimary`), 설명(`bodyS`,`textSecondary`), Primary 액션 버튼.
- 예: 경험 0건 → "아직 기록한 경험이 없어요" + "첫 경험을 기록해 볼까요?" + [경험 기록하기].
- 카피 톤: 비난/공허 금지, 다음 행동 제시(UX 에러 가이드).

### 5.9 로딩 (Loading)

- **전체 로딩:** 중앙 `CircularProgressIndicator`(`primary`, 32dp) + 선택 캡션.
- **스켈레톤(리스트):** 카드 형태의 회색 박스(bg slate-100) shimmer. 리스트 로딩에 권장(레이아웃 점프 방지).
- **버튼 인라인 로딩:** §5.1 참조.

### 5.10 스낵바 / 에러 안내 (Snackbar, ErrorBanner)

UX 에러 가이드 톤(해결 방법 제시 + 부정 감정 최소화 — 도메인 §실패 케이스).
- **Snackbar:** 하단 부유, bg `textPrimary`(slate-900) 0.95, 텍스트 white `bodyS`, radius 12, 우측 액션(`primary`/white Bold, 예 "다시 시도"). 3~4초.
- **InlineError(폼):** §5.2 텍스트필드 error. 메시지 `caption` `danger`.
- **ErrorBanner(섹션/네트워크):** bg `danger`-bg(#FEF2F2), border red-100, 좌측 아이콘 `danger`, 제목+설명, "다시 시도" GhostButton. 막다른 길 금지.
- **에러 카피 규칙:** "무엇이 / 왜 / 어떻게 해결"을 한국어로. 예(본문 누락): "이 경험에서 무슨 일을 했는지 한 줄이라도 적어 주세요. 나중에 더 자세히 보강할 수 있어요." (도메인 명세 그대로).

### 5.11 ComingSoon (준비 중) — 정직성 컴포넌트

산출물 생성(이력서/포트폴리오)은 백엔드 미구현 → **가짜 동작 금지**, 명시적 "준비 중".
- 구성: EmptyState 변형 + "준비 중" Badge(warning 쌍). 아이콘 `sparkles`/`wand`(`primary`, 옅은 배경). 제목 "곧 제공됩니다", 설명 "경험과 목표를 먼저 충실히 쌓아두면, 준비되는 대로 최적의 이력서·포트폴리오를 만들어 드려요." + [경험 기록하기]/[목표 추가하기] 유도 버튼(기록→겨냥 흐름으로 회수).
- 어떤 입력도 받지 않고, 생성 버튼은 비활성 또는 이 화면으로만 진입.

### 5.12 확인 다이얼로그 (ConfirmDialog) — 파괴적 행동

계정 삭제·경험/목표 삭제(도메인 §7 "되돌릴 수 없음 고지").
- bg `surface`, radius 16, padding 24dp. 제목 `headingM`, 설명 `bodyS` `textSecondary`(무엇이 사라지는지 명시).
- 액션: [취소] GhostButton + [삭제] PrimaryButton이되 containerColor=`danger`(파괴적 강조).
- Compose: `AlertDialog`(Material3) 커스텀 컬러.

---

## 6. 경험유형 5종 매핑표 (ExperienceType)

백엔드 enum: `PROJECT, JOB, EXTRACURRICULAR, AWARD, LEARNING`(브리프 §DTO).
홈/마이 레퍼런스의 아이콘칩 색 팔레트(indigo→blue 통일 후 emerald/rose/orange/amber 액센트)를 5종에 1:1 배정한다. Font Awesome은 웹폰트 의존이므로 **Compose Material Icons(`androidx.compose.material.icons`)** 또는 동등 vector로 **대체 매핑**한다.

| enum | 한글 라벨 | 액센트 전경 | 액센트 옅은배경 | Compose Color(전경/배경) | FA 원본(참고) | **Material Icons 대체** | 근거 |
|------|-----------|-------------|------------------|---------------------------|----------------|-------------------------|------|
| `PROJECT` | 프로젝트 | `#2563EB` blue-600 | `#EFF6FF` blue-50 | `Color(0xFF2563EB)`/`Color(0xFFEFF6FF)` | `fa-code` | `Icons.Outlined.Code` (또는 `Terminal`) | 홈 resume `fa-code` indigo→primary로 통일 |
| `JOB` | 직무·인턴 | `#059669` emerald-600 | `#ECFDF5` emerald-50 | `Color(0xFF059669)`/`Color(0xFFECFDF5)` | `fa-briefcase` | `Icons.Outlined.Work` (`BusinessCenter`) | 홈 두번째 아이템 emerald 톤 |
| `EXTRACURRICULAR` | 대외활동 | `#E11D48` rose-600 | `#FFF1F2` rose-50 | `Color(0xFFE11D48)`/`Color(0xFFFFF1F2)` | `fa-users`/`fa-id-card-clip` | `Icons.Outlined.Groups` (`Diversity3`) | 홈 persona rose 액센트 |
| `AWARD` | 수상·자격 | `#D97706` amber-600 | `#FFFBEB` amber-50 | `Color(0xFFD97706)`/`Color(0xFFFFFBEB)` | `fa-trophy` | `Icons.Outlined.EmojiEvents` (트로피) | 마이 수상 `fa-trophy text-amber-500` |
| `LEARNING` | 학습·교육 | `#EA580C` orange-600 | `#FFF7ED` orange-50 | `Color(0xFFEA580C)`/`Color(0xFFFFF7ED)` | `fa-graduation-cap`/`fa-language` | `Icons.Outlined.School` (`MenuBook`) | 홈 `fa-language` orange 톤 |

> 5종 액센트는 서로 충분히 구분되는 색상환 분포(blue/green/rose/amber/orange)를 갖되, amber·orange가 인접하므로 아이콘 형태(트로피 vs 학사모)로 추가 구분한다. 전경색은 모두 명도 대비 AA 이상(옅은 배경 위 사용 아님; 칩 배경은 옅은배경, 아이콘은 전경 = 충분한 대비).
>
> 구현: `data class ExperienceStyle(val label:String, val fg:Color, val bg:Color, val icon:ImageVector)` + `fun ExperienceType.style(): ExperienceStyle` when-매핑. Material Icons extended 의존(`compose.material:material-icons-extended`)을 카탈로그에 추가.

---

## 7. 웹 반응형 규칙 (Responsive)

웹 네이티브 레이아웃이다. **모바일 440dp 단일 컬럼 중앙 정렬 규칙은 폐기**한다(가용 폭의 70~85%를 버리던 구조). 전체폭 헤더 + 중앙 콘텐츠 컨테이너 구조를 채택한다.

- **전체폭(100vw) sticky 헤더:** height **64dp**, 전체폭 `surface` + 하단 1dp `borderSubtle`. 좌측 로고 + 홈/경험/목표 탭 + 우측 계정(§5.6/§5.7 AppHeader). 폼·세션은 back chevron + 타이틀 헤더(PageHeader). 헤더는 전체폭이되 내부 콘텐츠는 컨테이너 폭으로 중앙 제한.
- **콘텐츠 컨테이너(중앙 정렬, 두 폭 구분):**
  - **리스트/홈 = max-width 1120dp**(`RmSize.contentMaxWide`). 카드 그리드로 가로 공간 활용.
  - **폼·세션 = max-width 640dp**(`RmSize.contentMaxNarrow`). 긴 텍스트 입력·읽기 최적폭(한글 행당 40~50자).
  - 컨테이너는 `widthIn(max=…).fillMaxWidth()`로 — Expanded는 제한, 그 이하는 가용폭.
- **좌우 패딩(브레이크포인트별):** 데스크톱 **32dp** / 태블릿 **24dp** / 모바일 **20dp**(`WindowSize.pagePadding()`).
- **배경:** 페이지 `background`(slate-50), 카드 `surface`(white). 컨테이너 바깥 여백은 그대로 `background`.
- **브레이크포인트(웹 표준):**

  | 구간 | 폭 | 레이아웃 | 그리드(리스트) | 좌우 패딩 |
  |------|-----|----------|----------------|-----------|
  | Compact | < 600px | 단일 컬럼, 가용폭 | 1열 | 20dp |
  | Medium | 600 ~ 1024px | 가용폭(헤더 풀노출) | 2열 | 24dp |
  | Expanded | > 1024px | 콘텐츠 max-width 중앙(1120/640) | 2~3열 | 32dp |

- **카드 그리드 최소 셀폭 320dp**(`RmSize.gridCellMin`). 열 수는 `gridColumnsFor(windowSize, contentWidth)`로 계산(§4.2).
- 구현: `BoxWithConstraints`로 `maxWidth` 측정 → `windowSizeFor(maxWidth)`로 구간 판정 → 공통 `AppScaffold(header, contentWidth, content)` 컴포저블이 전체폭 헤더 + 중앙 컨테이너 + 스낵바를 한 곳에서 그린다. content 람다는 `(Modifier, WindowSize)`를 받아 화면이 패딩·열 수를 결정한다.
- **CTA 위치(웹):** 데스크톱 하단 floating bar **폐기**. 리스트는 페이지/섹션 헤더 인라인 "추가" 버튼(`InlineAddButton`), 폼은 폼 하단 인라인 저장 버튼. Compact 하단 고정 폴백은 선택(P2).
- 세로 스크롤: 컨테이너 내부 `verticalScroll`(폼·홈) 또는 `LazyVerticalGrid`(리스트).
- (구 `appColumnWidth/Max/Min`(440/480/360) 토큰은 제거하고 `contentMaxWide/contentMaxNarrow` + 브레이크포인트 토큰으로 대체했다.)

---

## 8. 화면별 레이아웃 명세 (Screens)

브리프 §범위 결정에 따른 **실제 우리 화면**. 각 화면 = ASCII 와이어프레임 + 사용 컴포넌트·토큰. 레퍼런스에 있으나 백엔드에 없는 요소는 제외(이름 필드·소셜·아바타·면접/AI생성 탭 등).

> 공통: 모든 화면은 `AppScaffold`(전체폭 헤더 + 중앙 콘텐츠 컨테이너, §7) 안에 그려진다. 리스트/홈은 1120dp 컨테이너 + 카드 그리드, 폼·세션은 640dp 좁은 컨테이너. 상단은 헤더 내비(홈/경험/목표 + 계정), 데스크톱 하단 floating CTA는 없다.
>
> 인증 필요한 화면은 보관된 `userId`를 `X-User-Id`로 사용(브리프 §API). 세션 없으면 세션 진입 화면으로.
>
> **용어:** 인증은 **로그인(이메일+비밀번호)** 기준이다. 소스 변수명 `userId`는 내부 식별자로 유지하며 사용자에게 노출하지 않는다. 직전 라운드의 "복구 코드/코드로 들어가기"는 로그인 부재 때문이었으므로 obsolete — 사용자 노출 문자열은 "로그인" 용어로 통일한다.
>
> **URL 라우팅(WX-8):** 인메모리 스택을 브라우저 History API와 동기화한다(`/`, `/experiences`, `/experiences/new`, `/experiences/{id}`, `/targets`, `/targets/{id}`, `/artifact`, `/me`, `/session`). 화면 전환=pushState, 루트 전환=replaceState, 브라우저 back/forward=popstate→화면 동기화. 새 의존성(navigation 라이브러리) 없이 `BrowserHistory`(expect/actual) + `Routes`(경로 매핑)로 구현한다.

### 8.1 세션 진입 (가입 / 로그인) — `SignUpScreen`
로그인 화면(png) + 회원가입 화면 비주얼. **가입(`POST /auth/signup`)** 과 **로그인(`POST /auth/login`, 이메일+비밀번호)**으로 구성한다(구현 설계 §278). **640dp 좁은 컨테이너 중앙**. 이름 필드·소셜·약관체크 제외("AI 결과 책임" 캡션은 유지).

```
┌──────────── 전체폭 BrandTopBar (로고 "Resumaker", center) ────────────┐
├──────────────[ 640dp 컨테이너 중앙 ]──────────────┤
│  반가워요!                    │  displayL
│  경험을 기록할 준비가 됐나요?  │
│  AI와 함께 당신의 커리어를…     │  bodyL textSecondary
│                              │
│  [탭] 가입하기 | 로그인        │  ← Segmented (가입 / 로그인)
│                              │
│  이메일                       │  label
│  ┌──────────────────────┐    │  RmTextField (email)
│  비밀번호                     │
│  ┌────────────────[eye]┐    │  RmTextField (password)
│                              │
│  ※ AI 생성 결과물의 책임은…    │  caption textTertiary (가입 탭 한정, 정직성 고지)
│                              │
│  [  가입하고 시작하기  ]       │  PrimaryButton (56dp) (로그인 탭은 "로그인")
│                              │
│ 이미 계정이 있으신가요? 로그인 │  TextLink (로그인 탭은 "처음이신가요? 가입하기")
└──────────────────────────────┘
```
- 컴포넌트: BrandTopBar, RmTextField×2(가입·로그인 공통 이메일+비밀번호), PrimaryButton, TextLink, Segmented.
- "로그인" 탭: 이메일+비밀번호 입력 → `POST /auth/login` 검증 → 성공 시 userId 보관 후 홈. 가입 비번 helper(8자 안내)는 가입 탭에만 표시.
- 에러: 이메일 형식/비번 8자 미만 → InlineError. 가입 실패(중복 등) → Snackbar(서버 한국어 메시지). 로그인 실패는 계정 열거 방지를 위해 이메일/비번을 구분하지 않는 일반 메시지("이메일 또는 비밀번호가 일치하지 않아요.")를 Snackbar로 노출.
- 성공: 가입·로그인 모두 userId를 세션에 보관하고 곧장 홈으로 진입(별도 고지 화면 없음).

### 8.2 홈 대시보드 — `HomeScreen`
홈 화면 비주얼 차용하되 내용은 **우리 도메인**(경험/목표/산출물 진입). 면접관 페르소나·아바타 제외.

```
┌── 전체폭 AppHeader: Resumaker  홈 경험 목표 ······· [계정] ──┐ (홈 탭 활성)
├────────────────[ 1120dp 컨테이너 중앙 ]────────────────┤
│ ▸ 내 경험         전체보기  [+ 경험 기록하기] │ headingM + TextLink + InlineAddButton
│ ┌ [chip]제목 ›┐ ┌ [chip]제목 ›┐ ┌ [chip]제목 ›┐ │ 카드 그리드 (Expanded 2~3열)
│ └────────────┘ └────────────┘ └────────────┘ │ ListItemCard ×N (유형칩+제목+배지)
│ (없으면) EmptyState "첫 경험…"                  │
│                                              │
│ ▸ 내 목표         전체보기  [+ 추가]           │ headingM
│ ┌ 회사·직무/요약 ›┐ ┌ 회사·직무/요약 ›┐         │ 카드 그리드
│ (없으면) EmptyState                            │
│                                              │
│ ▸ 이력서·포트폴리오  [준비 중]                  │ headingM + StatusBadge
│ ┌ ComingSoon InfoCard "경험을 쌓으면 준비됩니다" ┐│ InfoCard (넓은 배너)
└──────────────────────────────────────────────┘
```
- 데이터: `GET /experiences`, `GET /targets`. 각 섹션 최대 3개 미리보기 + 전체보기.
- 레이아웃: 전체폭 헤더 + 1120dp 콘텐츠. 경험·목표 미리보기는 반응형 카드 그리드(Compact 1 / Medium 2 / Expanded 2~3열). 섹션 헤더에 인라인 "추가" 버튼(하단 floating CTA 폐기).
- 산출물 영역: ComingSoon(§5.11) — 넓은 배너 InfoCard, 클릭 시 "준비 중" 안내, 기록→겨냥 유도.

### 8.3 경험 목록 — `ExperienceListScreen`
```
┌── 전체폭 AppHeader (경험 탭 활성) ──┐
├────────[ 1120dp 컨테이너 ]────────┤
│ 내 경험            [+ 경험 기록하기] │ titleL + InlineAddButton (페이지 헤더 행)
│ ┌ [chip]제목 [배지] ×┐ ┌ [chip]제목 ×┐ │ 카드 그리드 (반응형 1/2/3열)
│ │  기간메타          │ │  기간메타     │ │ ListItemCard (유형칩+제목+Badge+기간)
│ └──────────────────┘ └─────────────┘ │
│ (0건) 페이지 헤더 + EmptyState + [기록하기] │
└──────────────────────────────────────┘
```
- 데이터 `GET /experiences`. 아이템 클릭 → 수정 화면. 카드 우측 X 삭제 → ConfirmDialog → `DELETE /experiences/{id}`.
- 페이지 헤더 행에 인라인 "추가" 버튼(하단 floating CTA 폐기). 저장 후 복귀 시 상단 성공 스낵바 1회.

### 8.4 경험 생성·수정 — `ExperienceEditScreen`
도메인 핵심: 필수(제목·유형·본문) + 선택(STAR·기간·역량) + **정적 회상 보조**(본문 플레이스홀더 예시 + 유도 질문 세트).

> **폼 화면은 640dp 좁은 컨테이너 중앙.** 전체폭 PageHeader(back + 타이틀). 저장은 폼 하단 인라인 버튼.

```
┌── 전체폭 PageHeader (back "경험 기록"/"경험 수정") ──┐
├────────────[ 640dp 컨테이너 ]────────────┤
│ 제목 *                        │ label
│ ┌──────────────────────┐     │ RmTextField (필수)
│                              │
│ 유형 *                        │ label
│ [P][J][E][A][L]  ← 5 칩 토글  │ ExperienceIconChip 5종 (선택 1, §6 색)
│                              │
│ 본문 (무엇을 했는가) *         │ label
│ ┌──────────────────────┐     │ RmTextField multiline (minHeight 120dp)
│ │ placeholder: "결제 시스템…│ │   ← 정적 회상 보조 플레이스홀더(도메인 §96)
│ │  40% 개선했습니다."       │ │
│ └──────────────────────┘     │
│ ┌ 💡 이렇게 떠올려보세요 ────┐ │ InfoCard (유도 질문 세트)
│ │ • 내가 직접 한 일은?       │ │   도메인 §99 3문항 그대로
│ │ • 어떤 문제·목표가 있었나?  │ │
│ │ • 결과적으로 뭐가 달라졌나? │ │
│ └──────────────────────────┘ │
│ ───── 선택 항목 (접기/펼치기) ─│ Divider + 펼침 토글
│ 상황(S) / 행동(A) / 결과(R)    │ RmTextField ×3 (multiline)
│ 기간  [시작]~[종료]            │ DatePicker 2개 (LocalDate)
│ 사용 역량·기술                 │ SkillTag 입력 + AddChip
│                              │
│  [   저장   ]                 │ PrimaryButton (폼 하단 인라인)
└──────────────────────────────┘
```
- DTO: `CreateExperienceRequest{title!,type!,body!,detail?{situation,action,result,periodStart,periodEnd,skillTags[]}}`.
- 검증(클라): 제목·유형·본문 빈값 → 해당 필드 InlineError("어느 항목이 비었는지"). 본문 누락 카피 = 도메인 명세 그대로.
- 정적 회상 보조: 본문 빈값일 때 placeholder + 유도질문 InfoCard 항상 표시(수용기준 §3).
- 저장: 신규 `POST /experiences` / 수정 `PATCH /experiences/{id}`.

### 8.5 목표 목록 — `TargetListScreen`
```
┌── 전체폭 AppHeader (목표 탭 활성) ──┐
├────────[ 1120dp 컨테이너 ]────────┤
│ 내 목표            [+ 목표 추가하기] │ titleL + InlineAddButton
│ ┌ 회사·직무 ×┐ ┌ 회사·직무 ×┐       │ 카드 그리드 (반응형)
│ │ 채용방향요약 │ │ 채용방향요약 │       │ ListItemCard (recruitDirection 미리보기)
│ └───────────┘ └───────────┘       │
│ (0건) 페이지 헤더 + EmptyState + [목표 추가]│
└──────────────────────────────────────┘
```
- 데이터 `GET /targets`. 삭제 → ConfirmDialog → `DELETE /targets/{id}`. 저장 후 복귀 시 상단 성공 스낵바 1회.

### 8.6 목표 생성·수정 — `TargetEditScreen`
> **640dp 좁은 컨테이너.** 전체폭 PageHeader(back + 타이틀). 저장은 폼 하단 인라인 버튼.

```
┌── 전체폭 PageHeader (back "목표 추가"/"목표 수정") ──┐
├────────────[ 640dp 컨테이너 ]────────────┤
│ 회사명 (선택)                 │ RmTextField
│ 직무명 (선택)                 │ RmTextField
│ 채용 방향 *                   │ label
│ ┌──────────────────────┐     │ RmTextField multiline (minHeight 140dp)
│ │ placeholder: "이 회사가  │ │   "공고 내용을 붙여넣어도 좋아요"
│ │  원하는 인재상·요구 역량…"  │ │
│ └──────────────────────┘     │
│ ※ 채용공고를 그대로 붙여넣어도 됩니다 │ caption textTertiary
│                              │
│  [   저장   ]                 │ PrimaryButton (폼 하단 인라인)
└──────────────────────────────┘
```
- DTO: `CreateTargetRequest{recruitDirection!, companyName?, jobTitle?}`.
- 검증: recruitDirection 빈값 → InlineError(도메인 §137 카피: "어떤 회사·직무를 겨냥하는지 알려주시면…").

### 8.7 산출물 생성 "준비 중" — `ArtifactComingSoonScreen`
포트폴리오 생성 레퍼런스의 비주얼 톤만 차용. **백엔드 없음 → 가짜 동작 금지**(§5.11).

```
┌── 전체폭 PageHeader (back "이력서·포트폴리오") ──┐
├────────────[ 640dp 컨테이너, 중앙 ]────────────┤
│            ✨ (primary)       │ 아이콘 64dp, blue-50 원형
│        곧 제공됩니다           │ headingM
│  [ 준비 중 ]                  │ Badge(warning)
│  경험과 목표를 충실히 쌓아두면, │ bodyS textSecondary
│  준비되는 대로 최적의 이력서·   │
│  포트폴리오를 만들어 드려요.     │
│                              │
│  [ 경험 기록하기 ]             │ PrimaryButton (기록 유도)
│  [ 목표 추가하기 ]             │ SecondaryButton (겨냥 유도)
└──────────────────────────────┘
```
- 어떤 생성도 실행하지 않음. 기록→겨냥 흐름으로 회수(도메인 핵심 가치 흐름 1·2).

### 8.8 마이 (계정) — `MyPageScreen` (간소화)
마이페이지 레퍼런스에서 **백엔드 범위만**: 이메일 표시, 로그아웃(세션 클리어), 회원 탈퇴(`DELETE /me`). 아바타/성명/학력/경력/자격/수상 섹션은 제외. 로그인이 도입되어 "복구 코드" 노출(표시·복사·보관 고지)은 제거한다. **헤더 계정 메뉴에서 진입**(바텀내비 의존 제거). 640dp 좁은 컨테이너.

```
┌── 전체폭 AppHeader (탭 비활성, 계정 아이콘=현재) ──┐
├────────────[ 640dp 컨테이너 ]────────────┤
│ 마이페이지 / 계정 정보         │ titleL / headingM
│ ┌ 이메일  user@example.com ┐ │ RmCard (read-only)
│                              │
│ 로그아웃                  ›   │ textBody (세션 클리어)
│ 회원 탈퇴                 ›   │ danger 텍스트 → ConfirmDialog
└──────────────────────────────┘
```
- 로그아웃: ConfirmDialog(비파괴 톤) "다시 들어오려면 이메일과 비밀번호로 로그인하면 돼요." → 세션 클리어 → 진입 화면.
- 회원 탈퇴: ConfirmDialog(도메인 §271 "되돌릴 수 없음" 고지: "모든 경험·목표가 함께 삭제됩니다") → `DELETE /me` → 세션 클리어 → 진입 화면.

---

## 9. 검토 체크리스트 (디자인 충실도 — 개발 완료 후 디자이너 검수 기준)

코드가 이 시스템을 따랐는지 PASS/FAIL로 본다.

- [ ] 모든 색이 `RmColors`/ColorScheme 토큰 참조(raw hex 0건).
- [ ] 모든 dp가 `RmSpacing`/`RmRadius` 토큰 참조(raw dp 0건, 단 폭 계산 등 레이아웃 산식 예외).
- [ ] primary = `#2563EB` 단일. indigo 미사용.
- [ ] 카드·입력·버튼 radius=16, 칩=12, 태그=full.
- [ ] 입력 height 56dp, Primary 버튼 height 56dp.
- [ ] Pretendard 적용(또는 fallback 동작).
- [ ] 경험유형 5종이 §6 라벨·아이콘·색으로 매핑.
- [ ] 각 화면이 §8 와이어프레임 구조·컴포넌트와 일치.
- [ ] 정적 회상 보조(플레이스홀더+유도질문) 경험 생성 화면에 존재.
- [ ] 산출물은 ComingSoon(가짜 동작 0건).
- [ ] 에러/빈상태/로딩이 1급 컴포넌트로 구현(막다른 길 0건).
- [ ] 콘텐츠 컨테이너 max-width 1120dp(리스트/홈) / 640dp(폼·세션) 중앙 정렬.
- [ ] 리스트가 반응형 카드 그리드(Compact 1 / Medium 2 / Expanded 2~3열), 카드 최소 셀폭 320dp.
- [ ] 전체폭 64dp 헤더 내비(로고 + 홈/경험/목표 + 계정), 바텀내비·데스크톱 floating CTA 미사용.
- [ ] 사용자 노출 문자열이 "로그인" 용어 기준(변수명 userId는 내부 식별자로 유지, 사용자 노출 없음), a11y 포함.
- [ ] 저장 후 해당 도메인 목록 복귀 + 성공 스낵바 1회, 브라우저 back/forward·새로고침·딥링크 동작(URL 라우팅).
