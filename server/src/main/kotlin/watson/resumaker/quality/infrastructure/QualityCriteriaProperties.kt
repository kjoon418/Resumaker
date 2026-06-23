package watson.resumaker.quality.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 개선 기준 사전·임계값 외부 설정(품질 개선 기획 §5.2-2 "반자동(S) 기준의 구체 사전·임계값은 잠정 시드, 설정 외부화
 * 권장, 오너 큐레이션 시 교체"). [watson.resumaker.generation.infrastructure.GenerationQuotaProperties]와 동일 패턴.
 *
 * 검사 **로직**(어떤 기준을 어떻게 점검하는지)은 코드 고정이고, 여기서는 **사전·임계 수치만** 둔다(오너 큐레이션 대상).
 * 기본값은 MVP 잠정 시드다(취업 준비생 이력서 맥락에서 흔한 약한 동사·버즈워드·규모어 — 기획 §1·§2 안티패턴 카탈로그).
 *
 * - [weakVerbs]: 약한 동사 사전(I1). 책임 나열형("담당했다") 표현. 매칭되면 STRONG_VERB 소견.
 * - [buzzwords]: 버즈워드 사전(C2). 막연한 형용사·자기소개 상투구. 매칭되면 BUZZWORD 소견.
 * - [vagueMetricTerms]: 모호 수치·규모어 사전(I4). "대용량", "200% 증가" 류 추상 규모어. 매칭되면 VAGUE_METRIC 소견.
 * - [passiveSuffixes]: 수동태 종결 패턴(I2). 매칭되면 ACTIVE_VOICE 소견.
 * - [maxSectionLength]: 항목 길이 상한(C1). 초과하면 LENGTH 소견(자동 — 결정적).
 * - [duplicationShingleSize]·[duplicationThreshold]: 중복 판정(C3). n-그램(글자) 자카드 유사도가 임계 이상이면
 *   두 항목이 겹친다고 본다.
 */
@ConfigurationProperties(prefix = "resumaker.quality-criteria")
data class QualityCriteriaProperties(
    val weakVerbs: List<String> = DEFAULT_WEAK_VERBS,
    val buzzwords: List<String> = DEFAULT_BUZZWORDS,
    val vagueMetricTerms: List<String> = DEFAULT_VAGUE_METRIC_TERMS,
    val passiveSuffixes: List<String> = DEFAULT_PASSIVE_SUFFIXES,
    val maxSectionLength: Int = 600,
    val duplicationShingleSize: Int = 6,
    val duplicationThreshold: Double = 0.5,
) {
    companion object {
        /** 약한 동사 시드(I1·AP1·AP9). 책임 나열형 — 행동·성취 동사로 바꿀 후보. */
        private val DEFAULT_WEAK_VERBS = listOf(
            "담당했다", "담당하였다", "참여했다", "참여하였다", "수행했다", "관여했다", "있었다",
            "맡았다", "진행했다", "도왔다", "지원했다",
        )

        /** 버즈워드 시드(C2·AP2·AP4). 막연한 형용사·자기소개 상투구. */
        private val DEFAULT_BUZZWORDS = listOf(
            "열정적", "책임감", "성실한", "성실하게", "다양한 경험", "끊임없이", "적극적인",
            "원활한", "소통", "협업을 즐기", "성장하는", "꼼꼼한",
        )

        /** 모호 수치·규모어 시드(I4·AP3·AP5). 추상 규모어 — 객관 수치로 구체화 또는 보강 안내 분기. */
        private val DEFAULT_VAGUE_METRIC_TERMS = listOf(
            "대용량", "대규모", "다수의", "수많은", "상당한", "획기적", "비약적", "크게 향상", "대폭",
            "중요한 역할", "복잡한",
        )

        /** 수동태 종결 패턴 시드(I2·AP13). "~되었다/~되어/~지다" 류. */
        private val DEFAULT_PASSIVE_SUFFIXES = listOf(
            "되었다", "되었습니다", "되어", "되었고", "지게 되었다", "되었으며",
        )
    }
}
