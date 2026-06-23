package watson.resumaker.generation.infrastructure

/**
 * 신뢰성 가드레일 프롬프트 조각·출력 스키마의 **공유 진실의 원천**(도메인 이해 §407~419·§382).
 *
 * 1차 생성([ClaudeCliArtifactGenerationAdapter])과 품질 개선의 처치 어댑터
 * ([watson.resumaker.quality.infrastructure.ClaudeCliQualityImprovementAdapter]) **둘 다** 같은
 * "없는 사실 금지 + 근거 함께 산출(factGroundings)" 규칙을 짊어져야 한다(품질 개선 기획 §3.5·§3.8 — 다듬기도 LLM이
 * 수행하므로 수치·고유명사를 끼워넣을 구조적 위험이 동일). 그래서 절대 규칙 블록과 OUTPUT_SCHEMA를 여기로 모아
 * 두 어댑터가 공유한다(한쪽만 바뀌어 가드레일에 구멍이 나는 것을 방지).
 */
object GroundingPromptParts {

    /**
     * 신뢰성 가드레일 절대 규칙 블록(없는 사실 날조 금지 + sourceExperienceIds·factGroundings 산출 명령).
     * 프롬프트의 "## 절대 규칙(신뢰성 가드레일 — 위반 금지)" 섹션을 그대로 펼친다.
     */
    fun appendAbsoluteRules(sb: StringBuilder) {
        sb.appendLine("## 절대 규칙(신뢰성 가드레일 — 위반 금지)")
        sb.appendLine("- 아래 '경험 기록'에 문자적 근거가 있는 사실만 사용하세요.")
        sb.appendLine("- 사용자가 적지 않은 수치(%, ms, 건수 등), 고유명사(회사명·기술명·프로젝트명), 성과를 절대 지어내지 마세요.")
        sb.appendLine("- 표현을 더 설득력 있게 다듬는 것은 허용하지만, 없는 사실을 추가하는 것은 금지입니다.")
        sb.appendLine("- 각 항목마다 근거가 된 경험의 id 목록(sourceExperienceIds)을 함께 제시하세요.")
        sb.appendLine("- 산출물에 등장한 수치/고유명사 각각에 대해 factGroundings로 근거(token, kind, sourceExperienceId, evidenceText)를 제시하세요. evidenceText는 경험 기록 본문에 실제로 등장한 문자열이어야 합니다.")
    }

    /**
     * 출력 구조를 고정하는 JSON Schema(`claude --json-schema`). 항목 단위 부분 실패는 succeeded로 표현한다.
     * 1차 생성·품질 개선 처치가 같은 항목 출력 형태(definitionKey/sectionKind/content/succeeded/근거)를 쓴다.
     */
    val OUTPUT_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "sections": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "definitionKey": { "type": "string" },
                  "sectionKind": { "type": "string", "enum": ["SUMMARY", "CAREER", "EXPERIENCE_NARRATIVE"] },
                  "content": { "type": "string" },
                  "succeeded": { "type": "boolean" },
                  "sourceExperienceIds": { "type": "array", "items": { "type": "string" } },
                  "factGroundings": {
                    "type": "array",
                    "items": {
                      "type": "object",
                      "properties": {
                        "token": { "type": "string" },
                        "kind": { "type": "string", "enum": ["NUMERIC", "PROPER_NOUN"] },
                        "sourceExperienceId": { "type": "string" },
                        "evidenceText": { "type": "string" }
                      },
                      "required": ["token", "kind", "sourceExperienceId", "evidenceText"]
                    }
                  }
                },
                "required": ["definitionKey", "sectionKind", "content", "succeeded", "sourceExperienceIds", "factGroundings"]
              }
            }
          },
          "required": ["sections"]
        }
    """.trimIndent()
}
