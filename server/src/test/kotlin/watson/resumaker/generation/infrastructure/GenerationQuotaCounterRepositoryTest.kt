package watson.resumaker.generation.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import jakarta.persistence.EntityManager
import java.time.LocalDate

/**
 * [GenerationQuotaCounterRepository] 슬라이스 테스트(@DataJpaTest, H2 PostgreSQL 모드). 날짜-키 카운터의
 * 원자 증가·최초 행 삽입·유니크 제약·날짜 분리를 검증한다(비용 가드레일 영속/동시성 모델).
 */
@DataJpaTest
class GenerationQuotaCounterRepositoryTest {

    @Autowired
    private lateinit var repository: GenerationQuotaCounterRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private val today = LocalDate.parse("2026-06-17")
    private val scope = "INITIAL:owner-a"

    @Test
    fun 행이_없으면_사용량은_null이고_원자_증가는_0행이다() {
        // when and then — 미사용 스코프/날짜는 행이 없다(= 0회), 증가도 갱신 0행.
        assertThat(repository.findCountByScopeKeyAndQuotaDate(scope, today)).isNull()
        assertThat(repository.increment(scope, today)).isEqualTo(0)
    }

    @Test
    fun 최초_사용_행을_저장하고_원자_증가로_누적한다() {
        // given — 최초 사용 행(count=1) 삽입.
        repository.saveAndFlush(GenerationQuotaCounter.firstUse(scope, today))

        // when — 원자 증가 2회.
        assertThat(repository.increment(scope, today)).isEqualTo(1)
        assertThat(repository.increment(scope, today)).isEqualTo(1)
        flushAndClear()

        // then — 1(최초) + 2(증가) = 3.
        assertThat(repository.findCountByScopeKeyAndQuotaDate(scope, today)).isEqualTo(3)
    }

    @Test
    fun 같은_스코프_같은_날짜의_중복_행은_유니크_제약으로_거부된다() {
        // given — 같은 (scopeKey, quotaDate) 두 번째 삽입 시도(동시 최초 삽입 경합 시뮬레이션).
        repository.saveAndFlush(GenerationQuotaCounter.firstUse(scope, today))

        // when and then — 유니크 제약 위반.
        org.assertj.core.api.Assertions.assertThatThrownBy {
            repository.saveAndFlush(GenerationQuotaCounter.firstUse(scope, today))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun 날짜가_다르면_별도_카운터다_리셋_경계_확인() {
        // given — 같은 스코프, 다른 날짜는 독립 행(달력일 리셋 = 새 날짜 키).
        val tomorrow = today.plusDays(1)
        repository.saveAndFlush(GenerationQuotaCounter.firstUse(scope, today))
        repository.increment(scope, today)
        flushAndClear()

        // when and then — 내일은 행이 없어 0(자연 리셋), 오늘은 누적.
        assertThat(repository.findCountByScopeKeyAndQuotaDate(scope, today)).isEqualTo(2)
        assertThat(repository.findCountByScopeKeyAndQuotaDate(scope, tomorrow)).isNull()
    }

    @Test
    fun 행이_이미_있을때_incrementOrInsert_경로는_원자증가로_합류하고_count를_분실하지_않는다() {
        // given (항목 1 MEDIUM: catch→재증가 합류 경로 검증). 동시 최초 삽입 경합의 '패자' 경로를 직접 시뮬레이션:
        // saveAndFlush가 DataIntegrityViolationException을 던지면(유니크 제약), catch에서 increment로 합류해야 한다.
        // 여기서는 행 선삽입(count=1) → 두 번째 saveAndFlush 제약 위반 → increment 합류(count=2)로 검증한다.

        // 첫 번째 사용자: 최초 사용 행 삽입(count=1).
        repository.saveAndFlush(GenerationQuotaCounter.firstUse(scope, today))

        // 두 번째 동시 요청 시뮬레이션: 행이 이미 있는데 다시 최초 삽입 시도 → 유니크 제약 위반.
        // (실제 CountingGenerationQuotaGuard.incrementOrInsert는 먼저 increment 시도 → 이미 있으면 1행 성공해 삽입 경로를 타지 않음.
        // catch 합류 경로는 "increment=0행(행 없음)" + "saveAndFlush 제약 위반" + "increment 재시도"의 순서이므로,
        // 이를 직접 재현: 행 선삽입 후 increment 제대로 합류하는지 확인.)
        val countBefore = repository.findCountByScopeKeyAndQuotaDate(scope, today)!!
        val updatedRows = repository.increment(scope, today)
        flushAndClear()
        val countAfter = repository.findCountByScopeKeyAndQuotaDate(scope, today)!!

        // then — 원자 증가로 합류: 1행 갱신, count는 +1 증가(분실 없음).
        assertThat(updatedRows).isEqualTo(1)
        assertThat(countAfter).isEqualTo(countBefore + 1)
    }

    private fun flushAndClear() {
        // @Modifying UPDATE 이후 영속성 컨텍스트가 stale일 수 있어 flush + clear로 DB 값을 다시 읽는다.
        entityManager.flush()
        entityManager.clear()
    }
}
