package watson.resumaker.experience.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import watson.resumaker.account.domain.UserId
import watson.resumaker.experience.domain.ExperienceBody
import watson.resumaker.experience.domain.ExperienceDetail
import watson.resumaker.experience.domain.ExperienceRecord
import watson.resumaker.experience.domain.ExperienceTitle
import watson.resumaker.experience.domain.ExperienceType
import watson.resumaker.experience.domain.Period
import watson.resumaker.experience.domain.SkillTag
import java.time.LocalDate
import java.util.UUID

@DataJpaTest
class ExperienceRecordRepositoryTest {

    @Autowired
    private lateinit var repository: ExperienceRecordRepository

    private val ownerId = UserId(UUID.randomUUID())
    private val otherOwnerId = UserId(UUID.randomUUID())

    @Test
    fun 경험_기록을_저장하고_선택값까지_그대로_복원한다() {
        // given
        val detail = ExperienceDetail.of(
            situation = "느린 결제",
            action = "캐싱 도입",
            result = "응답 속도 40% 개선",
            period = Period.of(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 1)),
            skillTags = listOf(SkillTag("Kotlin"), SkillTag("Redis")),
        )

        val record = ExperienceRecord.create(
            ownerId = ownerId,
            title = ExperienceTitle("결제 시스템 개편"),
            type = ExperienceType.PROJECT,
            body = ExperienceBody("캐싱 전략을 도입했다."),
            detail = detail,
        )

        // when
        val saved = repository.saveAndFlush(record)
        val found = repository.findByIdAndOwnerId(saved.id, ownerId)

        // then
        assertThat(found).isNotNull
        assertThat(found!!.title.value).isEqualTo("결제 시스템 개편")
        assertThat(found.type).isEqualTo(ExperienceType.PROJECT)
        assertThat(found.body.value).isEqualTo("캐싱 전략을 도입했다.")
        assertThat(found.detail.result).isEqualTo("응답 속도 40% 개선")
        assertThat(found.detail.period?.start).isEqualTo(LocalDate.of(2024, 1, 1))
        assertThat(found.detail.skillTags.map { it.value }).containsExactlyInAnyOrder("Kotlin", "Redis")
    }

    @Test
    fun 다른_사용자의_경험_기록은_조회되지_않는다() {
        // given
        val record = ExperienceRecord.create(
            ownerId = ownerId,
            title = ExperienceTitle("내 경험"),
            type = ExperienceType.JOB,
            body = ExperienceBody("내가 한 일"),
            detail = ExperienceDetail.EMPTY,
        )
        val saved = repository.saveAndFlush(record)

        // when
        val foundByOther = repository.findByIdAndOwnerId(saved.id, otherOwnerId)

        // then
        assertThat(foundByOther).isNull()
    }

    @Test
    fun 소유자별_목록_조회는_본인_데이터만_반환한다() {
        // given
        repository.saveAndFlush(
            ExperienceRecord.create(ownerId, ExperienceTitle("A"), ExperienceType.PROJECT, ExperienceBody("a"), ExperienceDetail.EMPTY),
        )
        repository.saveAndFlush(
            ExperienceRecord.create(ownerId, ExperienceTitle("B"), ExperienceType.JOB, ExperienceBody("b"), ExperienceDetail.EMPTY),
        )
        repository.saveAndFlush(
            ExperienceRecord.create(otherOwnerId, ExperienceTitle("C"), ExperienceType.AWARD, ExperienceBody("c"), ExperienceDetail.EMPTY),
        )

        // when
        val mine = repository.findAllByOwnerId(ownerId)

        // then
        assertThat(mine).hasSize(2)
        assertThat(mine.map { it.title.value }).containsExactlyInAnyOrder("A", "B")
    }

    @Test
    fun 소유자_기준_삭제는_본인_데이터만_지운다() {
        // given
        repository.saveAndFlush(
            ExperienceRecord.create(ownerId, ExperienceTitle("A"), ExperienceType.PROJECT, ExperienceBody("a"), ExperienceDetail.EMPTY),
        )
        repository.saveAndFlush(
            ExperienceRecord.create(otherOwnerId, ExperienceTitle("C"), ExperienceType.AWARD, ExperienceBody("c"), ExperienceDetail.EMPTY),
        )

        // when
        repository.deleteByOwnerId(ownerId)
        repository.flush()

        // then
        assertThat(repository.findAllByOwnerId(ownerId)).isEmpty()
        assertThat(repository.findAllByOwnerId(otherOwnerId)).hasSize(1)
    }
}
