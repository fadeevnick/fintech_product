package com.minifin.platform.merchant.apikeys

import com.minifin.platform.identity.ApiError
import com.minifin.platform.identity.ApiResponse
import com.minifin.platform.identity.IdentityException
import com.minifin.platform.identity.IdentityService
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

private const val SESSION_COOKIE = "MFP_SESSION"

@RestController
class MerchantApiKeyController(
    private val apiKeyService: ApiKeyService,
    private val identityService: IdentityService,
) {
    @PostMapping("/api/v1/merchant/api-keys")
    fun create(
        @CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?,
        @RequestBody(required = false) request: CreateApiKeyRequest?,
    ): ResponseEntity<ApiResponse<CreateApiKeyResponse>> {
        val employee = identityService.currentMerchant(sessionToken)
        val response = apiKeyService.create(employee, request ?: CreateApiKeyRequest())
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse(data = response))
    }

    @GetMapping("/api/v1/merchant/api-keys")
    fun list(
        @CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?,
    ): ApiResponse<List<ApiKeySummary>> {
        val employee = identityService.currentMerchant(sessionToken)
        return ApiResponse(data = apiKeyService.list(employee))
    }

    @PostMapping("/api/v1/merchant/api-keys/{apiKeyId}/revoke")
    fun revoke(
        @CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?,
        @PathVariable apiKeyId: String,
    ): ApiResponse<RevokeApiKeyResponse> {
        val employee = identityService.currentMerchant(sessionToken)
        val keyUuid = runCatching { UUID.fromString(apiKeyId) }
            .getOrElse {
                throw ApiKeyException(
                    code = "invalid_uuid",
                    message = "Invalid API key id.",
                    status = HttpStatus.BAD_REQUEST,
                )
            }
        return ApiResponse(data = apiKeyService.revoke(employee, keyUuid))
    }

    @ExceptionHandler(ApiKeyException::class)
    fun handleApiKeyException(exception: ApiKeyException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status)
            .body(
                ApiResponse(
                    errors = listOf(
                        ApiError(
                            code = exception.code,
                            message = exception.message,
                            field = exception.field,
                        ),
                    ),
                ),
            )

    @ExceptionHandler(IdentityException::class)
    fun handleIdentityException(exception: IdentityException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status)
            .body(
                ApiResponse(
                    errors = listOf(
                        ApiError(
                            code = exception.code,
                            message = exception.message,
                            field = exception.field,
                        ),
                    ),
                ),
            )
}
