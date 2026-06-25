package watson.resumaker.generation.presentation

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import watson.resumaker.account.application.CurrentUserProvider
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.generation.application.GenerationJobService
import watson.resumaker.generation.domain.GenerationJobId
import java.util.UUID

/**
 * 비동기 생성 작업 API.
 * - GET    /generation-jobs            : 내 생성 작업 목록(최신순). 폴링·작업 카드 표시용.
 * - GET    /generation-jobs/{id}       : 단건 조회(상태·결과 폴링). 완료 시 artifactId로 산출물 열람.
 * - POST   /generation-jobs/{id}/retry : 일시적 실패 작업을 저장된 입력 그대로 다시 만든다(202). 실패 작업을
 *   새 PENDING으로 교체한다. IN_PLACE가 아닌 작업은 409, 한도 초과는 429.
 * - DELETE /generation-jobs/{id}       : 종료된 작업 삭제(활성 작업은 409).
 *
 * 모든 엔드포인트는 인증 주체 ownerId로 소유 격리한다(CurrentUserProvider). 타인 소유·미존재는 404.
 */
@RestController
@RequestMapping("/generation-jobs")
class GenerationJobController(
    private val generationJobService: GenerationJobService,
    private val currentUserProvider: CurrentUserProvider,
) {

    @GetMapping("")
    fun list(): ResponseEntity<List<GenerationJobResponse>> =
        ResponseEntity.ok(generationJobService.list(currentUserProvider.currentUserId()))

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ResponseEntity<GenerationJobResponse> =
        ResponseEntity.ok(generationJobService.get(currentUserProvider.currentUserId(), toId(id)))

    /**
     * 일시적 실패 작업 '다시 만들기'(IN_PLACE). 저장된 입력으로 새 PENDING 작업을 만들고 실패 작업을 삭제한 뒤
     * 새 작업을 202로 돌려준다. 같은 입력 재요청이 무의미한 작업(입력 오류·한도 초과·활성·성공)은 서비스가 409,
     * 한도 초과는 429로 막는다.
     */
    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun retry(@PathVariable id: String): GenerationJobResponse =
        generationJobService.retryInPlace(currentUserProvider.currentUserId(), toId(id))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: String) {
        generationJobService.delete(currentUserProvider.currentUserId(), toId(id))
    }

    private fun toId(id: String): GenerationJobId = try {
        GenerationJobId(UUID.fromString(id))
    } catch (e: IllegalArgumentException) {
        throw DomainValidationException("입력 형식을 다시 확인해 주세요.")
    }
}
