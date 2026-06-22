package watson.resumaker.fake

import watson.resumaker.model.dto.ArtifactResponse
import watson.resumaker.model.dto.ArtifactSummaryResponse
import watson.resumaker.model.dto.ArtifactVersionsResponse
import watson.resumaker.model.dto.CreateExperienceRequest
import watson.resumaker.model.dto.CreateTargetRequest
import watson.resumaker.model.dto.ExperienceResponse
import watson.resumaker.model.dto.GenerationJobResponse
import watson.resumaker.model.dto.PortfolioGenerationRequest
import watson.resumaker.model.dto.ResumeGenerationRequest
import watson.resumaker.model.dto.InterpretRequest
import watson.resumaker.model.dto.InterpretResponse
import watson.resumaker.model.dto.LoginRequest
import watson.resumaker.model.dto.LoginResponse
import watson.resumaker.model.dto.CreateTemplateRequest
import watson.resumaker.model.dto.SignUpRequest
import watson.resumaker.model.dto.SignUpResponse
import watson.resumaker.model.dto.TargetResponse
import watson.resumaker.model.dto.TemplatePresetResponse
import watson.resumaker.model.dto.TemplateResponse
import watson.resumaker.model.dto.UpdateExperienceRequest
import watson.resumaker.model.dto.UpdateTargetRequest
import watson.resumaker.model.dto.UpdateTemplateRequest
import watson.resumaker.network.AccountApi
import watson.resumaker.network.ApiResult
import watson.resumaker.network.ArtifactApi
import watson.resumaker.network.ExperienceApi
import watson.resumaker.network.TargetApi
import watson.resumaker.network.TemplateApi
import watson.resumaker.network.TemplateInterpretApi
import watson.resumaker.network.TemplatePresetApi
import watson.resumaker.session.SessionStore

/** 테스트용 인메모리 세션. */
class FakeSessionStore(
    private var userId: String? = null,
    private var email: String? = null,
) : SessionStore {
    var cleared = false
        private set

    override fun currentUserId(): String? = userId
    override fun currentEmail(): String? = email
    override fun save(userId: String, email: String?) {
        this.userId = userId
        if (email != null) this.email = email
    }
    override fun clear() {
        userId = null
        email = null
        cleared = true
    }
}

/** 결과를 미리 지정하는 fake AccountApi. */
class FakeAccountApi(
    var signUpResult: ApiResult<SignUpResponse> = ApiResult.Success(SignUpResponse("u-1")),
    var loginResult: ApiResult<LoginResponse> = ApiResult.Success(LoginResponse("u-1")),
    var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit),
    var logoutResult: ApiResult<Unit> = ApiResult.Success(Unit),
) : AccountApi {
    var lastSignUp: SignUpRequest? = null
    var lastLogin: LoginRequest? = null
    var deleteCalled = false
    var logoutCalled = false

    override suspend fun signUp(request: SignUpRequest): ApiResult<SignUpResponse> {
        lastSignUp = request
        return signUpResult
    }
    override suspend fun login(request: LoginRequest): ApiResult<LoginResponse> {
        lastLogin = request
        return loginResult
    }
    override suspend fun logout(): ApiResult<Unit> {
        logoutCalled = true
        return logoutResult
    }
    override suspend fun deleteAccount(): ApiResult<Unit> {
        deleteCalled = true
        return deleteResult
    }
}

/** 결과를 미리 지정하는 fake ExperienceApi. */
class FakeExperienceApi(
    var getAllResult: ApiResult<List<ExperienceResponse>> = ApiResult.Success(emptyList()),
    var getOneResult: ApiResult<ExperienceResponse>? = null,
    var createResult: ApiResult<ExperienceResponse>? = null,
    var updateResult: ApiResult<ExperienceResponse>? = null,
    var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit),
) : ExperienceApi {
    var lastCreate: CreateExperienceRequest? = null
    var lastUpdate: Pair<String, UpdateExperienceRequest>? = null
    var deletedId: String? = null

    override suspend fun getAll() = getAllResult
    override suspend fun getOne(id: String) =
        getOneResult ?: ApiResult.Failure("not found")
    override suspend fun create(request: CreateExperienceRequest): ApiResult<ExperienceResponse> {
        lastCreate = request
        return createResult ?: ApiResult.Success(sampleExperience(title = request.title, type = request.type))
    }
    override suspend fun update(id: String, request: UpdateExperienceRequest): ApiResult<ExperienceResponse> {
        lastUpdate = id to request
        return updateResult ?: ApiResult.Success(sampleExperience(id = id, title = request.title, type = request.type))
    }
    override suspend fun delete(id: String): ApiResult<Unit> {
        deletedId = id
        return deleteResult
    }
}

/** 결과를 미리 지정하는 fake TargetApi. */
class FakeTargetApi(
    var getAllResult: ApiResult<List<TargetResponse>> = ApiResult.Success(emptyList()),
    var getOneResult: ApiResult<TargetResponse>? = null,
    var createResult: ApiResult<TargetResponse>? = null,
    var updateResult: ApiResult<TargetResponse>? = null,
    var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit),
) : TargetApi {
    var lastCreate: CreateTargetRequest? = null
    var deletedId: String? = null

    override suspend fun getAll() = getAllResult
    override suspend fun getOne(id: String) = getOneResult ?: ApiResult.Failure("not found")
    override suspend fun create(request: CreateTargetRequest): ApiResult<TargetResponse> {
        lastCreate = request
        return createResult ?: ApiResult.Success(
            TargetResponse(id = "t-1", recruitDirection = request.recruitDirection, companyName = request.companyName, jobTitle = request.jobTitle),
        )
    }
    override suspend fun update(id: String, request: UpdateTargetRequest): ApiResult<TargetResponse> =
        updateResult ?: ApiResult.Success(
            TargetResponse(id = id, recruitDirection = request.recruitDirection, companyName = request.companyName, jobTitle = request.jobTitle),
        )
    override suspend fun delete(id: String): ApiResult<Unit> {
        deletedId = id
        return deleteResult
    }
}

/** 결과를 미리 지정하는 fake TemplateApi. */
class FakeTemplateApi(
    var getAllResult: ApiResult<List<TemplateResponse>> = ApiResult.Success(emptyList()),
    var getOneResult: ApiResult<TemplateResponse>? = null,
    var createResult: ApiResult<TemplateResponse>? = null,
    var updateResult: ApiResult<TemplateResponse>? = null,
    var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit),
) : TemplateApi {
    var lastCreate: CreateTemplateRequest? = null
    var lastUpdate: Pair<String, UpdateTemplateRequest>? = null
    var deletedId: String? = null

    override suspend fun getAll() = getAllResult
    override suspend fun getOne(id: String) = getOneResult ?: ApiResult.Failure("not found")
    override suspend fun create(request: CreateTemplateRequest): ApiResult<TemplateResponse> {
        lastCreate = request
        return createResult ?: ApiResult.Success(
            TemplateResponse(id = "tpl-1", name = request.name, sections = request.sections.map { it.toResponse() }),
        )
    }
    override suspend fun update(id: String, request: UpdateTemplateRequest): ApiResult<TemplateResponse> {
        lastUpdate = id to request
        return updateResult ?: ApiResult.Success(
            TemplateResponse(id = id, name = request.name, sections = request.sections.map { it.toResponse() }),
        )
    }
    override suspend fun delete(id: String): ApiResult<Unit> {
        deletedId = id
        return deleteResult
    }
}

/** 결과를 미리 지정하는 fake TemplatePresetApi(FU-B). */
class FakeTemplatePresetApi(
    var getAllResult: ApiResult<List<TemplatePresetResponse>> = ApiResult.Success(emptyList()),
) : TemplatePresetApi {
    var getAllCount = 0
        private set

    override suspend fun getAll(): ApiResult<List<TemplatePresetResponse>> {
        getAllCount++
        return getAllResult
    }
}

/** 결과를 미리 지정하는 fake TemplateInterpretApi(FU-C). 호출 카운터로 미호출을 검증한다. */
class FakeTemplateInterpretApi(
    var interpretResult: ApiResult<InterpretResponse> = ApiResult.Success(InterpretResponse(status = InterpretResponse.STATUS_INTERPRETED)),
) : TemplateInterpretApi {
    var interpretCount = 0
        private set
    var lastInterpret: InterpretRequest? = null

    override suspend fun interpret(request: InterpretRequest): ApiResult<InterpretResponse> {
        interpretCount++
        lastInterpret = request
        return interpretResult
    }
}

/** 결과를 미리 지정하는 fake ArtifactApi. 호출 인자를 기록해 검증한다. */
class FakeArtifactApi(
    var generateResumeResult: ApiResult<GenerationJobResponse>? = null,
    var generatePortfolioResult: ApiResult<GenerationJobResponse>? = null,
    var getArtifactResult: ApiResult<ArtifactResponse>? = null,
    var regenerateResult: ApiResult<ArtifactResponse>? = null,
    var editResult: ApiResult<ArtifactResponse>? = null,
    var getVersionsResult: ApiResult<ArtifactVersionsResponse>? = null,
    var restoreResult: ApiResult<ArtifactResponse>? = null,
    var listJobsResult: ApiResult<List<GenerationJobResponse>> = ApiResult.Success(emptyList()),
    var getJobResult: ApiResult<GenerationJobResponse>? = null,
    var deleteJobResult: ApiResult<Unit> = ApiResult.Success(Unit),
    var listArtifactsResult: ApiResult<List<ArtifactSummaryResponse>> = ApiResult.Success(emptyList()),
    /**
     * 호출마다 다른 listJobs 결과를 돌려주기 위한 큐(폴링 전환 테스트용). 비어 있지 않으면 매 호출 앞에서 하나씩
     * 꺼내 [listJobsResult]를 갱신한 뒤 반환한다. 비면 마지막 [listJobsResult]를 계속 반환한다.
     */
    var listJobsSequence: ArrayDeque<ApiResult<List<GenerationJobResponse>>> = ArrayDeque(),
) : ArtifactApi {
    var lastResumeRequest: ResumeGenerationRequest? = null
    var lastPortfolioRequest: PortfolioGenerationRequest? = null
    var getArtifactId: String? = null
    var deletedJobId: String? = null
    var getJobId: String? = null

    /** generateResume 호출 횟수(재시도 검증용). */
    var generateResumeCallCount = 0
        private set

    /** listJobs/listArtifacts 호출 횟수(폴링 검증용). */
    var listJobsCallCount = 0
        private set
    var listArtifactsCallCount = 0
        private set

    /** 버전 목록 조회에 들어온 artifactId 기록. */
    var getVersionsId: String? = null

    /** 복원 호출 인자(artifactId, versionId) 기록. */
    var lastRestore: Pair<String, String>? = null

    var getVersionsCount = 0
        private set
    var restoreCount = 0
        private set

    /**
     * 복원 호출을 한 번 일시정지시키는 게이트. `gate.complete(Unit)`를 호출해야 응답이 반환된다. null이면 즉시
     * 반환한다. 복원 in-flight(진행 중) 상태에서의 중복 복원 차단을 테스트할 때 진행 중을 유지하는 데 쓴다.
     */
    var restoreGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    /** 재생성 호출 인자(artifactId, sectionId, directive) 기록. directive는 trim 후 빈 값이면 null로 들어온다. */
    var lastRegenerate: Triple<String, String, String?>? = null

    /** 편집 호출 인자(artifactId, sectionId, content) 기록. */
    var lastEdit: Triple<String, String, String>? = null

    /** 같은 호출 카운트(in-flight 가드·중복 호출 검증용). */
    var regenerateCount = 0
        private set
    var editCount = 0
        private set

    /**
     * 재생성 호출을 한 번 일시정지시키는 게이트. `gate.complete(Unit)`를 호출해야 응답이 반환된다.
     * null이면 즉시 반환한다. in-flight(진행 중) 상태에서의 중복 액션 차단을 테스트할 때 진행 중을
     * 유지하는 데 쓴다.
     */
    var regenerateGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    /**
     * 편집 호출을 한 번 일시정지시키는 게이트. `gate.complete(Unit)`를 호출해야 응답이 반환된다.
     * null이면 즉시 반환한다. 편집-in-flight 상태에서의 재생성 교차 차단을 테스트할 때 쓴다.
     */
    var editGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun generateResume(request: ResumeGenerationRequest): ApiResult<GenerationJobResponse> {
        generateResumeCallCount++
        lastResumeRequest = request
        return generateResumeResult ?: ApiResult.Failure("no result")
    }

    override suspend fun generatePortfolio(request: PortfolioGenerationRequest): ApiResult<GenerationJobResponse> {
        lastPortfolioRequest = request
        return generatePortfolioResult ?: ApiResult.Failure("no result")
    }

    override suspend fun listJobs(): ApiResult<List<GenerationJobResponse>> {
        listJobsCallCount++
        if (listJobsSequence.isNotEmpty()) {
            listJobsResult = listJobsSequence.removeFirst()
        }
        return listJobsResult
    }

    override suspend fun getJob(id: String): ApiResult<GenerationJobResponse> {
        getJobId = id
        return getJobResult ?: ApiResult.Failure("no result")
    }

    override suspend fun deleteJob(id: String): ApiResult<Unit> {
        deletedJobId = id
        return deleteJobResult
    }

    override suspend fun listArtifacts(): ApiResult<List<ArtifactSummaryResponse>> {
        listArtifactsCallCount++
        return listArtifactsResult
    }

    override suspend fun getArtifact(id: String): ApiResult<ArtifactResponse> {
        getArtifactId = id
        return getArtifactResult ?: ApiResult.Failure("no result")
    }

    override suspend fun regenerateSection(
        artifactId: String,
        sectionId: String,
        directive: String?,
    ): ApiResult<ArtifactResponse> {
        regenerateCount++
        lastRegenerate = Triple(artifactId, sectionId, directive)
        regenerateGate?.await()
        return regenerateResult ?: ApiResult.Failure("no result")
    }

    override suspend fun editSectionContent(
        artifactId: String,
        sectionId: String,
        content: String,
    ): ApiResult<ArtifactResponse> {
        editCount++
        lastEdit = Triple(artifactId, sectionId, content)
        editGate?.await()
        return editResult ?: ApiResult.Failure("no result")
    }

    override suspend fun getVersions(artifactId: String): ApiResult<ArtifactVersionsResponse> {
        getVersionsCount++
        getVersionsId = artifactId
        return getVersionsResult ?: ApiResult.Failure("no result")
    }

    override suspend fun restoreVersion(artifactId: String, versionId: String): ApiResult<ArtifactResponse> {
        restoreCount++
        lastRestore = artifactId to versionId
        restoreGate?.await()
        return restoreResult ?: ApiResult.Failure("no result")
    }
}

private fun watson.resumaker.model.dto.SectionRequest.toResponse() =
    watson.resumaker.model.dto.SectionResponse(name = name, character = character, required = required)

fun sampleExperience(
    id: String = "e-1",
    title: String = "샘플",
    type: watson.resumaker.model.type.ExperienceType = watson.resumaker.model.type.ExperienceType.PROJECT,
) = ExperienceResponse(
    id = id,
    title = title,
    type = type,
    body = "본문",
    situation = null,
    action = null,
    result = null,
    periodStart = null,
    periodEnd = null,
    skillTags = emptyList(),
)
