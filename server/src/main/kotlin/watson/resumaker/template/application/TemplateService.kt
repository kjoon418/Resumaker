package watson.resumaker.template.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.resumaker.account.domain.UserId
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.template.domain.ResumeTemplate
import watson.resumaker.template.domain.ResumeTemplateId
import watson.resumaker.template.infrastructure.ResumeTemplateRepository
import watson.resumaker.template.presentation.TemplateResponse

/**
 * 이력서 양식 CRUD + 저장·재사용 유스케이스(FU-A). 모든 조회/수정/삭제는 ownerId 조건 + 결과 소유자
 * 재검증으로 소유 격리를 강제한다(구현 설계 §4, 도메인 이해 §2.5 "목표 정보와 동일한 저장·삭제 정책").
 */
@Service
class TemplateService(
    private val repository: ResumeTemplateRepository,
    private val mapper: TemplateServiceMapper,
) {

    @Transactional
    fun create(ownerId: UserId, command: CreateTemplateCommand): TemplateResponse {
        val template = ResumeTemplate.create(
            ownerId = ownerId,
            name = command.name,
            sections = command.sections,
        )
        val saved = repository.save(template)
        return mapper.toResponse(saved)
    }

    @Transactional(readOnly = true)
    fun getOne(ownerId: UserId, id: ResumeTemplateId): TemplateResponse {
        val template = findOwnedOrThrow(ownerId, id)
        return mapper.toResponse(template)
    }

    @Transactional(readOnly = true)
    fun getAll(ownerId: UserId): List<TemplateResponse> =
        mapper.toResponses(repository.findAllByOwnerId(ownerId))

    @Transactional
    fun update(ownerId: UserId, id: ResumeTemplateId, command: UpdateTemplateCommand): TemplateResponse {
        val template = findOwnedOrThrow(ownerId, id)
        template.update(command.name, command.sections)
        return mapper.toResponse(template)
    }

    @Transactional
    fun delete(ownerId: UserId, id: ResumeTemplateId) {
        val template = findOwnedOrThrow(ownerId, id)
        repository.delete(template)
    }

    private fun findOwnedOrThrow(ownerId: UserId, id: ResumeTemplateId): ResumeTemplate =
        repository.findByIdAndOwnerId(id, ownerId)
            ?: throw ResourceNotFoundException("요청하신 이력서 양식을 찾을 수 없어요.")
}
