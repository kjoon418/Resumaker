package watson.resumaker.fake

import watson.resumaker.model.dto.CreateExperienceRequest
import watson.resumaker.model.dto.CreateTargetRequest
import watson.resumaker.model.dto.ExperienceResponse
import watson.resumaker.model.dto.SignUpRequest
import watson.resumaker.model.dto.SignUpResponse
import watson.resumaker.model.dto.TargetResponse
import watson.resumaker.model.dto.UpdateExperienceRequest
import watson.resumaker.model.dto.UpdateTargetRequest
import watson.resumaker.network.AccountApi
import watson.resumaker.network.ApiResult
import watson.resumaker.network.ExperienceApi
import watson.resumaker.network.TargetApi
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
    var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit),
) : AccountApi {
    var lastSignUp: SignUpRequest? = null
    var deleteCalled = false

    override suspend fun signUp(request: SignUpRequest): ApiResult<SignUpResponse> {
        lastSignUp = request
        return signUpResult
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
