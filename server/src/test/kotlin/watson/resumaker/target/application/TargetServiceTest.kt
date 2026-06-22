package watson.resumaker.target.application

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import watson.resumaker.account.domain.UserId
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.target.domain.CompanyName
import watson.resumaker.target.domain.JobTitle
import watson.resumaker.target.domain.RecruitDirection
import watson.resumaker.target.domain.StrategyStatus
import watson.resumaker.target.domain.TargetBrief
import watson.resumaker.target.domain.TargetBriefId
import watson.resumaker.target.infrastructure.TargetBriefRepository
import java.util.UUID

/**
 * [TargetService] 단위 테스트. 레포는 mock — 저장만 하고 추출은 워커가 한다는 계약을 고정한다.
 *
 * 검증: create/update가 status=PENDING으로 두는지, 채용 방향 변경 시 전략 무효화·재PENDING, company/job만 바뀌면
 * 상태 불변, retry가 PENDING으로 리셋, 모든 동작의 소유 격리(타소유·미존재는 404).
 */
class TargetServiceTest {

    private val repository: TargetBriefRepository = mock()
    private val mapper = TargetServiceMapper(ObjectMapper())
    private val service = TargetService(repository, mapper)

    private val ownerId = UserId(UUID.randomUUID())
    private val id = TargetBriefId(UUID.randomUUID())

    private fun stubSave() {
        whenever(repository.save(any<TargetBrief>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun 생성은_전략상태_PENDING으로_저장한다() {
        // given
        stubSave()

        // when
        val response = service.create(
            ownerId,
            CreateTargetCommand(RecruitDirection("백엔드 신입 공고"), CompanyName("토스"), null),
        )

        // then — 추출 전이므로 PENDING·전략 null(워커가 추출).
        assertThat(response.strategyStatus).isEqualTo(StrategyStatus.PENDING)
        assertThat(response.writingStrategy).isNull()
    }

    @Test
    fun 채용방향이_바뀌면_전략을_무효화하고_PENDING으로_되돌린다() {
        // given — READY 상태의 기존 목표.
        val brief = TargetBrief.retrieve(
            id = id,
            ownerId = ownerId,
            recruitDirection = RecruitDirection("기존 방향"),
            company = CompanyName("토스"),
            job = null,
            writingStrategyJson = """{"keywords":[],"tone":"","emphasize":[],"avoid":[],"summary":"요약"}""",
            strategyStatus = StrategyStatus.READY,
        )
        whenever(repository.findByIdAndOwnerId(id, ownerId)).thenReturn(brief)

        // when — 채용 방향을 바꾼다.
        val response = service.update(
            ownerId, id,
            UpdateTargetCommand(RecruitDirection("새 방향"), CompanyName("토스"), null),
        )

        // then — 전략 무효화·재PENDING.
        assertThat(response.strategyStatus).isEqualTo(StrategyStatus.PENDING)
        assertThat(response.writingStrategy).isNull()
        assertThat(brief.writingStrategyJson).isNull()
    }

    @Test
    fun 회사_직무만_바뀌면_전략상태는_그대로다() {
        // given — READY 상태, 채용 방향 동일.
        val brief = TargetBrief.retrieve(
            id = id,
            ownerId = ownerId,
            recruitDirection = RecruitDirection("동일 방향"),
            company = CompanyName("토스"),
            job = null,
            writingStrategyJson = """{"keywords":[],"tone":"","emphasize":[],"avoid":[],"summary":"요약"}""",
            strategyStatus = StrategyStatus.READY,
        )
        whenever(repository.findByIdAndOwnerId(id, ownerId)).thenReturn(brief)

        // when — 채용 방향은 그대로, 회사·직무만 변경.
        val response = service.update(
            ownerId, id,
            UpdateTargetCommand(RecruitDirection("동일 방향"), CompanyName("카카오"), JobTitle("서버")),
        )

        // then — 전략 상태·내용 불변.
        assertThat(response.strategyStatus).isEqualTo(StrategyStatus.READY)
        assertThat(brief.writingStrategyJson).isNotNull()
        assertThat(response.companyName).isEqualTo("카카오")
    }

    @Test
    fun retry는_전략을_PENDING으로_리셋한다() {
        // given — FAILED 상태.
        val brief = TargetBrief.retrieve(
            id = id,
            ownerId = ownerId,
            recruitDirection = RecruitDirection("방향"),
            company = null,
            job = null,
            writingStrategyJson = null,
            strategyStatus = StrategyStatus.FAILED,
        )
        whenever(repository.findByIdAndOwnerId(id, ownerId)).thenReturn(brief)

        // when
        service.retryStrategy(ownerId, id)

        // then
        assertThat(brief.strategyStatus).isEqualTo(StrategyStatus.PENDING)
    }

    @Test
    fun retry는_타소유_미존재면_404() {
        // given (소유 격리)
        whenever(repository.findByIdAndOwnerId(id, ownerId)).thenReturn(null)

        // when and then
        assertThatThrownBy { service.retryStrategy(ownerId, id) }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }
}
