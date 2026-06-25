package watson.resumaker.generation.presentation

import org.springframework.stereotype.Component
import watson.resumaker.generation.domain.GenerationJob

/**
 * 생성 작업 응답 Service Mapper(구현 설계 §8 "Response DTO 변환은 Service Mapper"). 엔티티의 식별자·시각을
 * 문자열로 바꿔 응답 DTO로 변환한다. 작업은 지연 로딩 컬렉션을 응답에 싣지 않으므로 트랜잭션 경계에 민감하지 않다.
 */
@Component
class GenerationJobMapper {

    fun toResponse(job: GenerationJob): GenerationJobResponse = GenerationJobResponse(
        jobId = job.id.value.toString(),
        kind = job.kind,
        status = job.status,
        artifactId = job.artifactId?.toString(),
        errorCode = job.errorCode,
        errorMessage = job.errorMessage,
        targetCompany = job.targetCompany,
        createdAt = job.createdAt.toString(),
        // '다시 만들기' 분류는 서버 단일 책임(클라이언트는 이 값만 보고 버튼 동작을 정한다).
        retryMode = job.retryMode(),
        // EDIT_INPUTS 재시도 시 제작 화면 프리필용 입력(모두 사용자 소유 데이터).
        experienceIds = job.experienceIds.map { it.toString() },
        targetId = job.targetId.toString(),
        templateId = job.templateId?.toString(),
    )
}
