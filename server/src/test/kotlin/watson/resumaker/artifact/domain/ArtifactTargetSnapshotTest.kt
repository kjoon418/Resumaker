package watson.resumaker.artifact.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import watson.resumaker.target.domain.RecruitDirection

class ArtifactTargetSnapshotTest {

    @Test
    fun `채용공고 전문처럼 200자를 넘는 채용 방향도 스냅샷이 그대로 보존한다`() {
        // QA 2026-06-21 #1·#2 회귀: 스냅샷이 원본(5000자)보다 좁은 200자 한도를 따로 두어 생성이 막히던 버그.
        // 이제 길이 불변식은 RecruitDirection VO가 단일 소유하고, 스냅샷은 검증 없이 값을 복제만 한다.
        val longDirection = RecruitDirection("가".repeat(2000))

        val snapshot = ArtifactTargetSnapshot.of(longDirection, company = null, job = null)

        assertThat(snapshot.recruitDirection).hasSize(2000)
    }

    @Test
    fun `회사·직무가 없어도 스냅샷을 만들 수 있다`() {
        val snapshot = ArtifactTargetSnapshot.of(RecruitDirection("백엔드 신입"), company = null, job = null)

        assertThat(snapshot.recruitDirection).isEqualTo("백엔드 신입")
        assertThat(snapshot.company).isNull()
        assertThat(snapshot.job).isNull()
    }
}
