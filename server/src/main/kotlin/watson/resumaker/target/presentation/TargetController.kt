package watson.resumaker.target.presentation

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import watson.resumaker.account.application.CurrentUserProvider
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.target.application.TargetService
import watson.resumaker.target.domain.TargetBriefId
import java.util.UUID

/**
 * 목표 정보 API: POST/GET/PATCH/DELETE /targets. 모든 동작은 현재 사용자 소유로 격리된다.
 */
@RestController
@RequestMapping("/targets")
class TargetController(
    private val service: TargetService,
    private val mapper: TargetMapper,
    private val currentUserProvider: CurrentUserProvider,
) {

    @PostMapping
    fun create(@Valid @RequestBody request: CreateTargetRequest): ResponseEntity<TargetResponse> {
        val response = service.create(currentUserProvider.currentUserId(), mapper.toCreateCommand(request))
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping
    fun getAll(): ResponseEntity<List<TargetResponse>> =
        ResponseEntity.ok(service.getAll(currentUserProvider.currentUserId()))

    @GetMapping("/{id}")
    fun getOne(@PathVariable id: String): ResponseEntity<TargetResponse> =
        ResponseEntity.ok(service.getOne(currentUserProvider.currentUserId(), toId(id)))

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateTargetRequest,
    ): ResponseEntity<TargetResponse> {
        val response = service.update(currentUserProvider.currentUserId(), toId(id), mapper.toUpdateCommand(request))
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): ResponseEntity<Void> {
        service.delete(currentUserProvider.currentUserId(), toId(id))
        return ResponseEntity.noContent().build()
    }

    /**
     * 작성 전략 추출을 다시 요청한다(비동기 — 상태를 PENDING으로 리셋하고 워커가 추출). 즉시 202를 돌려주고,
     * 클라이언트는 GET /{id}의 strategyStatus로 폴링한다. 소유 격리로 본인 목표만 재시도한다.
     */
    @PostMapping("/{id}/strategy/retry")
    fun retryStrategy(@PathVariable id: String): ResponseEntity<Void> {
        service.retryStrategy(currentUserProvider.currentUserId(), toId(id))
        return ResponseEntity.accepted().build()
    }

    private fun toId(id: String): TargetBriefId = try {
        TargetBriefId(UUID.fromString(id))
    } catch (e: IllegalArgumentException) {
        throw DomainValidationException("입력 형식을 다시 확인해 주세요.")
    }
}
