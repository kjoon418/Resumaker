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

    /**
     * 작성 전략 추출을 다시 대기열에 올린다(상태 PENDING으로 리셋). 추출 실패(FAILED)나 사용자가 직접 재시도할 때
     * 쓴다. 소유 격리(findOwnedOrThrow)로 본인 목표만 리셋한다. 멱등 — 이미 PENDING/진행 중이어도 허용한다
     * (PENDING으로 되돌리면 진행 중이던 추출은 결과 쓰기 0행으로 폐기되고 다음 틱이 재추출한다).
     */
    @Transactional
    fun retryStrategy(ownerId: UserId, id: TargetBriefId) {
        val brief = findOwnedOrThrow(ownerId, id)
        brief.resetStrategyPending()
    }

    private fun findOwnedOrThrow(ownerId: UserId, id: TargetBriefId): TargetBrief =
        repository.findByIdAndOwnerId(id, ownerId)
            ?: throw ResourceNotFoundException("요청하신 목표 정보를 찾을 수 없어요.")
}
