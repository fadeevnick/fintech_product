package com.minifin.platform.kyc

import com.minifin.platform.controls.ActorControlException
import com.minifin.platform.identity.ApiError
import com.minifin.platform.identity.ApiResponse
import com.minifin.platform.identity.IdentityException
import com.minifin.platform.identity.IdentityService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

private const val SESSION_COOKIE = "MFP_SESSION"

@RestController
class KycController(
    private val identityService: IdentityService,
    private val kycService: KycService,
    private val signatureVerifier: SumsubSignatureVerifier,
) {
    @PostMapping("/api/v1/kyc/start")
    fun start(@CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?): ResponseEntity<ApiResponse<KycStartResponse>> {
        val user = identityService.currentUser(sessionToken)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse(data = kycService.start(user)))
    }

    @PostMapping("/webhooks/sumsub/v1")
    fun webhook(
        @RequestBody rawBody: String,
        @RequestHeader(name = "X-Payload-Digest", required = false) digest: String?,
        @RequestHeader(name = "X-Signature", required = false) signature: String?,
    ): ApiResponse<SumsubWebhookResponse> {
        val provided = digest ?: signature
        if (!signatureVerifier.verify(rawBody, provided)) {
            throw KycException("invalid_sumsub_signature", "Sumsub webhook signature is invalid.", HttpStatus.UNAUTHORIZED)
        }
        return ApiResponse(data = kycService.processWebhook(rawBody))
    }

    @ExceptionHandler(KycException::class)
    fun handleKycException(exception: KycException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status).body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message))))

    @ExceptionHandler(IdentityException::class)
    fun handleIdentityException(exception: IdentityException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status).body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message, exception.field))))

    @ExceptionHandler(ActorControlException::class)
    fun handleActorControlException(exception: ActorControlException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status).body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message))))
}
