package com.minifin.platform.merchant.webhooks

import com.minifin.platform.identity.ApiError
import com.minifin.platform.identity.ApiResponse
import com.minifin.platform.identity.IdentityException
import com.minifin.platform.identity.IdentityService
import com.minifin.platform.merchant.dashboard.MerchantDashboardException
import java.util.UUID
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
class MerchantWebhookEventController(
    private val identityService: IdentityService,
    private val service: OutboundWebhookService,
) {
    @GetMapping("/api/v1/merchant/webhook-events")
    fun list(
        @CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?,
        @RequestParam(name = "status", required = false) status: String?,
        @RequestParam(name = "limit", required = false) limit: Int?,
        @RequestParam(name = "endpointId", required = false) endpointId: String?,
    ): ApiResponse<WebhookEventListResponse> {
        val endpointUuid = endpointId?.let {
            runCatching { UUID.fromString(it) }.getOrElse {
                throw MerchantDashboardException("invalid_uuid", "Invalid endpoint ID.", org.springframework.http.HttpStatus.BAD_REQUEST, "endpointId")
            }
        }
        return ApiResponse(data = service.listEvents(identityService.currentMerchant(sessionToken), status, limit ?: 25, endpointUuid))
    }

    @GetMapping("/api/v1/merchant/webhook-events/{id}")
    fun detail(
        @CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?,
        @PathVariable id: String,
    ): ApiResponse<WebhookEventDto> =
        ApiResponse(data = service.detail(identityService.currentMerchant(sessionToken), parseUuid(id)))

    @PostMapping("/api/v1/merchant/webhook-events/{id}/replay")
    fun replay(
        @CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?,
        @PathVariable id: String,
    ): ApiResponse<WebhookReplayResponse> =
        ApiResponse(data = service.replay(identityService.currentMerchant(sessionToken), parseUuid(id)))

    @ExceptionHandler(MerchantDashboardException::class)
    fun handleDashboardException(exception: MerchantDashboardException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status).body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message, exception.field))))

    @ExceptionHandler(IdentityException::class)
    fun handleIdentityException(exception: IdentityException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status).body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message, exception.field))))

    private fun parseUuid(value: String): UUID = runCatching { UUID.fromString(value) }
        .getOrElse { throw MerchantDashboardException("invalid_uuid", "Invalid UUID.", org.springframework.http.HttpStatus.BAD_REQUEST, "id") }
}
