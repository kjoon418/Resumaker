package watson.resumaker.account.presentation

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 회원가입 요청 DTO. Bean Validation은 필수값 null/blank 여부와 비밀번호 최소 길이를 검증한다(검증 가이드).
 * 최소 길이 8자는 제품 기본 강도 정책(추후 제품 결정에 따라 조정 가능).
 * 이메일 형식 등 세부 비즈니스 규칙은 VO/도메인이 검증한다.
 */
data class SignUpRequest(
    @field:NotBlank(message = "이메일을 입력해 주세요.")
    val email: String?,
    @field:NotBlank(message = "비밀번호를 입력해 주세요.")
    @field:Size(min = 8, message = "비밀번호는 8자 이상으로 만들어 주세요.")
    val password: String?,
    // 선택값. 미입력 시 서버 기본 시간대를 사용한다.
    val timeZone: String? = null,
)

/**
 * 회원가입 응답 DTO.
 */
data class SignUpResponse(
    val userId: String,
)

/**
 * 로그인 요청 DTO. Bean Validation은 필수값 null/blank 여부만 검증한다(검증 가이드).
 * 이메일/비밀번호 일치 여부는 서비스가 영속 해시로 검증한다.
 */
data class LoginRequest(
    @field:NotBlank(message = "이메일을 입력해 주세요.")
    val email: String?,
    @field:NotBlank(message = "비밀번호를 입력해 주세요.")
    val password: String?,
)

/**
 * 로그인 응답 DTO. 가입 응답과 동일하게 userId를 돌려준다(클라가 X-User-Id로 사용).
 */
data class LoginResponse(
    val userId: String,
)
