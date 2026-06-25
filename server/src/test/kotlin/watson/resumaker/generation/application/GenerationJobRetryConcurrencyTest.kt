package watson.resumaker.generation.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import watson.resumaker.account.domain.Credential
import watson.resumaker.account.domain.User
import watson.resumaker.account.domain.UserTimeZone
import watson.resumaker.account.infrastructure.UserRepository
import watson.resumaker.artifact.domain.ArtifactKind
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.e2e.CoreValueFlowE2ETest
import watson.resumaker.generation.domain.GenerationErrorCode
import watson.resumaker.generation.domain.GenerationJob
import watson.resumaker.generation.domain.GenerationJobStatus
import watson.resumaker.generation.infrastructure.GenerationJobRepository
import watson.resumaker.generation.presentation.GenerationJobResponse
import watson.resumaker.target.domain.CompanyName
import watson.resumaker.target.domain.RecruitDirection
import watson.resumaker.target.domain.TargetBrief
import watson.resumaker.target.infrastructure.TargetBriefRepository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 일시적 실패 '다시 만들기'(IN_PLACE)의 **동시 재시도 가드** 통합 테스트(@SpringBootTest + 실제 H2 트랜잭션).
 *
 * 같은 실패 작업을 두 스레드가 동시에 재시도해도 비관 락([GenerationJobRepository.findByIdAndOwnerIdForUpdate])이
 * 직렬화해 **새 작업이 정확히 하나만** 생기는지 검증한다 — 이중 생성 = 이중 LLM 호출·이중 가드레일 차감이라
 * 프로젝트가 명시적으로 막는 위험([GenerationJob] 클래스 주석 "이중 비용 방지")이다.
 *
 * 슬라이스(@WebMvcTest)·Mockito 단위 테스트는 락의 실제 동시성 효과를 재현하지 못하므로(가짜 레포는 직렬화하지
 * 않음), 실제 트랜잭션·행 락을 가진 풀 컨텍스트에서 확인한다. 외부 비결정 요소(LLM·Redis)는 E2E와 같은 결정적
 * 더블([CoreValueFlowE2ETest.E2ETestDoubles])로 교체하고, @Scheduled 워커는 모킹해 재시도 결과 PENDING 작업이
 * 처리되지 않게 막아(상태 보존) 순수한 '작업 수' 효과만 본다.
 */
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@Import(CoreValueFlowE2ETest.E2ETestDoubles::class)
class GenerationJobRetryConcurrencyTest {

    @Autowired
    private lateinit var service: GenerationJobService

    @Autowired
    private lateinit var jobRepository: GenerationJobRepository

    @Autowired
    private lateinit var targetRepository: TargetBriefRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    /** @Scheduled 워커가 재시도 결과 PENDING 작업을 백그라운드에서 처리(→RUNNING/SUCCEEDED)하지 못하게 막는다. */
    @MockitoBean
    private lateinit var worker: GenerationJobWorker

    @Test
    fun 같은_실패_작업을_동시에_다시_만들어도_새_작업은_하나만_생긴다() {
        // given — 소유자(테스트별 고유 계정)·목표·일시적(IN_PLACE) 실패 작업을 커밋한다. 가드레일이 소유자 시간대를
        // 읽으려 User를 적재하므로 실제 계정을 저장한다.
        val owner = userRepository.save(
            User.create(
                credential = Credential.of("retry-concurrency-${UUID.randomUUID()}@example.com", "hash"),
                timeZone = UserTimeZone.DEFAULT,
            ),
        )
        val ownerId = owner.id
        val target = targetRepository.save(
            TargetBrief.create(
                ownerId = ownerId,
                recruitDirection = RecruitDirection("백엔드 신입을 찾고 있어요."),
                company = CompanyName("토스"),
                job = null,
            ),
        )
        val failed = GenerationJob.create(
            ownerId = ownerId,
            kind = ArtifactKind.RESUME,
            experienceIds = listOf(UUID.randomUUID()),
            targetId = target.id.value,
            templateId = null,
            targetCompany = "토스",
            createdAt = Instant.now(),
        ).apply {
            markFailed(GenerationErrorCode.AI_UNAVAILABLE, "지금은 AI 생성을 사용할 수 없어요.", Instant.now())
        }
        jobRepository.save(failed)

        // when — 두 스레드가 배리어로 정렬해 같은 실패 작업을 동시에 재시도한다.
        val barrier = CyclicBarrier(2)
        val pool = Executors.newFixedThreadPool(2)
        val outcomes = try {
            val futures = (1..2).map {
                pool.submit(
                    Callable {
                        barrier.await(5, TimeUnit.SECONDS)
                        runCatching { service.retryInPlace(ownerId, failed.id) }
                    },
                )
            }
            futures.map { it.get(15, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        // then — 정확히 하나는 성공(새 PENDING), 다른 하나는 먼저 커밋한 쪽이 행을 지워 404(이미 다시 만드는 중).
        val successes: List<GenerationJobResponse> = outcomes.mapNotNull { it.getOrNull() }
        val failures: List<Throwable> = outcomes.mapNotNull { it.exceptionOrNull() }
        assertThat(successes).hasSize(1)
        assertThat(failures).hasSize(1)
        assertThat(failures.single()).isInstanceOf(ResourceNotFoundException::class.java)
        assertThat(successes.single().status).isEqualTo(GenerationJobStatus.PENDING)

        // 이중 생성 없음(핵심 불변식): 소유자 작업은 새 PENDING 하나뿐이고, 원본 실패 작업은 교체되어 사라졌다.
        val remaining = jobRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId)
        assertThat(remaining).hasSize(1)
        assertThat(remaining.single().status).isEqualTo(GenerationJobStatus.PENDING)
        assertThat(remaining.single().id).isNotEqualTo(failed.id)
        assertThat(remaining.single().id.value.toString()).isEqualTo(successes.single().jobId)
    }
}
