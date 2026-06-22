package watson.resumaker.target.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import watson.resumaker.account.domain.UserId
import watson.resumaker.target.domain.CompanyName
import watson.resumaker.target.domain.JobTitle
import watson.resumaker.target.domain.RecruitDirection
import watson.resumaker.target.domain.StrategyStatus
import watson.resumaker.target.domain.TargetBrief
import java.time.Instant
import java.util.UUID

@DataJpaTest
class TargetBriefRepositoryTest {

    @Autowired
    private lateinit var repository: TargetBriefRepository

    @Autowired
    private lateinit var entityManager: TestEntityManager

    private val ownerId = UserId(UUID.randomUUID())
    private val otherOwnerId = UserId(UUID.randomUUID())
    private val baseTime = Instant.parse("2026-06-22T00:00:00Z")

    @Test
    fun 목표_정보를_저장하고_선택값까지_그대로_복원한다() {
        // given
        val brief = TargetBrief.create(
            ownerId = ownerId,
            recruitDirection = RecruitDirection("대용량 트래픽 백엔드 경험 우대"),
            company = CompanyName("토스"),
            job = JobTitle("백엔드 개발자"),
        )

        // when
        val saved = repository.saveAndFlush(brief)
        val found = repository.findByIdAndOwnerId(saved.id, ownerId)

        // then
        assertThat(found).isNotNull
        assertThat(found!!.recruitDirection.value).isEqualTo("대용량 트래픽 백엔드 경험 우대")
        assertThat(found.company?.value).isEqualTo("토스")
        assertThat(found.job?.value).isEqualTo("백엔드 개발자")
    }

    @Test
    fun 선택값이_없어도_저장과_복원에_성공한다() {
        // given
        val brief = TargetBrief.create(
            ownerId = ownerId,
            recruitDirection = RecruitDirection("프론트엔드 주니어"),
            company = null,
            job = null,
        )

        // when
        val saved = repository.saveAndFlush(brief)
        val found = repository.findByIdAndOwnerId(saved.id, ownerId)

        // then
        assertThat(found).isNotNull
        assertThat(found!!.company).isNull()
        assertThat(found.job).isNull()
    }

    @Test
    fun 다른_사용자의_목표_정보는_조회되지_않는다() {
        // given
        val brief = TargetBrief.create(ownerId, RecruitDirection("내 목표"), null, null)
        val saved = repository.saveAndFlush(brief)

        // when
        val foundByOther = repository.findByIdAndOwnerId(saved.id, otherOwnerId)

        // then
        assertThat(foundByOther).isNull()
    }

    @Test
    fun 소유자_기준_삭제는_본인_데이터만_지운다() {
        // given
        repository.saveAndFlush(TargetBrief.create(ownerId, RecruitDirection("내 목표"), null, null))
        repository.saveAndFlush(TargetBrief.create(otherOwnerId, RecruitDirection("남의 목표"), null, null))

        // when
        repository.deleteByOwnerId(ownerId)
        repository.flush()

        // then
        assertThat(repository.findAllByOwnerId(ownerId)).isEmpty()
        assertThat(repository.findAllByOwnerId(otherOwnerId)).hasSize(1)
    }

    // ── 작성 전략 추출 큐(원자 claim·조건부 쓰기) ──────────────────────────────

    private fun pendingTarget(): TargetBrief =
        TargetBrief.create(ownerId, RecruitDirection("대용량 트래픽 백엔드 공고"), CompanyName("토스"), null)

    @Test
    fun 새_목표는_전략상태_PENDING으로_저장된다() {
        // given
        val saved = repository.saveAndFlush(pendingTarget())
        entityManager.clear()

        // when
        val found = repository.findById(saved.id.value).orElseThrow()

        // then
        assertThat(found.strategyStatus).isEqualTo(StrategyStatus.PENDING)
        assertThat(found.writingStrategyJson).isNull()
    }

    @Test
    fun PENDING_목표를_픽업한다() {
        // given — PENDING 1건, READY 1건.
        val pending = repository.saveAndFlush(pendingTarget())
        repository.saveAndFlush(
            TargetBrief.retrieve(
                id = pendingTarget().id,
                ownerId = ownerId,
                recruitDirection = RecruitDirection("이미 추출됨"),
                company = null,
                job = null,
                writingStrategyJson = "{}",
                strategyStatus = StrategyStatus.READY,
            ),
        )

        // when
        val picked = repository.findFirstByStrategyStatusOrderById(StrategyStatus.PENDING)

        // then
        assertThat(picked!!.id).isEqualTo(pending.id)
    }

    @Test
    fun 원자_클레임은_PENDING일_때만_1을_돌려주고_EXTRACTING으로_바꾼다() {
        // given
        val target = repository.saveAndFlush(pendingTarget())

        // when — 최초 클레임 성공(1), 재클레임 실패(0, 이미 EXTRACTING).
        val first = repository.claimStrategyExtraction(target.id, baseTime)
        val second = repository.claimStrategyExtraction(target.id, baseTime)
        entityManager.clear()
        val reloaded = repository.findById(target.id.value).orElseThrow()

        // then
        assertThat(first).isEqualTo(1)
        assertThat(second).isEqualTo(0)
        assertThat(reloaded.strategyStatus).isEqualTo(StrategyStatus.EXTRACTING)
        assertThat(reloaded.strategyExtractionStartedAt).isEqualTo(baseTime)
    }

    @Test
    fun 결과_쓰기는_EXTRACTING일_때만_적용되고_READY로_전이한다() {
        // given — 클레임으로 EXTRACTING 상태.
        val target = repository.saveAndFlush(pendingTarget())
        repository.claimStrategyExtraction(target.id, baseTime)
        entityManager.clear()

        // when — 결과 쓰기(1행), 같은 쓰기 재시도는 0행(이미 READY).
        val written = repository.writeStrategyResult(target.id, """{"summary":"x"}""")
        val again = repository.writeStrategyResult(target.id, """{"summary":"y"}""")
        entityManager.clear()
        val reloaded = repository.findById(target.id.value).orElseThrow()

        // then
        assertThat(written).isEqualTo(1)
        assertThat(again).isEqualTo(0)
        assertThat(reloaded.strategyStatus).isEqualTo(StrategyStatus.READY)
        assertThat(reloaded.writingStrategyJson).isEqualTo("""{"summary":"x"}""")
    }

    @Test
    fun PENDING으로_돌아간_사이_결과_쓰기는_0행이라_폐기된다() {
        // given — 클레임(EXTRACTING) 후 사용자가 수정해 PENDING으로 되돌린 상황을 모사한다.
        val target = repository.saveAndFlush(pendingTarget())
        repository.claimStrategyExtraction(target.id, baseTime)
        entityManager.clear()
        val claimed = repository.findById(target.id.value).orElseThrow()
        claimed.resetStrategyPending() // EXTRACTING → PENDING(채용 방향 수정 등)
        repository.saveAndFlush(claimed)
        entityManager.clear()

        // when — 워커가 뒤늦게 결과를 쓰려 해도 EXTRACTING이 아니므로 0행.
        val written = repository.writeStrategyResult(target.id, """{"summary":"stale"}""")
        entityManager.clear()
        val reloaded = repository.findById(target.id.value).orElseThrow()

        // then — 결과 폐기, 여전히 PENDING(다음 틱 재추출).
        assertThat(written).isEqualTo(0)
        assertThat(reloaded.strategyStatus).isEqualTo(StrategyStatus.PENDING)
        assertThat(reloaded.writingStrategyJson).isNull()
    }

    @Test
    fun 실패_쓰기는_EXTRACTING일_때만_FAILED로_전이한다() {
        // given
        val target = repository.saveAndFlush(pendingTarget())
        repository.claimStrategyExtraction(target.id, baseTime)
        entityManager.clear()

        // when
        val marked = repository.markStrategyFailed(target.id)
        entityManager.clear()
        val reloaded = repository.findById(target.id.value).orElseThrow()

        // then
        assertThat(marked).isEqualTo(1)
        assertThat(reloaded.strategyStatus).isEqualTo(StrategyStatus.FAILED)
    }

    @Test
    fun 고아_EXTRACTING은_시작시각_기준으로_모은다() {
        // given — 오래전 시작(고아) 1건, 방금 시작 1건.
        val stale = repository.saveAndFlush(pendingTarget())
        repository.claimStrategyExtraction(stale.id, baseTime)
        val fresh = repository.saveAndFlush(pendingTarget())
        repository.claimStrategyExtraction(fresh.id, baseTime.plusSeconds(600))
        entityManager.clear()

        // when — cutoff = baseTime+300s. stale만 과거.
        val orphans = repository.findByStrategyStatusAndStrategyExtractionStartedAtBefore(
            StrategyStatus.EXTRACTING,
            baseTime.plusSeconds(300),
        )

        // then
        assertThat(orphans.map { it.id }).containsExactly(stale.id)
    }
}
