package watson.resumaker.generation.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.ArtifactKind
import java.time.Instant
import java.util.UUID

/**
 * [GenerationJob.retryMode] 분류 단위 테스트('다시 만들기' 동작의 단일 진실 원천). 실패 코드별로 IN_PLACE(일시적·
 * 그 자리 재요청)/EDIT_INPUTS(입력 오류·제작 화면)/NONE(한도 초과·비실패)으로 정확히 갈리는지 검증한다.
 */
class GenerationJobRetryModeTest {

    private val now: Instant = Instant.parse("2026-06-22T00:00:00Z")

    private fun job() = GenerationJob.create(
        ownerId = UserId(UUID.randomUUID()),
        kind = ArtifactKind.RESUME,
        experienceIds = listOf(UUID.randomUUID()),
        targetId = UUID.randomUUID(),
        templateId = null,
        targetCompany = "토스",
        createdAt = now,
    )

    @Test
    fun 일시적_실패는_IN_PLACE다() {
        // AI 일시 불가·예기치 못한 실패는 같은 입력으로 다시 만들면 성공 가능 → 목록에서 바로 재요청.
        assertThat(job().apply { markFailed(GenerationErrorCode.AI_UNAVAILABLE, "x", now) }.retryMode())
            .isEqualTo(GenerationJobRetryMode.IN_PLACE)
        assertThat(job().apply { markFailed(GenerationErrorCode.GENERATION_FAILED, "x", now) }.retryMode())
            .isEqualTo(GenerationJobRetryMode.IN_PLACE)
    }

    @Test
    fun 입력_관련_실패는_EDIT_INPUTS다() {
        // 생성할 항목 없음·재료 삭제는 같은 입력으론 또 실패 → 입력을 바꾸도록 제작 화면으로.
        assertThat(job().apply { markFailed(GenerationErrorCode.NO_CONTENT, "x", now) }.retryMode())
            .isEqualTo(GenerationJobRetryMode.EDIT_INPUTS)
        assertThat(job().apply { markFailed(GenerationErrorCode.SOURCE_MISSING, "x", now) }.retryMode())
            .isEqualTo(GenerationJobRetryMode.EDIT_INPUTS)
    }

    @Test
    fun 한도_초과_실패는_NONE이다() {
        // 재시도해도 같은 결과 → 다시 만들기 버튼 자체를 제공하지 않는다.
        assertThat(job().apply { markFailed(GenerationErrorCode.QUOTA_EXCEEDED, "x", now) }.retryMode())
            .isEqualTo(GenerationJobRetryMode.NONE)
    }

    @Test
    fun 실패가_아닌_작업은_NONE이다() {
        // PENDING(제출 직후)·SUCCEEDED(완료)는 다시 만들기 대상이 아니다.
        assertThat(job().retryMode()).isEqualTo(GenerationJobRetryMode.NONE)
        assertThat(job().apply { markSucceeded(UUID.randomUUID(), now) }.retryMode())
            .isEqualTo(GenerationJobRetryMode.NONE)
    }
}
