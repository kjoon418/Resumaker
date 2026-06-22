package watson.resumaker.generation.presentation

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
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
 * - GET    /generation-jobs       : 내 생성 작업 목록(최신순). 폴링·작업 카드 표시용.
 * - GET    /generation-jobs/{id}  : 단건 조회(상태·결과 폴링). 완료 시 artifactId로 산출물 열람.
 * - DELETE /generation-jobs/{id}  : 종료된 작업 삭제(활성 작업은 409).
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
