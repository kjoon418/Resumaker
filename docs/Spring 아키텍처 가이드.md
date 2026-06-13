이 문서는 Spring Boot 애플리케이션을 구현할 때, 아키텍처를 어떻게 구성해야 할지 명시하기 위해 작성되었다.

# 좋은 아키텍처의 기준
- 요구사항 변경시 코드 수정 범위가 좁다.
- 요구사항 변경시 어느 파일을 수정해야 할지 예측하기 쉽다.
- 각 계층의 목적이 명확하다.
- 모킹 등의 도구 없이도, 대부분 단위 테스트 작성이 쉽다.
- 대부분 로직을 도메인 단위 테스트로 커버할 수 있다.
- 다른 개발자도 현재 구조를 쉽게 이해할 수 있다.

# 전체 아키텍처 요약

- Spring MVC 아키텍처를 기본으로 하여 `Controller`, `Service`, `Repository` 계층으로 나눈다.

# DDD
DDD를 기반으로 한 Rich Domain Model을 추구한다.

구체적인 지침과 그 목적:
- 비즈니스 로직을 도메인 모델 안에 캡슐화한다.
    - 요구사항이 변경되었을 때, 수정 범위를 도메인 모델 내부로 한정짓는다.
    - 요구사항이 변경되었을 때, 어느 파일을 수정해야 할지 쉽게 예측하게 돕는다.
    - 같은 목적의 로직이 여러 파일에 중복 작성되지 않게 만든다.
- 비즈니스 로직을 가능한 한 서비스가 아닌 도메인에 둔다.
    - 외부 인프라나 모킹에 의존하지 않는 순수 단위 테스트가 가능하게 만든다.
    - 서비스 계층의 로직을 단순화하여, 서비스 로직 작성을 쉽게 만든다.

# 원시 값을 포장하는 VO
목적: 비즈니스 로직을 도메인에 옮기면서도, 한 도메인이 많은 책임을 지니지 않게 세분화한다.

구체적인 지침과 그 목적:
- 어떠한 원시 값을 특정 개념의 인스턴스로 다루는게 자연스러울 때, VO로 감싼다.
- 지닌 책임이 경미한 경우(검증 로직이 지나치게 간단하거나, 비즈니스적으로 큰 의미가 없을 경우)에는 굳이 VO로 감싸지 않고, 원시값을 사용할 수 있다.

구체적인 예시는 다음과 같다.

```java
// "사용자 이름"이라는 개념을 표현하는 VO
public record UserName(
    String value
) {
    private static final int MAXIMUM_LENGTH = 20;

    public UserName {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("사용자 이름은 비어 있을 수 없습니다.");
        }
        if (value.length() > MAXIMUM_LENGTH) {
            throw new IllegalStateException(
                "사용자 이름은 " + MAXIMUM_LENGTH + "자 보다 길 수 없습니다."
                + " value = " + value
            );
        }
    }
}
```

# Controller와 Service 간 협력

## Controller -> Service 방향의 협력(메서드 호출)
목적: Service가 원시 값 대신 VO만 다루도록 제한한다.

구체적인 지침:
- Controller는 `String`, `int` 등의 원시 값으로 클라이언트 요청을 받아올 수 있다.
- Service 메서드의 파라미터는, 원시 값 대신 VO를 사용해야 한다.
- 원시 값을 VO로 변환하는 기능은 Controller 계층의 Mapper 객체가 담당한다.

## Service -> Controller 방향의 협력(메서드 응답)
목적: Entity가 트랜잭션 범위 밖에서 다뤄지지 않도록 제한한다.

구체적인 지침:
- Service 계층에서 Response DTO 변환 책임을 맡는다.
    - Service가 웹 계층의 DTO를 아는건 부적절하지만, API 응답 스펙은 쉽게 바뀌지 않음을 감안해 타협한다.
- Response DTO 변환 세부 로직은 Service 계층의 Mapper가 지닌다.
    - 서비스는 해당 Mapper에 의존해 처리한다.
