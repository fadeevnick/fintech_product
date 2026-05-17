package com.minifin.platform.merchant.webhooks

import com.minifin.platform.identity.ApiError
import com.minifin.platform.identity.ApiResponse
import com.minifin.platform.identity.IdentityException
import com.minifin.platform.identity.IdentityService
import com.minifin.platform.merchant.dashboard.MerchantDashboardException
import java.util.UUID
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

private const val SESSION_COOKIE = "MFP_SESSION"

@RestController
class MerchantWebhookEndpointController(
    private val identityService: IdentityService,
    private val service: MerchantWebhookEndpointService,
) {
    @GetMapping("/api/v1/merchant/webhook-endpoints")
    fun list(@CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?): ApiResponse<List<WebhookEndpointDto>> =
        ApiResponse(data = service.list(identityService.currentMerchant(sessionToken)))

    @PostMapping("/api/v1/merchant/webhook-endpoints")
    fun create(
        @CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?,
        @RequestBody request: WebhookEndpointRequest,
    ): ApiResponse<WebhookEndpointDto> = ApiResponse(data = service.create(identityService.currentMerchant(sessionToken), request))

    @PutMapping("/api/v1/merchant/webhook-endpoints/{id}")
    fun update(
        @CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?,
        @PathVariable id: String,
        @RequestBody request: WebhookEndpointRequest,
    ): ApiResponse<WebhookEndpointDto> = ApiResponse(data = service.update(identityService.currentMerchant(sessionToken), parseUuid(id), request))

    @DeleteMapping("/api/v1/merchant/webhook-endpoints/{id}")
    fun delete(
        @CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?,
        @PathVariable id: String,
    ): ApiResponse<WebhookEndpointDto> = ApiResponse(data = service.delete(identityService.currentMerchant(sessionToken), parseUuid(id)))

    @ExceptionHandler(MerchantDashboardException::class)
    fun handleDashboardException(exception: MerchantDashboardException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status).body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message, exception.field))))

    @ExceptionHandler(IdentityException::class)
    fun handleIdentityException(exception: IdentityException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status).body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message, exception.field))))

    private fun parseUuid(value: String): UUID = runCatching { UUID.fromString(value) }
        .getOrElse { throw MerchantDashboardException("invalid_uuid", "Invalid UUID.", org.springframework.http.HttpStatus.BAD_REQUEST, "id") }
}
