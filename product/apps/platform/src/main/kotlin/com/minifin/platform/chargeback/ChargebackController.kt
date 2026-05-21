package com.minifin.platform.chargeback

import com.minifin.platform.identity.ApiError
import com.minifin.platform.identity.ApiResponse
import com.minifin.platform.identity.IdentityException
import com.minifin.platform.identity.IdentityService
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

private const val SESSION_COOKIE = "MFP_SESSION"

@RestController
class ChargebackController(
    private val identityService: IdentityService,
    private val chargebackService: ChargebackService,
) {
    @PostMapping("/api/v1/card-payments/{paymentIntentId}/disputes")
    fun createDispute(
        @CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?,
        @PathVariable paymentIntentId: String,
        @RequestBody request: CreateDisputeRequest,
    ): ResponseEntity<ApiResponse<ChargebackDisputeDto>> {
        val user = identityService.currentUser(sessionToken)
        val id = runCatching { UUID.fromString(paymentIntentId) }.getOrElse {
            throw ChargebackException("invalid_payment_id", "Payment id is invalid.", HttpStatus.BAD_REQUEST, "paymentIntentId")
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse(data = chargebackService.createDispute(user, id, request)))
    }

    @ExceptionHandler(ChargebackException::class)
    fun handleChargebackException(exception: ChargebackException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status).body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message, exception.field))))

    @ExceptionHandler(IdentityException::class)
    fun handleIdentityException(exception: IdentityException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status).body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message, exception.field))))
}
