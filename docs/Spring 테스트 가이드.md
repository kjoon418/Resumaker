이 문서는 테스트 작성 지침을 위해 작성되었다.

# 테스트가 지켜야 할 가치

1. 객체의 책임이 제대로 수행되는지를 빠르게 검증할 수 있다.
2. 테스트 코드를 읽음으로써, 해당 객체가 지닌 책임을 이해할 수 있다. (명세서 역할을 할 수 있다)

---

# 테스트의 품질을 개선하기 위해 사용할 수 있는 도구

1. 도메인/엔티티 인스턴스의 생성 과정이 테스트 관심사가 아닐 경우, Enum 기반의 테스트 픽스쳐를 도입해, 인스턴스 생성 로직을 간결하게 개선하고, 테스트 간 중복 코드를 줄일 수 있다.
   - 이 때, 각 픽스쳐의 열거형 상수는 본인의 특징을 상수 이름만으로도 표현할 수 있어야 한다. (ex_ "DEFAULT_USER", "DELETED_USER", "BANNED_USER")
   - 픽스쳐가 제공해야 할 도메인/엔티티 생성이, 생성 방법이 복잡한 다른 도메인/엔티티를 의존한다면, 해당 엔티티는 파라미터로 주입받도록 한다.

테스트 픽스쳐에 대한 구체적인 예시는 다음과 같다.

```java
public enum ReservationFixture {
    FUTURE("예약자", LocalDate.now().plusYears(1)),
    PAST("예약자", LocalDate.now().minusYears(1));

    private final String name;
    private final LocalDate date;

    ReservationFixture(String name, LocalDate date) {
        this.name = name;
        this.date = date;
    }

    public Reservation createInstance(Time time, Theme theme) {
        return Reservation.create(new ReserverName(name), date, time, theme, ReservationStatus.ACTIVE);
    }
}
```

2. 실제 환경과 동일한 환경으로 테스트해야할 경우, Testcontainers를 기반으로 한 `IntegrationTestBase` 추상 클래스를 만들고, 테스트가 해당 추상 클래스를 상속하도록 할 수 있다.
    - 다음 케이스에 해당하는 테스트라면, `IntegrationTestBase`를 상속해 테스트하는게 바람직하다.
        - "실제 환경에서도 애플리케이션이 동작한다"를 검증하는게 목적인 E2E 테스트
        - 외부 라이브러리(Kafka, Redis 등) 사용에 문제 없는지 검증하는 테스트

`IntegrationTestBase`에 대한 구체적인 예시는 다음과 같다.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    // ... DB 및 Docker 설정은 실제 운영 환경에 맞춘다 ...

}
```

---

# 테스트 작성 지침

## 한글 사용

테스트 케이스 이름과 유틸 메서드 등에는 한글을 사용할 수 있다.
단, 변수명에 한글을 사용하는건 가능한 지양한다.
변수명은 영어를 사용하며, 해당 변수의 목적/역할/맥락을 잘 표현해야 한다.

구체적인 예시는 다음과 같다.

```java
// 목적/역할/맥락을 잘 표현하지 않는 부적절한 예시
User james = new User("james", UserRole.ADMIN);

// 목적/역할/맥락을 잘 표현한 예시
User adminUser = new User("james", UserRole.ADMIN);
```

## 검증 라이브러리

JUnit5와 org.assertj.core.api.Assertions를 사용하는것으로 통일한다.

## 상수화

인스턴스 생성, 사용 등을 위해 필요한 값이지만 세부 값이 의미가 없을 경우, 이를 상수화해 코드의 가독성을 개선한다.

구체적인 예시는 다음과 같다.

```java
// 부적절한 예시
String userName = "name"; // User 생성을 위해 필요하지만, 실제 값이 "name"인지는 중요하지 않다
User user = new User(userName);

// 적절한 예시
User user = new User(DEFAULT_USER_NAME); // '적절한 값'이라는 의미만 전달하도록 상수화한다.
```

## given-when-then

테스트를 given-when-then 구조로 배치 가능할 경우, 주석으로 각 절을 표현한다.
given-when-then 혹은 given-when and then 구조가 모두 적용하기 어려운 테스트라면, 억지로 이 구조를 도입하지 않는다.

구체적인 예시는 다음과 같다.

```java
// given
User user = UserFixture.DEFAULT_USER.createInstance();

// when
User savedUser = userRepository.save(user);

// then
assertThat(savedUser.getId()).isNotNull();
```

혹은, given-when and then이 적절할 경우 다음과 같이 작성할 수 있다.

```java
// given
String emptyName = "";

// when and then
assertThatThrownBy(() -> new User(emptyName))
    .isInstanceOf(IllegalStateException.class)
    .hasMessage("회원의 이름은 비어 있을 수 없습니다.");
```

위 예시에 상수화를 적용했다면, given-when-then 구조가 적절하지 않으니 도입하지 않는다.
(`emptyName` 값이 `""`인건 의미가 있으니, 실제로는 상수화를 권장하지 않는다.)

```java
assertThatThrownBy(() -> new User(EMPTY_NAME))
    .isInstanceOf(IllegalStateException.class)
    .hasMessage("회원의 이름은 비어 있을 수 없습니다.");
```

## 테스트 케이스 네이밍

테스트 케이스의 이름은 '어떤 상황에서 어떻게 동작하는가'를 직관적이면서 간결하게 표현해야 한다.
또한, @DisplayName 없이 한글로 작성한다.

구체적인 예시는 다음과 같다.

```java
@Test
void 이름이_비어_있을_경우_예외를_던진다() {
    // ...
}

@Nested
class 등급을_계산한다 {

    @Test
    void 점수가_90점_이상이라면_A_등급을_반환한다() {
    	// ...
    }

    @Test
    void 점수가_90점_미만_80점_이상이라면_B_등급을_반환한다() {
    	// ...
    }
}
```

## 테스트 그룹화

동일한 책임에 대해 여러 케이스를 테스트해야 할 경우, 이를 `@Nested`로 그룹화한다.

구체적인 예시는 다음과 같다.

```java

@Nested
class 생성_시점에_값을_검증한다 {

    @Test
    void 이름이_비어_있다면_예외를_던진다() {
    	// ...
    }

    @Test
    void 나이가_음수라면_예외를_던진다() {
    	// ...
    }
}
```

## 예외 검증 방법

예외를 던지는지 검증할 때, 예외의 타입 뿐 아니라 메시지도 검증한다.
같은 맥락으로 같은 예외 메시지가 여러 번 사용될 경우, 이를 상수화한다.
아닐 경우, 상수화하지 않는다.

## given절 내 개행

같은 `given`절 내부의 코드라도, 맥락이 바뀌는 지점에서 한 칸 개행한다.

구체적인 예시는 다음과 같다.

```java
// given
Theme theme = createTheme(); // 필요한 데이터를 준비하는 맥락
Time time = createTime();

Reservation reservation = createReservation(theme, time); // 테스트 대상이 될 인스턴스를 생성하는 맥락

when(theme.getId()).thenReturn(THEME_ID); // 모킹하는 맥락
when(time.getId()).thenReturn(TIME_ID);
```

## Mockito.verify

메서드의 응답 값에 대한 검증만으론 불충분할 경우. `Mockito.verify` 등의 메서드를 사용해 연관 컴포넌트의 호출 횟수를 검증한다.

`verify` 혹은 `verifyNoInteractions` 등이 유효한 상황:
1. 메서드의 반환값만으로, 해당 메서드가 제대로 동작했는지 검증할 수 없을 때
2. 다른 컴포넌트의 특정 메서드를 호출한다는 맥락이 중요할 때
3. 불필요한 호출이 문제가 될 수 있을 때(ex_ 불필요한 레포지토리 호출로 인한 자원 낭비)
4. 다른 컴포넌트에 의존하지 않는다는 맥락이 중요할 때(ex_ `User`를 조회하는 기능은 `OrderRepository`에 의존하지 않아야 함)
5. 의도대로 분기처리하면 호출하지 않아야 할 메서드가 있을 때.
6. 메서드가 예상못한 곳에서 `return`되어서, 호출해야 하는 메서드가 호출되지 않을 수 있을 때
7. 반복문 구성이 잘못되거나 페이징 로직 등이 잘못되어서, 예상보다 훨씬 많은 동작이 수행될 수 있을 때

`verify` 혹은 `verifyNoInteractions` 등이 유효하지 않은 상황:
1. 메서드가 값을 제대로 반환하는지만 검증하면 충분할 때(ex_ 회원 목록 조회 기능)
2. 다른 컴포넌트의 특정 메서드를 호출하는지 여부가 핵심적이지 않을 때(ex_ `UserMapper`를 통해 회원 정보를 응답 형태로 매핑하는지 여부는 '조회 기능'의 핵심이 아니다)
3. (`verifyNoInteractions` 한정) 의존 컴포넌트가 너무 많을 때
4. 해당 컴포넌트에 의존하는지 아닌지가 크게 중요하지 않을 때(ex_ 응답값 매핑에 `UserMapper`를 사용하는지 아닌지는 중요하지 않음)

추가적인 지침:
1. "특정 메서드를 호출한다" 혹은 "특정 메서드를 호출하지 않는다"가 테스트의 핵심에 부합할 경우, Stub을 통해서 검증되는 내용이더라도 `verify`로 추가 검증해서 의도를 명확히 한다.
2. "동일한 `verify` 검증이 너무 많은 케이스에서 불필요하게 중복될 경우" 혹은 "위 조건에는 부합하지만 테스트의 핵심과 벗어난 경우"라면, `verify` 검증을 별도의 테스트 케이스로 분리할 수 있다.
    - 이 때, 동일한 맥락을 지닌 `verify`를 묶어서 하나의 테스트 케이스로 분리한다.

---

# 계층별 테스트 작성 지침 및 목적

이 부분은 계층별 테스트 작성 지침을 다룬다.
`@Retryable` 등을 사용할 경우, 슬라이스 테스트와 별개로 해당 객체에 대한 통합 테스트를 작성해야 한다.

## 컨트롤러 계층 테스트

목적: 컨트롤러가 API 요청에 대해 적절히 동작하는지 검증한다.

구제적인 작성 지침:
1. MockMvc를 이용한 슬라이스 테스트로 작성한다.
2. 정상적으로 요청을 보냈을 때의 응답 형태를 검증한다.
3. 부적절한 요청(필수 파라미터 누락 등)에 대한 응답 형태를 검증한다.
    - 여러 API가 동일한 Request DTO를 사용하면 검증 로직이 중복될 수 있지만, 별개의 API이므로 각각 테스트를 작성해야 한다.
4. 예외 상황에 대한 세부 상태코드(ex_ 404, 409)는 검증하지 않는다. 4XX 상태코드인지만 검증한다.
5. 서비스가 처리하는 비즈니스 로직은 검증하지 않는다. (ex_ 존재하지 않는 id, 값의 형식은 올바르지만 내용이 부적절한 경우)

## 예외 핸들러 테스트

목적: 예외를 의도된 응답 형태로 변환하는지 검증한다.

구체적인 작성 지침:
1. 응답이 의도된 상태 코드를 지녔는지 검증한다.
2. 응답 형태(ex_ ErrorResponse)가 지닌 값이, 의도대로인지 검증한다(ex_ ErrorResponse.message가 의도대로인지 검증한다)

## 서비스 계층 테스트

목적: 서비스가 비즈니스 로직을 적절히 수행하는지 검증한다.

구체적인 작성 지침:
1. Mockito 기반 슬라이스 테스트로 구현한다.
2. 존재하는 모든 해피 케이스, 엣지 케이스, 예외상황을 검증해야 한다. 단, 서비스 테스트 내 다른 케이스에 의해 커버되는 테스트(중복 테스트)는 추가로 작성하지 않는다.

## 레포지토리 계층 테스트

목적: 데이터베이스 로직이 제대로 구현되었는지 검증한다.

구체적인 작성 지침:
1. 저장 기능을 호출해 실제 DB에 제대로 저장되는지, SQL 하드코딩으로 검증한다. (JdbcTemplate을 이용해서, 한 번의 SELECT 쿼리로 저장된 엔티티의 실제 값을 조회해, 의도된 값대로 저장되었는지 비교한다)
2. 조회 기능을 테스트할 때는, 사전작업으로 레포지토리의 저장 기능에 의존한다. 단, 저장 기능이 조회 기능보다 먼저 테스트하게 순서를 강제하고, 저장 기능 테스트가 실패했다면 조회 기능 테스트를 Skip하도록 구현한다.
3. 나머지 기능은, 앞서 검증된 저장과 조회 기능에 의존해서 테스트한다. 이를 통해 SQL 하드코딩을 줄이면서 신뢰도 높은 테스트 코드를 만들어낸다. 이 테스트는 `@Order` 애노테이션을 생략하는 것으로, 저장/조회 기능보다 늦게 수행하도록 한다. 만일 저장/조회 기능 테스트가 실패했다면, 이 테스트들을 Skip하도록 구현한다.

## E2E 테스트

목적: 최소한의 비용으로, 실제 환경에서도 애플리케이션이 잘 동작하는지 검증한다.

구체적인 작성 지침:
1. RestAssured 기반의 통합 테스트로 작성한다.
2. 컨트롤러를 통해 발생할 수 있는 주요 동작들을 검증한다. (ex_ 회원가입-로그인-내 정보 조회)
3. Mock으로 검증할 수 없는 영속성/트랜잭션 로직을 검증한다. (ex_ 지연 로딩으로 인한 LazyInitializationException이나 N+1 문제, 실제 생성/삭제시 데이터베이스 제약조건이 문제되지 않는지 등)
4. E2E 테스트는 비용이 크니, 슬라이스 테스트로 충분히 커버되는 엣지 케이스(에러 상황)는 테스트하지 않는다. 단, 비즈니스적으로 큰 문제가 될 수 있는 엣지 케이스(ex_ 결제, 보안, 데이터 정합성 문제 발생)는 검증해야 한다.
5. 데이터베이스 정리는 별도의 데이터베이스 초기화 유틸을 만들어 사용한다. (가능한 한 `@DirtiesContext`를 사용하지 않는다)
6. 사전 작업을 위한 데이터 생성은 API 호출로 구현한다. 불가하거나 복잡한 경우 Repository를 사용한다.
7. 컨트롤러별 E2E 테스트를 작성하는 것과 별개로, 특정 시나리오를 커버하는 E2E 테스트를 작성해야 할 수 있다.
    - 컨트롤러별 E2E 테스트의 목적: 해당 컨트롤러를 통해 발생할 수 있는 주요 동작을 검증한다
    - 이외 E2E 테스트의 목적: 서비스 흐름상 발생할 수 있는 시나리오에 따라 동작을 검증한다

## 통합 테스트

목적: 슬라이스 테스트만으로 검증할 수 없는 각 객체의 동작을 검증한다.

이 테스트로 검증해야 할 내용의 예시는 다음과 같다. (예시 뿐 아니라 다양한 상황이 이에 해당할 수 있다)
1. 트랜잭션 경계 테스트(중간에 실패했을 때 어디까지 롤백되는지 검증)
2. 재시도 테스트(`@Retryable`이 의도대로 동작하는지 검증)
