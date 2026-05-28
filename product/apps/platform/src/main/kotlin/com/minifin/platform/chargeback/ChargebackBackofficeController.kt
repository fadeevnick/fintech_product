package com.minifin.platform.chargeback

import com.minifin.platform.backoffice.BackofficeException
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ChargebackBackofficeController(
    private val roleMapper: BackofficeRoleMapper,
    private val chargebackService: ChargebackService,
) {
    @GetMapping("/api/v1/backoffice/disputes")
    fun listDisputes(
        authentication: JwtAuthenticationToken,
        @RequestParam(name = "limit", required = false) limit: Int?,
    ): ApiResponse<ChargebackDisputeListResponse> {
        roleMapper.requireBackofficePrincipal(authentication.token)
        return ApiResponse(data = chargebackService.listBackofficeDisputes(limit ?: 25))
    }

    @GetMapping("/api/v1/backoffice/disputes/{disputeId}")
    fun disputeDetail(
        authentication: JwtAuthenticationToken,
        @PathVariable disputeId: String,
    ): ApiResponse<BackofficeDisputeDetailDto> {
        val principal = roleMapper.requireBackofficePrincipal(authentication.token)
        return ApiResponse(data = chargebackService.getBackofficeDispute(parseUuid(disputeId), principal))
    }

    @PostMapping("/api/v1/backoffice/disputes/{disputeId}/arbitration")
    fun decideArbitration(
        authentication: JwtAuthenticationToken,
        @PathVariable disputeId: String,
        @RequestBody request: ArbitrationDecisionRequest,
    ): ApiResponse<ArbitrationDecisionDto> {
        val principal = roleMapper.requireBackofficePrincipal(authentication.token)
        return ApiResponse(data = chargebackService.decideArbitration(parseUuid(disputeId), request, principal))
    }

    @ExceptionHandler(BackofficeException::class)
    fun handleBackofficeException(exception: BackofficeException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status).body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message))))

    private fun parseUuid(value: String): UUID = runCatching { UUID.fromString(value) }
        .getOrElse { throw BackofficeException("invalid_uuid", "Invalid UUID.", HttpStatus.BAD_REQUEST) }
}
