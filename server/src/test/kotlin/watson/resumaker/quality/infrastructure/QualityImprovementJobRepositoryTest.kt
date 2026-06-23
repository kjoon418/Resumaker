package watson.resumaker.quality.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import watson.resumaker.account.domain.UserId
import watson.resumaker.quality.domain.QualityCandidate
import watson.resumaker.quality.domain.QualityImprovementJob
import watson.resumaker.quality.domain.QualityImprovementJobStatus
import watson.resumaker.artifact.domain.SectionId
import java.time.Instant
import java.util.UUID

/**
 * [QualityImprovementJobRepository]·[QualityCandidateRepository] @DataJpaTest(H2). 엔티티 매핑·소유 격리·큐 픽업·
 * 원자 클레임·고아 회수·후보 jobId 연결을 검증한다(GenerationJobRepositoryTest 동형).
 */
@DataJpaTest
class QualityImprovementJobRepositoryTest {

    @Autowired
    private lateinit var jobRepository: QualityImprovementJobRepository

    @Autowired
    private lateinit var candidateRepository: QualityCandidateRepository

    @Autowired
    private lateinit var entityManager: TestEntityManager

    private val ownerId = UserId(UUID.randomUUID())
    private val otherOwnerId = UserId(UUID.randomUUID())
    private val baseTime = Instant.parse("2026-06-22T00:00:00Z")

    private fun pending(owner: UserId, createdAt: Instant): QualityImprovementJob = QualityImprovementJob.create(
        ownerId = owner,
        artifactId = UUID.randomUUID(),
        versionId = UUID.randomUUID(),
        findingIds = listOf("s1:I1", "s2:C2"),
        createdAt = createdAt,
    )

    @Test
    fun 작업을_저장하고_소견_식별자_컬렉션과_함께_복원한다() {
        // given
        val job = pending(ownerId, baseTime)
        val expectedFindingIds = job.findingIds.toList()

        // when
        val saved = jobRepository.saveAndFlush(job)
        entityManager.clear()
        val found = jobRepository.findByIdAndOwnerId(saved.id, ownerId)!!

        // then — 매핑·컬렉션 순서 보존.
        assertThat(found.status).isEqualTo(QualityImprovementJobStatus.PENDING)
        assertThat(found.attempts).isEqualTo(0)
        assertThat(found.findingIds).containsExactlyElementsOf(expectedFindingIds)
    }

    @Test
    fun 소유자_기준_조회는_본인_작업만_돌려준다() {
        // given
        val mine = jobRepository.saveAndFlush(pending(ownerId, baseTime))
        jobRepository.saveAndFlush(pending(otherOwnerId, baseTime))

        // when and then — 본인 작업은 조회, 타인 작업은 null(소유 격리 QC8).
        assertThat(jobRepository.findByIdAndOwnerId(mine.id, ownerId)).isNotNull
        assertThat(jobRepository.findByIdAndOwnerId(mine.id, otherOwnerId)).isNull()
    }

    @Test
    fun 가장_오래된_PENDING을_먼저_픽업한다() {
        // given
        jobRepository.saveAndFlush(pending(ownerId, baseTime.plusSeconds(60)))
        val oldest = jobRepository.saveAndFlush(pending(ownerId, baseTime))

        // when
        val picked = jobRepository.findFirstByStatusOrderByCreatedAtAsc(QualityImprovementJobStatus.PENDING)

        // then
        assertThat(picked!!.id).isEqualTo(oldest.id)
    }

    @Test
    fun 원자_클레임은_PENDING일_때만_1을_돌려주고_RUNNING으로_바꾼다() {
        // given
        val job = jobRepository.saveAndFlush(pending(ownerId, baseTime))
        val claimAt = baseTime.plusSeconds(5)

        // when — 최초 클레임 성공(1), 재클레임 실패(0).
        val first = jobRepository.claim(job.id, claimAt)
        val second = jobRepository.claim(job.id, claimAt)
        entityManager.clear()
        val reloaded = jobRepository.findById(job.id.value).orElseThrow()

        // then
        assertThat(first).isEqualTo(1)
        assertThat(second).isEqualTo(0)
        assertThat(reloaded.status).isEqualTo(QualityImprovementJobStatus.RUNNING)
        assertThat(reloaded.startedAt).isEqualTo(claimAt)
        assertThat(reloaded.attempts).isEqualTo(1)
    }

    @Test
    fun 시작시각이_기준보다_과거인_RUNNING을_고아로_모은다() {
        // given (QC6 수렴) — 오래전 시작(고아) vs 방금 시작.
        val staleJob = pending(ownerId, baseTime)
        jobRepository.saveAndFlush(staleJob)
        jobRepository.claim(staleJob.id, baseTime)

        val freshJob = pending(ownerId, baseTime.plusSeconds(10))
        jobRepository.saveAndFlush(freshJob)
        jobRepository.claim(freshJob.id, baseTime.plusSeconds(600))
        entityManager.clear()

        // when — cutoff = baseTime+300s.
        val orphans = jobRepository.findByStatusAndStartedAtBefore(
            QualityImprovementJobStatus.RUNNING,
            baseTime.plusSeconds(300),
        )

        // then
        assertThat(orphans).hasSize(1)
        assertThat(orphans[0].id).isEqualTo(staleJob.id)
    }

    @Test
    fun 후보를_jobId로_연결해_저장하고_조회한다() {
        // given — 한 작업에 후보 2개.
        val jobId = UUID.randomUUID()
        val sectionId = SectionId(UUID.randomUUID())
        candidateRepository.saveAndFlush(
            QualityCandidate.create(jobId, sectionId, "section-0-요약", "원본", "다듬은 후보", listOf("I1", "C2")),
        )
        candidateRepository.saveAndFlush(
            QualityCandidate.create(UUID.randomUUID(), SectionId(UUID.randomUUID()), "k2", "x", "y", listOf("C1")),
        )
        entityManager.clear()

        // when
        val found = candidateRepository.findAllByJobId(jobId)

        // then — 이 jobId의 후보만, 적용 기준 보존.
        assertThat(found).hasSize(1)
        assertThat(found[0].definitionKey).isEqualTo("section-0-요약")
        assertThat(found[0].candidateContent).isEqualTo("다듬은 후보")
        assertThat(found[0].appliedCriterionIds).containsExactly("I1", "C2")
    }
}
