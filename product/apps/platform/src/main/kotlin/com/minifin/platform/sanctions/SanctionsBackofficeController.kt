package com.minifin.platform.sanctions

import com.minifin.platform.backoffice.BackofficeException
import com.minifin.platform.backoffice.BackofficePrincipal
import com.minifin.platform.backoffice.BackofficeRoleMapper
import com.minifin.platform.identity.ApiError
import com.minifin.platform.identity.ApiResponse
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
open class SanctionsBackofficeController(
    private val roleMapper: BackofficeRoleMapper,
    private val service: SanctionsService,
) {
    @GetMapping("/api/v1/backoffice/sanctions-hits")
    open fun list(authentication: JwtAuthenticationToken): ApiResponse<List<SanctionsHitResponse>> {
        requireCompliancePrincipal(authentication)
        return ApiResponse(data = service.listHits())
    }

    @GetMapping("/api/v1/backoffice/sanctions-hits/{id}")
    open fun detail(authentication: JwtAuthenticationToken, @PathVariable id: String): ApiResponse<SanctionsHitResponse> {
        val principal = requireCompliancePrincipal(authentication)
        return ApiResponse(data = service.getHit(requireUuid(id), principal))
    }

    @PostMapping("/api/v1/backoffice/sanctions-hits/{id}/decision")
    open fun decide(
        authentication: JwtAuthenticationToken,
        @PathVariable id: String,
        @RequestBody request: SanctionsHitDecisionRequest,
    ): ApiResponse<SanctionsHitDecisionResponse> {
        val principal = roleMapper.requireBackofficePrincipal(authentication.token)
        return ApiResponse(data = service.decideHit(requireUuid(id), request, principal))
    }

    @ExceptionHandler(SanctionsException::class)
    open fun handleSanctionsException(exception: SanctionsException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status).body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message))))

    @ExceptionHandler(BackofficeException::class)
    open fun handleBackofficeException(exception: BackofficeException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status).body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message))))

    private fun requireUuid(value: String): UUID =
        runCatching { UUID.fromString(value) }
            .getOrElse { throw SanctionsException("invalid_uuid", "Invalid UUID.", HttpStatus.BAD_REQUEST) }

    private fun requireCompliancePrincipal(authentication: JwtAuthenticationToken): BackofficePrincipal {
        val principal = roleMapper.requireBackofficePrincipal(authentication.token)
        if (principal.roles.none { it == "compliance_officer" || it == "senior_compliance" }) {
            throw SanctionsException("forbidden_role", "Compliance role is required.", HttpStatus.FORBIDDEN)
        }
        return principal
    }
}
