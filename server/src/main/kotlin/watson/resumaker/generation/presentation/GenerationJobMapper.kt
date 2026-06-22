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
    )
}
