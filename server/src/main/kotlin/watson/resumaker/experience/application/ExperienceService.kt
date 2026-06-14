package watson.resumaker.experience.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.resumaker.account.domain.UserId
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.experience.domain.ExperienceRecord
import watson.resumaker.experience.domain.ExperienceRecordId
import watson.resumaker.experience.infrastructure.ExperienceRecordRepository
import watson.resumaker.experience.presentation.ExperienceResponse

/**
 * 경험 기록 CRUD 유스케이스. 모든 조회/수정/삭제는 ownerId 조건 + 결과 소유자 재검증으로 소유 격리를 강제한다(구현 설계 §4).
 */
@Service
class ExperienceService(
    private val repository: ExperienceRecordRepository,
    private val mapper: ExperienceServiceMapper,
) {

    @Transactional
    fun create(ownerId: UserId, command: CreateExperienceCommand): ExperienceResponse {
        val record = ExperienceRecord.create(
            ownerId = ownerId,
            title = command.title,
            type = command.type,
            body = command.body,
            detail = command.detail,
        )
        val saved = repository.save(record)
        return mapper.toResponse(saved)
    }

    @Transactional(readOnly = true)
    fun getOne(ownerId: UserId, id: ExperienceRecordId): ExperienceResponse {
        val record = findOwnedOrThrow(ownerId, id)
        return mapper.toResponse(record)
    }

    @Transactional(readOnly = true)
    fun getAll(ownerId: UserId): List<ExperienceResponse> =
        mapper.toResponses(repository.findAllByOwnerId(ownerId))

    @Transactional
    fun update(ownerId: UserId, id: ExperienceRecordId, command: UpdateExperienceCommand): ExperienceResponse {
        val record = findOwnedOrThrow(ownerId, id)
        record.update(command.title, command.type, command.body, command.detail)
        return mapper.toResponse(record)
    }

    @Transactional
    fun delete(ownerId: UserId, id: ExperienceRecordId) {
        val record = findOwnedOrThrow(ownerId, id)
        repository.delete(record)
    }

    private fun findOwnedOrThrow(ownerId: UserId, id: ExperienceRecordId): ExperienceRecord =
        repository.findByIdAndOwnerId(id, ownerId)
            ?: throw ResourceNotFoundException("요청하신 경험 기록을 찾을 수 없어요.")
}
