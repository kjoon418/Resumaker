package watson.resumaker.target.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import watson.resumaker.account.domain.UserId
import watson.resumaker.target.domain.CompanyName
import watson.resumaker.target.domain.JobTitle
import watson.resumaker.target.domain.RecruitDirection
import watson.resumaker.target.domain.TargetBrief
import java.util.UUID

@DataJpaTest
class TargetBriefRepositoryTest {

    @Autowired
    private lateinit var repository: TargetBriefRepository

    private val ownerId = UserId(UUID.randomUUID())
    private val otherOwnerId = UserId(UUID.randomUUID())

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
}
