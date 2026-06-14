package watson.resumaker.target.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.resumaker.account.domain.UserId
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.target.domain.TargetBrief
import watson.resumaker.target.domain.TargetBriefId
import watson.resumaker.target.infrastructure.TargetBriefRepository
import watson.resumaker.target.presentation.TargetResponse

/**
 * 목표 정보 CRUD + 저장·재사용 유스케이스. 모든 조회/수정/삭제는 ownerId 조건 + 결과 소유자 재검증으로 소유 격리를 강제한다(구현 설계 §4).
 */
@Service
class TargetService(
    private val repository: TargetBriefRepository,
    private val mapper: TargetServiceMapper,
) {

    @Transactional
    fun create(ownerId: UserId, command: CreateTargetCommand): TargetResponse {
        val brief = TargetBrief.create(
            ownerId = ownerId,
            recruitDirection = command.recruitDirection,
            company = command.company,
            job = command.job,
        )
        val saved = repository.save(brief)
        return mapper.toResponse(saved)
    }

    @Transactional(readOnly = true)
    fun getOne(ownerId: UserId, id: TargetBriefId): TargetResponse {
        val brief = findOwnedOrThrow(ownerId, id)
        return mapper.toResponse(brief)
    }

    @Transactional(readOnly = true)
    fun getAll(ownerId: UserId): List<TargetResponse> =
        mapper.toResponses(repository.findAllByOwnerId(ownerId))

    @Transactional
    fun update(ownerId: UserId, id: TargetBriefId, command: UpdateTargetCommand): TargetResponse {
        val brief = findOwnedOrThrow(ownerId, id)
        brief.update(command.recruitDirection, command.company, command.job)
        return mapper.toResponse(brief)
    }

    @Transactional
    fun delete(ownerId: UserId, id: TargetBriefId) {
        val brief = findOwnedOrThrow(ownerId, id)
        repository.delete(brief)
    }

    private fun findOwnedOrThrow(ownerId: UserId, id: TargetBriefId): TargetBrief =
        repository.findByIdAndOwnerId(id, ownerId)
            ?: throw ResourceNotFoundException("요청하신 목표 정보를 찾을 수 없어요.")
}
