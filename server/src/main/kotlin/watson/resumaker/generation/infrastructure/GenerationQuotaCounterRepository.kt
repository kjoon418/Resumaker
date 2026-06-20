package watson.resumaker.generation.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.util.UUID

/**
 * 비용 가드레일 날짜-키 카운터 영속성([GenerationQuotaCounter]). 순수 DB 로직만 담당한다(검증 가이드).
 *
 * 점검은 [findCountByScopeKeyAndQuotaDate]로 당일 사용량을 읽고, 차감은 [increment](원자 UPDATE) →
 * (행 없음 → 0행 갱신) 최초 사용 행 삽입의 2단계로 동시성 안전하게 처리한다.
 */
interface GenerationQuotaCounterRepository : JpaRepository<GenerationQuotaCounter, UUID> {

    /**
     * 해당 scopeKey의 당일 사용량을 읽는다(행이 없으면 null = 0회). 점검(차감 전 빠른 실패)에 쓴다.
     * 새 날짜는 행이 없어 자연히 0이므로 별도 리셋 로직이 필요 없다.
     */
    @Query(
        "select c.count from GenerationQuotaCounter c " +
            "where c.scopeKey = :scopeKey and c.quotaDate = :quotaDate",
    )
    fun findCountByScopeKeyAndQuotaDate(
        @Param("scopeKey") scopeKey: String,
        @Param("quotaDate") quotaDate: LocalDate,
    ): Int?

    /**
     * 당일 카운터를 원자적으로 1 증가시킨다. 갱신된 행 수를 돌려준다(행이 없으면 0 → 호출자가 최초 사용 행을 삽입).
     * DB가 `count = count + 1`을 단일 문으로 처리하므로 같은 행에 대한 동시 증가도 분실 없이 합산된다.
     *
     * `flushAutomatically = true`: UPDATE 실행 **전에** 영속성 컨텍스트를 flush한다. 이 increment는 1차 생성·
     * 재생성 유스케이스에서 **같은 TX 안에 아직 flush되지 않은 산출물(Artifact/Version) 영속 직후** 호출되므로,
     * flush를 강제하지 않으면 뒤따르는 `clearAutomatically`가 그 보류 중인 INSERT를 폐기해 산출물이 저장되지
     * 않는다(가드레일 도입 시 회귀). flush를 먼저 해 보류 변경을 DB에 반영한 뒤 clear한다.
     *
     * `clearAutomatically = true`: UPDATE 후 영속성 컨텍스트를 자동 clear해 같은 TX 안에서 이 행을 재read할 때
     * stale 캐시를 보지 않게 방어한다(현재 구현은 UPDATE 후 같은 TX에서 이 행을 재read하지 않지만, 호출 패턴이
     * 바뀌어도 stale read가 발생하지 않도록 방어적으로 설정한다).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "update GenerationQuotaCounter c set c.count = c.count + 1 " +
            "where c.scopeKey = :scopeKey and c.quotaDate = :quotaDate",
    )
    fun increment(
        @Param("scopeKey") scopeKey: String,
        @Param("quotaDate") quotaDate: LocalDate,
    ): Int
}
