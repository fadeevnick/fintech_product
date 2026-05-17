package com.minifin.platform.merchant.dashboard

import com.minifin.platform.identity.ApiError
import com.minifin.platform.identity.ApiResponse
import com.minifin.platform.identity.IdentityException
import com.minifin.platform.identity.IdentityService
import com.minifin.platform.publicapi.PaymentIntentDto
import com.minifin.platform.publicapi.toDto
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private const val SESSION_COOKIE = "MFP_SESSION"

class MerchantDashboardException(
    val code: String,
    override val message: String,
    val status: HttpStatus,
    val field: String? = null,
) : RuntimeException(message)

data class PaymentIntentListResponse(
    val items: List<PaymentIntentDto>,
    val nextCursor: String? = null,
)

@RestController
class MerchantPaymentDashboardController(
    private val identityService: IdentityService,
    private val repository: MerchantPaymentDashboardRepository,
) {
    @GetMapping("/api/v1/merchant/payment-intents")
    fun list(
        @CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?,
        @RequestParam(name = "state", required = false) state: String?,
        @RequestParam(name = "limit", required = false) limit: Int?,
    ): ApiResponse<PaymentIntentListResponse> {
        val employee = identityService.currentMerchant(sessionToken)
        requireActiveMerchant(employee.status)
        val normalizedLimit = (limit ?: 25).coerceIn(1, 100)
        val normalizedState = state?.trim()?.takeIf { it.isNotBlank() }
        if (normalizedState != null && normalizedState != "REQUIRES_PAYMENT_METHOD") {
            throw MerchantDashboardException("invalid_state", "Payment intent state filter is invalid.", HttpStatus.BAD_REQUEST, "state")
        }
        return ApiResponse(data = PaymentIntentListResponse(repository.list(employee.merchantId, normalizedState, normalizedLimit).map { it.toDto() }))
    }

    @GetMapping("/api/v1/merchant/payment-intents/{id}")
    fun detail(
        @CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?,
        @PathVariable id: String,
    ): ApiResponse<PaymentIntentDto> {
        val employee = identityService.currentMerchant(sessionToken)
        requireActiveMerchant(employee.status)
        val uuid = parseUuid(id, "paymentIntentId")
        val record = repository.findByIdForMerchant(uuid, employee.merchantId)
            ?: throw MerchantDashboardException("payment_intent_not_found", "Payment intent was not found.", HttpStatus.NOT_FOUND)
        return ApiResponse(data = record.toDto())
    }

    @ExceptionHandler(MerchantDashboardException::class)
    fun handleDashboardException(exception: MerchantDashboardException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status).body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message, exception.field))))

    @ExceptionHandler(IdentityException::class)
    fun handleIdentityException(exception: IdentityException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status).body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message, exception.field))))

    private fun requireActiveMerchant(status: String) {
        if (status != "ACTIVE") throw MerchantDashboardException("merchant_employee_not_active", "Merchant employee is not active.", HttpStatus.FORBIDDEN)
    }

    private fun parseUuid(value: String, field: String): UUID = runCatching { UUID.fromString(value) }
        .getOrElse { throw MerchantDashboardException("invalid_uuid", "Invalid UUID.", HttpStatus.BAD_REQUEST, field) }
}
