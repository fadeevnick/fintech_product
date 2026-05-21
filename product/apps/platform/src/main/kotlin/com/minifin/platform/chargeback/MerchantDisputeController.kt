package com.minifin.platform.chargeback

import com.minifin.platform.identity.ApiError
import com.minifin.platform.identity.ApiResponse
import com.minifin.platform.identity.IdentityException
import com.minifin.platform.identity.IdentityService
import com.minifin.platform.merchant.dashboard.MerchantDashboardException
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

private const val MERCHANT_SESSION_COOKIE = "MFP_SESSION"

@RestController
class MerchantDisputeController(
    private val identityService: IdentityService,
    private val chargebackService: ChargebackService,
) {
    @PostMapping("/api/v1/merchant/disputes/{disputeId}/evidence")
    fun submitEvidence(
        @CookieValue(name = MERCHANT_SESSION_COOKIE, required = false) sessionToken: String?,
        @PathVariable disputeId: String,
        @RequestBody request: SubmitEvidenceRequest,
    ): ApiResponse<EvidenceSubmissionDto> {
        val employee = identityService.currentMerchant(sessionToken)
        return ApiResponse(data = chargebackService.submitEvidence(employee, parseUuid(disputeId), request))
    }

    @PostMapping("/api/v1/merchant/disputes/{disputeId}/accept")
    fun acceptDispute(
        @CookieValue(name = MERCHANT_SESSION_COOKIE, required = false) sessionToken: String?,
        @PathVariable disputeId: String,
    ): ApiResponse<MerchantAcceptChargebackDto> {
        val employee = identityService.currentMerchant(sessionToken)
        return ApiResponse(data = chargebackService.acceptDispute(employee, parseUuid(disputeId)))
    }

    @ExceptionHandler(MerchantDashboardException::class)
    fun handleDashboardException(exception: MerchantDashboardException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status).body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message, exception.field))))

    @ExceptionHandler(IdentityException::class)
    fun handleIdentityException(exception: IdentityException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status).body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message, exception.field))))

    private fun parseUuid(value: String): UUID = runCatching { UUID.fromString(value) }
        .getOrElse { throw MerchantDashboardException("invalid_uuid", "Invalid UUID.", HttpStatus.BAD_REQUEST, "disputeId") }
}
