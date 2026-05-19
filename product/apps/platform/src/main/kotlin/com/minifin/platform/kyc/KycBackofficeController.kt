package com.minifin.platform.kyc

import com.minifin.platform.backoffice.BackofficeException
import com.minifin.platform.backoffice.BackofficeRoleMapper
import com.minifin.platform.identity.ApiError
import com.minifin.platform.identity.ApiResponse
import com.minifin.platform.sanctions.SanctionsException
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class KycBackofficeController(
    private val roleMapper: BackofficeRoleMapper,
    private val service: KycBackofficeService,
) {
    @GetMapping("/api/v1/backoffice/kyc-cases")
    fun list(authentication: JwtAuthenticationToken): ApiResponse<List<KycCaseResponse>> {
        roleMapper.requireBackofficePrincipal(authentication.token)
        return ApiResponse(data = service.listCases())
    }

    @GetMapping("/api/v1/backoffice/kyc-cases/{id}")
    fun detail(authentication: JwtAuthenticationToken, @PathVariable id: String): ApiResponse<KycCaseResponse> {
        roleMapper.requireBackofficePrincipal(authentication.token)
        return ApiResponse(data = service.getCase(requireUuid(id)))
    }

    @PostMapping("/api/v1/backoffice/kyc-cases/{id}/decision")
    fun decide(
        authentication: JwtAuthenticationToken,
        @PathVariable id: String,
        @RequestBody request: KycManualDecisionRequest,
    ): ApiResponse<KycManualDecisionResponse> {
        val principal = roleMapper.requireBackofficePrincipal(authentication.token)
        return ApiResponse(data = service.decide(requireUuid(id), request, principal))
    }

    @ExceptionHandler(KycException::class)
    fun handleKycException(exception: KycException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status).body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message))))

    @ExceptionHandler(BackofficeException::class)
    fun handleBackofficeException(exception: BackofficeException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status).body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message))))

    @ExceptionHandler(SanctionsException::class)
    fun handleSanctionsException(exception: SanctionsException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status).body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message))))

    private fun requireUuid(value: String): UUID =
        runCatching { UUID.fromString(value) }
            .getOrElse { throw KycException("invalid_uuid", "Invalid UUID.", HttpStatus.BAD_REQUEST) }
}
