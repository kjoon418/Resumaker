package watson.resumaker.template.presentation

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
import watson.resumaker.template.application.TemplateService
import watson.resumaker.template.domain.ResumeTemplateId
import java.util.UUID

/**
 * 이력서 양식 API: POST/GET/PATCH/DELETE /resume-templates(FU-A).
 * 모든 동작은 현재 사용자 소유로 격리된다.
 */
@RestController
@RequestMapping("/resume-templates")
class TemplateController(
    private val service: TemplateService,
    private val mapper: TemplateMapper,
    private val currentUserProvider: CurrentUserProvider,
) {

    @PostMapping
    fun create(@Valid @RequestBody request: CreateTemplateRequest): ResponseEntity<TemplateResponse> {
        val response = service.create(currentUserProvider.currentUserId(), mapper.toCreateCommand(request))
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping
    fun getAll(): ResponseEntity<List<TemplateResponse>> =
        ResponseEntity.ok(service.getAll(currentUserProvider.currentUserId()))

    @GetMapping("/{id}")
    fun getOne(@PathVariable id: String): ResponseEntity<TemplateResponse> =
        ResponseEntity.ok(service.getOne(currentUserProvider.currentUserId(), toId(id)))

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateTemplateRequest,
    ): ResponseEntity<TemplateResponse> {
        val response = service.update(currentUserProvider.currentUserId(), toId(id), mapper.toUpdateCommand(request))
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): ResponseEntity<Void> {
        service.delete(currentUserProvider.currentUserId(), toId(id))
        return ResponseEntity.noContent().build()
    }

    private fun toId(id: String): ResumeTemplateId = ResumeTemplateId(UUID.fromString(id))
}
