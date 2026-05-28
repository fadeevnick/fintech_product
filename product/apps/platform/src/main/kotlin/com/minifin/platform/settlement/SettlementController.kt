package com.minifin.platform.settlement

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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private const val SESSION_COOKIE = "MFP_SESSION"

@RestController
class SettlementController(
    private val identityService: IdentityService,
    private val settlementService: SettlementService,
) {
    @GetMapping("/api/v1/merchant/settlements")
    fun listMerchantSettlements(
        @CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?,
        @RequestParam(name = "limit", required = false) limit: Int?,
    ): ApiResponse<MerchantSettlementBatchListResponse> {
        val employee = identityService.currentMerchant(sessionToken)
        return ApiResponse(data = settlementService.listMerchantSettlements(employee, limit ?: 25))
    }

    @GetMapping("/api/v1/merchant/settlements/{batchId}")
    fun settlementDetail(
        @CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?,
        @PathVariable batchId: String,
    ): ApiResponse<MerchantSettlementBatchDetailDto> {
        val employee = identityService.currentMerchant(sessionToken)
        return ApiResponse(data = settlementService.getMerchantSettlement(employee, parseUuid(batchId)))
    }

    @PostMapping("/internal/settlement/process-captured")
    fun processCaptured(
        @RequestParam(defaultValue = "50") limit: Int,
    ): ApiResponse<SettlementProcessResponse> =
        ApiResponse(data = settlementService.processCaptured(limit))

    @PostMapping("/internal/settlement/publish-projections")
    fun publishProjections(
        @RequestParam(defaultValue = "100") limit: Int,
    ): ApiResponse<SettlementProjectionPublishResponse> =
        ApiResponse(data = settlementService.publishProjections(limit))

    @ExceptionHandler(SettlementException::class)
    fun handleSettlementException(exception: SettlementException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status)
            .body(
                ApiResponse(
                    errors = listOf(
                        ApiError(
                            code = exception.code,
                            message = exception.message,
                        ),
                    ),
                ),
            )

    @ExceptionHandler(MerchantDashboardException::class)
    fun handleMerchantDashboardException(exception: MerchantDashboardException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status)
            .body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message, exception.field))))

    @ExceptionHandler(IdentityException::class)
    fun handleIdentityException(exception: IdentityException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status)
            .body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message, exception.field))))

    private fun parseUuid(value: String): UUID = runCatching { UUID.fromString(value) }
        .getOrElse { throw MerchantDashboardException("invalid_uuid", "Invalid UUID.", HttpStatus.BAD_REQUEST, "batchId") }
}
