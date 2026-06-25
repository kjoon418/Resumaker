package watson.resumaker.experience.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.resumaker.account.domain.UserId
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.experience.domain.ExperienceRecord
import watson.resumaker.experience.domain.ExperienceRecordId
import watson.resumaker.experience.infrastructure.ExperienceRecordRepository
import watson.resumaker.experience.presentation.ExperienceResponse
import watson.resumaker.experience.presentation.ExperienceReviewResponse

/**
 * 경험 기록 CRUD 유스케이스. 모든 조회/수정/삭제는 ownerId 조건 + 결과 소유자 재검증으로 소유 격리를 강제한다(구현 설계 §4).
 * 응답의 boostHintCount는 [reviewService]의 결정적 점검(무LLM)으로 계산한다(목록 배지·상세 힌트용).
 */
@Service
class ExperienceService(
    private val repository: ExperienceRecordRepository,
    private val mapper: ExperienceServiceMapper,
    private val reviewService: ExperienceReviewService,
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
        return toResponse(saved)
    }

    @Transactional(readOnly = true)
    fun getOne(ownerId: UserId, id: ExperienceRecordId): ExperienceResponse {
        val record = findOwnedOrThrow(ownerId, id)
        return toResponse(record)
    }

    @Transactional(readOnly = true)
    fun getAll(ownerId: UserId): List<ExperienceResponse> =
        repository.findAllByOwnerId(ownerId).map { toResponse(it) }

    /** 경험 점검(보강 유도) — 소유 격리 진단을 응답 DTO로 변환한다. */
    @Transactional(readOnly = true)
    fun review(ownerId: UserId, id: ExperienceRecordId): ExperienceReviewResponse =
        mapper.toReviewResponse(reviewService.review(ownerId, id))

    @Transactional
    fun update(ownerId: UserId, id: ExperienceRecordId, command: UpdateExperienceCommand): ExperienceResponse {
        val record = findOwnedOrThrow(ownerId, id)
        record.update(command.title, command.type, command.body, command.detail)
        return toResponse(record)
    }

    @Transactional
    fun delete(ownerId: UserId, id: ExperienceRecordId) {
        val record = findOwnedOrThrow(ownerId, id)
        repository.delete(record)
    }

    private fun findOwnedOrThrow(ownerId: UserId, id: ExperienceRecordId): ExperienceRecord =
        repository.findByIdAndOwnerId(id, ownerId)
            ?: throw ResourceNotFoundException("요청하신 경험 기록을 찾을 수 없어요.")

    /** 응답 변환 + 결정적 점검으로 boostHintCount를 함께 채운다(무LLM·CPU만이라 목록 경로 비용 미미). */
    private fun toResponse(record: ExperienceRecord): ExperienceResponse =
        mapper.toResponse(record, reviewService.review(record).boostHintCount)
}
