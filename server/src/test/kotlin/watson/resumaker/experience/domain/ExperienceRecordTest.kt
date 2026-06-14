package watson.resumaker.experience.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import watson.resumaker.account.domain.UserId
import java.util.UUID

class ExperienceRecordTest {

    @Test
    fun 신규_생성_시_식별자를_발급하고_소유자를_가진다() {
        // given
        val ownerId = UserId(UUID.randomUUID())

        // when
        val record = ExperienceRecord.create(
            ownerId = ownerId,
            title = ExperienceTitle("결제 시스템 개편"),
            type = ExperienceType.PROJECT,
            body = ExperienceBody("캐싱을 도입했다."),
            detail = ExperienceDetail.EMPTY,
        )

        // then
        assertThat(record.id.value).isNotNull()
        assertThat(record.ownerId).isEqualTo(ownerId)
    }

    @Test
    fun 선택값이_비어도_생성에_성공한다() {
        // when
        val record = ExperienceRecord.create(
            ownerId = UserId(UUID.randomUUID()),
            title = ExperienceTitle("학습 기록"),
            type = ExperienceType.LEARNING,
            body = ExperienceBody("코틀린을 학습했다."),
            detail = ExperienceDetail.EMPTY,
        )

        // then
        assertThat(record.detail.skillTags).isEmpty()
        assertThat(record.detail.period).isNull()
    }

    @Test
    fun 수정하면_제목_유형_본문_선택값이_갱신된다() {
        // given
        val record = ExperienceRecord.create(
            ownerId = UserId(UUID.randomUUID()),
            title = ExperienceTitle("기존 제목"),
            type = ExperienceType.PROJECT,
            body = ExperienceBody("기존 본문"),
            detail = ExperienceDetail.EMPTY,
        )

        val updatedDetail = ExperienceDetail.of(
            situation = "상황",
            action = "행동",
            result = "결과",
            period = null,
            skillTags = listOf(SkillTag("Kotlin")),
        )

        // when
        record.update(
            title = ExperienceTitle("새 제목"),
            type = ExperienceType.JOB,
            body = ExperienceBody("새 본문"),
            detail = updatedDetail,
        )

        // then
        assertThat(record.title.value).isEqualTo("새 제목")
        assertThat(record.type).isEqualTo(ExperienceType.JOB)
        assertThat(record.body.value).isEqualTo("새 본문")
        assertThat(record.detail.skillTags.map { it.value }).containsExactly("Kotlin")
    }
}
