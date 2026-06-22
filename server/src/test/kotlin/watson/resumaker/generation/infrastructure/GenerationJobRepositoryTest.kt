package watson.resumaker.generation.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.ArtifactKind
import watson.resumaker.generation.domain.GenerationJob
import watson.resumaker.generation.domain.GenerationJobStatus
import java.time.Instant
import java.util.UUID

/**
 * [GenerationJobRepository] @DataJpaTest(H2). 엔티티 매핑·소유 격리·큐 픽업·원자 클레임·고아 회수 쿼리를 검증한다.
 */
@DataJpaTest
class GenerationJobRepositoryTest {

    @Autowired
    private lateinit var repository: GenerationJobRepository

    @Autowired
    private lateinit var entityManager: TestEntityManager

    private val ownerId = UserId(UUID.randomUUID())
    private val otherOwnerId = UserId(UUID.randomUUID())
    private val baseTime = Instant.parse("2026-06-22T00:00:00Z")

    private fun pending(owner: UserId, createdAt: Instant): GenerationJob = GenerationJob.create(
        ownerId = owner,
        kind = ArtifactKind.RESUME,
        experienceIds = listOf(UUID.randomUUID(), UUID.randomUUID()),
        targetId = UUID.randomUUID(),
        templateId = null,
        targetCompany = "토스",
        createdAt = createdAt,
    )

    @Test
    fun 작업을_저장하고_경험_식별자_컬렉션과_함께_복원한다() {
        // given
        val job = pending(ownerId, baseTime)
        val expectedExpIds = job.experienceIds.toList()

        // when
        val saved = repository.saveAndFlush(job)
        entityManager.clear()
        val found = repository.findByIdAndOwnerId(saved.id, ownerId)!!

        // then — 매핑·컬렉션 순서 보존.
        assertThat(found.kind).isEqualTo(ArtifactKind.RESUME)
        assertThat(found.status).isEqualTo(GenerationJobStatus.PENDING)
        assertThat(found.targetCompany).isEqualTo("토스")
        assertThat(found.attempts).isEqualTo(0)
        assertThat(found.experienceIds).containsExactlyElementsOf(expectedExpIds)
    }

    @Test
    fun 소유자_기준_조회는_본인_작업만_최신순으로_돌려준다() {
        // given — owner 작업 2건(시간 차), otherOwner 1건.
        repository.saveAndFlush(pending(ownerId, baseTime))
        repository.saveAndFlush(pending(ownerId, baseTime.plusSeconds(60)))
        repository.saveAndFlush(pending(otherOwnerId, baseTime))

        // when
        val mine = repository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId)

        // then — 본인 2건, 최신(plus60)이 먼저.
        assertThat(mine).hasSize(2)
        assertThat(mine[0].createdAt).isEqualTo(baseTime.plusSeconds(60))
    }

    @Test
    fun 가장_오래된_PENDING을_먼저_픽업한다() {
        // given
        repository.saveAndFlush(pending(ownerId, baseTime.plusSeconds(60)))
        val oldest = repository.saveAndFlush(pending(ownerId, baseTime))

        // when
        val picked = repository.findFirstByStatusOrderByCreatedAtAsc(GenerationJobStatus.PENDING)

        // then
        assertThat(picked!!.id).isEqualTo(oldest.id)
    }

    @Test
    fun 원자_클레임은_PENDING일_때만_1을_돌려주고_RUNNING으로_바꾼다() {
        // given
        val job = repository.saveAndFlush(pending(ownerId, baseTime))
        val claimAt = baseTime.plusSeconds(5)

        // when — 최초 클레임은 성공(1), 같은 작업 재클레임은 실패(0, 이미 RUNNING).
        val first = repository.claim(job.id, claimAt)
        val second = repository.claim(job.id, claimAt)
        entityManager.clear()
        val reloaded = repository.findById(job.id.value).orElseThrow()

        // then
        assertThat(first).isEqualTo(1)
        assertThat(second).isEqualTo(0)
        assertThat(reloaded.status).isEqualTo(GenerationJobStatus.RUNNING)
        assertThat(reloaded.startedAt).isEqualTo(claimAt)
        assertThat(reloaded.attempts).isEqualTo(1)
    }

    @Test
    fun 시작시각이_기준보다_과거인_RUNNING을_고아로_모은다() {
        // given — 하나는 오래전 시작(고아), 하나는 방금 시작.
        val staleJob = pending(ownerId, baseTime)
        repository.saveAndFlush(staleJob)
        repository.claim(staleJob.id, baseTime) // RUNNING, startedAt=baseTime

        val freshJob = pending(ownerId, baseTime.plusSeconds(10))
        repository.saveAndFlush(freshJob)
        repository.claim(freshJob.id, baseTime.plusSeconds(600)) // RUNNING, startedAt=baseTime+600s
        entityManager.clear()

        // when — cutoff = baseTime+300s. staleJob(=baseTime)만 과거다.
        val cutoff = baseTime.plusSeconds(300)
        val orphans = repository.findByStatusAndStartedAtBefore(GenerationJobStatus.RUNNING, cutoff)

        // then
        assertThat(orphans).hasSize(1)
        assertThat(orphans[0].id).isEqualTo(staleJob.id)
    }

    @Test
    fun 소유자_기준_삭제는_본인_작업만_지운다() {
        // given
        repository.saveAndFlush(pending(ownerId, baseTime))
        repository.saveAndFlush(pending(otherOwnerId, baseTime))

        // when
        repository.deleteByOwnerId(ownerId)
        repository.flush()
        entityManager.clear()

        // then
        assertThat(repository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId)).isEmpty()
        assertThat(repository.findAllByOwnerIdOrderByCreatedAtDesc(otherOwnerId)).hasSize(1)
    }
}
