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
import watson.resumaker.template.application.TemplateInterpretService
import watson.resumaker.template.application.TemplatePresetService
import watson.resumaker.template.application.TemplateService
import watson.resumaker.template.domain.ResumeTemplateId
import java.util.UUID

/**
 * 이력서 양식 API(FU-A/B/C).
 * - POST/GET/PATCH/DELETE /resume-templates: 사용자 소유 양식 CRUD(FU-A).
 * - GET /resume-templates/presets: 서비스 제공 프리셋 목록(FU-B, 소유 격리 없음).
 * - POST /resume-templates/interpret: 붙여넣기 해석 후보 반환(FU-C, 영속 안 함).
 *
 * Spring MVC는 정적 경로(`/presets`, `/interpret`)를 경로 변수(`/{id}`)보다 우선 매칭한다.
 */
@RestController
@RequestMapping("/resume-templates")
class TemplateController(
    private val service: TemplateService,
    private val mapper: TemplateMapper,
    private val currentUserProvider: CurrentUserProvider,
    private val presetService: TemplatePresetService,
    private val interpretService: TemplateInterpretService,
) {

    // ── FU-B: 프리셋 목록 ──────────────────────────────────────────────────────

    @GetMapping("/presets")
    fun getPresets(): ResponseEntity<List<TemplatePresetResponse>> =
        ResponseEntity.ok(presetService.getAll())

    // ── FU-C: 붙여넣기 해석 (영속 안 함) ───────────────────────────────────────

    @PostMapping("/interpret")
    fun interpret(@Valid @RequestBody request: InterpretRequest): ResponseEntity<InterpretResponse> =
        ResponseEntity.ok(interpretService.interpret(request.text!!))

    // ── FU-A: 사용자 소유 양식 CRUD ─────────────────────────────────────────────

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
