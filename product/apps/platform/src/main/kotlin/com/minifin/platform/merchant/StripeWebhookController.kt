package com.minifin.platform.merchant

import com.minifin.platform.identity.ApiError
import com.minifin.platform.identity.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class StripeWebhookController(
    private val service: StripeWebhookService,
) {
    @PostMapping(
        value = ["/webhooks/stripe/v1"],
        consumes = [MediaType.ALL_VALUE],
    )
    fun handle(
        @RequestBody rawBody: ByteArray,
        @RequestHeader(name = "Stripe-Signature", required = false) signatureHeader: String?,
    ): ApiResponse<StripeWebhookResponse> {
        val result = service.handle(rawBody, signatureHeader)
        return ApiResponse(
            data = StripeWebhookResponse(
                received = true,
                eventId = result.eventId,
                eventType = result.eventType,
                outcome = result.outcome,
            ),
        )
    }

    @ExceptionHandler(StripeWebhookException::class)
    fun handleException(exception: StripeWebhookException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status).body(
            ApiResponse(
                errors = listOf(
                    ApiError(
                        code = exception.code,
                        message = exception.message,
                    ),
                ),
            ),
        )
}

data class StripeWebhookResponse(
    val received: Boolean,
    val eventId: String?,
    val eventType: String?,
    val outcome: String,
)
