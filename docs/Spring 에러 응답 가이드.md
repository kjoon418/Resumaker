이 문서는 백엔드 애플리케이션이 에러 상황에 대해 어떻게 동작해야 하는지를 명시하기 위해 작성되었다.

# 좋은 에러 응답의 기준
1. 클라이언트의 개발 도중, 예상하지 못한 에러를 응답받았을 때 디버깅이 용이해야 한다.
2. 클라이언트가 일관된 형식으로 에러 응답을 처리할 수 있어야 한다.
3. 클라이언트가 에러 상황을 쉽게 분기처리 할 수 있어야 한다.
4. 보안 문제로 이어질 수 없는 정보는 응답에서 제외되어야 한다.
5. 서비스 사용자가 아닌 클라이언트 개발자를 위해 작성한다.
   - 서비스 사용자를 위한 에러 메시지는 클라이언트 단에서 작성한다.
   - 목적: 백엔드 에러 메시지가 뷰(UX/UI)에 종속적이지 않게 분리한다.

# 커스텀 상태 코드 도입
목적: 클라이언트가 상태 코드만으로도 예외 상황을 분기처리할 수 있게 만든다.

구체적인 지침:
- 클라이언트가 분기 처리할 때 필요한 만큼만 구체화해 분류한다.
    - 지나친 상태 코드 분류는 유지보수를 어렵게 만드니 지양한다.
    - 지나친 상태 코드 추상화는 클라이언트 분기 처리를 어렵게 만드니 지양한다.
- 커스텀 상태 코드는 HTTP 상태 코드와 별도로 관리된다.
    - 상태 코드에 관한 객체는, HTTP 환경에 의존하지 않는 순수한 객체로 만든다.

코드 예시는 다음과 같다.

```java
public enum ErrorCode {
    INTERNAL_SERVER_ERROR,
    BAD_REQUEST,
    DATABASE_ERROR,
    DATA_INTEGRITY_VIOLATION,

    INVALID_INPUT,
    INVALID_REQUEST,
    INVALID_ID,
    INVALID_DURATION,
    INVALID_RESERVATION,
    INVALID_RESERVATION_TIME,
    INVALID_THEME
}
```

# 응답 형태 통일
목적: 클라이언트가 예외 응답을 일관된 형식으로 다룰 수 있게 만든다.

구체적인 지침:
- 정말 필요한 예외 상황이 아닐 경우, 모든 에러 응답은 `ErrorResponse`라는 클래스 하나로 통일한다.
- 해당 클래스는 기본적으로 다음 속성을 지닌다.
    1. message: 에러를 설명하는 문자열.
    2. errorCode: 에러에 대한 커스텀 상태 코드.

코드 예시는 다음과 같다.

```java
public record ErrorResponse(
        String message,
        ErrorCode errorCode
) {
}
```

# 에러 전역 핸들러 구현
목적: 애플리케이션이 예외를 처리하는 지점을 하나로 통일한다.

구체적인 지침:
- `GlobalExceptionHandler` 클래스를 만들고, 예외별 응답 로직을 구성한다.
- 처리할 예외가 많아지더라도, 예외 핸들러를 여러 클래스로 나누지 않는다.
- 기본적으로 처리할 예외는 다음과 같다.
    - 사용자의 요청이 부적절할 때, 인터셉터/컨트롤러 단에서 Spring Boot에 의해 던져지는 예외
    - 구현한 애플리케이션 내부에서 사용하는 커스텀 예외 및 표준 예외
- 예상하지 못한 예외가 발생하더라도, 사용자에게 일관된 형식으로 응답해야 한다.
    - `Exception`을 핸들링하는 메서드도 작성하고, 500 Internal Error로 응답한다.
- 커스텀 상태 코드를 사용하더라도, HTTP 상태 코드도 세분화해서 응답한다.
    - HTTP 상태 코드는, 예외의 타입별로 할당한다.
- 예외 핸들링 메서드는 정렬 순서는 다음과 같다. (낮은 번호에 해당하는 메서드가 더 상단에 오도록 정렬한다.)
    1. `Exception`(최상위 예외)에 대한 핸들링 메서드
    2. 커스텀 예외에 대한 핸들링 메서드
    3. 표준 예외에 대한 핸들링 메서드
    4. Spring Boot 프레임워크가 기본으로 사용하는 예외에 대한 핸들링 메서드
- 하나의 핸들링 메서드가 커스텀 예외, 표준 예외 등 여러 예외 타입을 처리할 수도 있다.
    - 이 때는, 정렬 기준을 크게 위배하지 않는 선에서 아무 자리에나 배치한다.

코드 예시는 다음과 같다.

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleInternalServerError(
        Exception exception
    ) {
        log.error("[Internal Server Error]", exception);

        ErrorResponse response = new ErrorResponse("예상하지 못한 예외가 발생했습니다.", ErrorCode.INTERNAL_SERVER_ERROR);

        return ResponseEntity.internalServerError()
                .body(response);
    }

    @ExceptionHandler(NotAcceptableReservationException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(
        NotAcceptableReservationException exception
    ) {
        log.warn("[Forbidden]", exception);
        ErrorResponse response = new ErrorResponse(exception.getMessage(), exception.getErrorCode());

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(response);
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<ErrorResponse> handleMissingPathVariable(
            MissingPathVariableException exception
    ) {
        log.warn("[Missing Path Variable]", exception);

        String message = "필수 경로 변수가 누락되었습니다: "
                + exception.getVariableName();
        ErrorResponse response = new ErrorResponse(message, ErrorCode.INVALID_REQUEST);

        return ResponseEntity.badRequest()
                .body(response);
    }

    // ...
}
```

# 예외 메시지 작성
목적: 클라이언트 개발자가 장애 상황을 쉽게 디버깅하도록 돕는다.

구체적인 지침:
- 예외의 원인을 명시한다.
- 보안상 민감한 정보가 노출되지 않는 선에서, 예외 상황과 관련된 정보를 추가로 제공한다.

코드 예시는 다음과 같다.

```java
public record Duration(
        LocalDate startDate,
        LocalDate endDate
) {
    public Duration {
        if (endDate.isBefore(startDate)) {
            String message = "시작일은 종료일과 같거나 앞서야 합니다."
                    + " startDate = " + startDate
                    + " endDate = " + endDate;

            throw new InvalidDomainStateException(ErrorCode.INVALID_DURATION, message);
        }
    }
}
```
