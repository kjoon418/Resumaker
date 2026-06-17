package watson.resumaker.generation.infrastructure

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import watson.resumaker.common.domain.IdentifierGenerator
import java.time.LocalDate
import java.util.UUID

/**
 * 비용 가드레일 사용량을 **날짜-키 카운터 행**으로 영속한다(도메인 이해 §396~397, 구현 설계 §7).
 *
 * **영속 모델 선택(트레이드오프):**
 * - (택1) **날짜-키 카운터 행**((scopeKey, quotaDate) → count): 새 날에는 행이 없어 자연히 0부터 시작하므로
 *   **별도 스케줄러/크론 없이 달력일 리셋이 자동**으로 이뤄진다. 점검·차감이 행 1개 조회/증가로 끝나 단순하고 빠르다.
 *   한계: 사용 이력(언제 무엇을 생성했는지) 감사 추적은 남지 않는다(이번 범위 밖).
 * - (대안) 이벤트 로그(생성마다 1행 적재 후 당일 COUNT): 감사 추적·세밀 분석엔 유리하나, 점검마다 집계 쿼리가
 *   필요하고 행이 무한 누적돼 정리 배치가 별도로 든다. MVP에는 과하다.
 * **선택: 날짜-키 카운터.** 근거 — 리셋이 스케줄러 없이 자동이고(자정 경계 = 새 날짜 키), 도메인이 요구하는 것은
 * "남은 횟수 점검·차단"뿐이라 감사 로그 요구가 없다. 감사 필요가 생기면 이벤트 로그로 이 seam을 교체한다.
 *
 * **scopeKey 구성:** 1차 생성은 `"INITIAL:{ownerId}"`(사용자당), 항목 재생성은 `"REGEN:{sectionId}"`(생성 항목당,
 * §397 "항목당"). 두 카운트가 같은 테이블에 섞이지만 키 접두로 충돌하지 않는다.
 *
 * **TODO(운영):** 날짜-키 방식은 과거 행이 영구 잔존해 테이블이 단조 증가한다(사용자수 × 날수로 누적).
 * MVP 범위에서 즉각 문제가 되지 않으나, 운영 규모가 커지면 `quotaDate < today - N` 조건으로 오래된 행을
 * 정리하는 배치/스케줄러를 추가해야 한다(구현은 이번 범위 밖).
 *
 * **동시성:** 같은 사용자/항목의 동시 생성으로 같은 (scopeKey, quotaDate) 행에 경합이 생길 수 있다.
 * 차감은 [GenerationQuotaCounterRepository.increment]의 **DB 원자 증가(UPDATE ... count = count + 1)**로 수행하고,
 * 행이 없을 때의 최초 삽입은 (scopeKey, quotaDate) **유니크 제약**으로 직렬화한다(동시 삽입 시 한쪽만 성공, 다른 쪽은
 * 제약 위반 → 재시도해 원자 증가로 합류). 이로써 카운터 분실 없이 안전하게 합산된다.
 */
@Entity
@Table(
    name = "generation_quota_counters",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_quota_scope_date", columnNames = ["scope_key", "quota_date"]),
    ],
)
class GenerationQuotaCounter private constructor(
    @Id
    @Column(name = "id")
    val id: UUID,
    @Column(name = "scope_key", nullable = false)
    val scopeKey: String,
    @Column(name = "quota_date", nullable = false)
    val quotaDate: LocalDate,
    @Column(name = "count", nullable = false)
    var count: Int,
) {

    companion object {
        /** 최초 1회 사용 행을 만든다(count = 1). 이후 증가는 레포지토리 원자 UPDATE로 한다. */
        fun firstUse(scopeKey: String, quotaDate: LocalDate): GenerationQuotaCounter =
            GenerationQuotaCounter(
                id = IdentifierGenerator.newId(),
                scopeKey = scopeKey,
                quotaDate = quotaDate,
                count = 1,
            )
    }
}
