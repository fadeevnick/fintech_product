package com.minifin.platform.publicapi

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class PublicApiController(
    private val paymentIntentService: PaymentIntentService,
    private val idempotencyService: IdempotencyService,
    private val objectMapper: ObjectMapper,
) {
    @PostMapping("/v1/payment_intents", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun createPaymentIntent(
        request: HttpServletRequest,
        @RequestHeader(name = "Idempotency-Key", required = false) idempotencyKey: String?,
    ): ResponseEntity<String> {
        val principal = requirePrincipal(request)
        val key = idempotencyService.validateKey(idempotencyKey)
        val rawBody = request.inputStream.readAllBytes().toString(StandardCharsets.UTF_8)
        val route = "/v1/payment_intents"
        val method = "POST"
        val fingerprint = idempotencyService.fingerprint(method, route, rawBody)

        val cached = idempotencyService.runWriteOnce(
            merchantId = principal.merchantId,
            idempotencyKey = key,
            method = method,
            route = route,
            requestFingerprint = fingerprint,
        ) {
            val parsedRequest = parseBody(rawBody)
            val dto = paymentIntentService.create(principal, parsedRequest)
            IdempotentOutcome.of(
                httpStatus = HttpStatus.CREATED.value(),
                body = idempotencyService.writeJson(PublicApiResponse(data = dto)),
            )
        }
        return jsonResponse(cached.httpStatus, cached.body)
    }

    @PostMapping("/v1/payment_intents/{id}/authorize", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun authorizePaymentIntent(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestHeader(name = "Idempotency-Key", required = false) idempotencyKey: String?,
    ): ResponseEntity<String> {
        val principal = requirePrincipal(request)
        val uuid = parsePaymentIntentId(id)
        val key = idempotencyService.validateKey(idempotencyKey)
        val rawBody = request.inputStream.readAllBytes().toString(StandardCharsets.UTF_8)
        val route = "/v1/payment_intents/$id/authorize"
        val fingerprint = idempotencyService.fingerprint("POST", route, rawBody)
        val cached = idempotencyService.runWriteOnce(principal.merchantId, key, "POST", route, fingerprint) {
            val dto = paymentIntentService.authorize(principal, uuid, parseAuthorizeBody(rawBody))
            IdempotentOutcome.of(HttpStatus.OK.value(), idempotencyService.writeJson(PublicApiResponse(data = dto)))
        }
        return jsonResponse(cached.httpStatus, cached.body)
    }

    @PostMapping("/v1/payment_intents/{id}/capture", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun capturePaymentIntent(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestHeader(name = "Idempotency-Key", required = false) idempotencyKey: String?,
    ): ResponseEntity<String> {
        val principal = requirePrincipal(request)
        val uuid = parsePaymentIntentId(id)
        val key = idempotencyService.validateKey(idempotencyKey)
        val rawBody = request.inputStream.readAllBytes().toString(StandardCharsets.UTF_8)
        val route = "/v1/payment_intents/$id/capture"
        val fingerprint = idempotencyService.fingerprint("POST", route, rawBody)
        val cached = idempotencyService.runWriteOnce(principal.merchantId, key, "POST", route, fingerprint) {
            val parsedRequest = parseCaptureBody(rawBody)
            val dto = paymentIntentService.capture(principal, uuid, parsedRequest, key)
            IdempotentOutcome.of(HttpStatus.OK.value(), idempotencyService.writeJson(PublicApiResponse(data = dto)))
        }
        return jsonResponse(cached.httpStatus, cached.body)
    }

    @GetMapping("/v1/payment_intents/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getPaymentIntent(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<PublicApiResponse<PaymentIntentDto>> {
        val principal = requirePrincipal(request)
        val uuid = parsePaymentIntentId(id)
        val dto = paymentIntentService.get(principal, uuid)
        return ResponseEntity.ok(PublicApiResponse(data = dto))
    }

    @ExceptionHandler(PublicApiException::class)
    fun handlePublicApiException(exception: PublicApiException): ResponseEntity<PublicApiResponse<Nothing>> =
        ResponseEntity.status(exception.status).body(
            PublicApiResponse(
                errors = listOf(
                    PublicApiError(
                        code = exception.code,
                        message = exception.message,
                        field = exception.field,
                    ),
                ),
            ),
        )

    private fun requirePrincipal(request: HttpServletRequest): PublicApiPrincipal =
        request.getAttribute(PublicApiAttributes.PRINCIPAL) as? PublicApiPrincipal
            ?: throw PublicApiException(
                code = "unauthenticated",
                message = "API key is required.",
                status = HttpStatus.UNAUTHORIZED,
            )

    private fun parsePaymentIntentId(id: String): UUID = runCatching { UUID.fromString(id) }
        .getOrElse { throw PublicApiException("invalid_payment_intent_id", "Payment intent id is invalid.", HttpStatus.BAD_REQUEST) }

    private fun parseAuthorizeBody(rawBody: String): AuthorizePaymentIntentRequest {
        if (rawBody.isBlank()) throw PublicApiException("invalid_request_body", "Request body is required.", HttpStatus.BAD_REQUEST)
        return runCatching { objectMapper.readValue(rawBody, AuthorizePaymentIntentRequest::class.java) }
            .getOrElse { throw PublicApiException("invalid_request_body", "Request body is invalid JSON.", HttpStatus.BAD_REQUEST) }
    }

    private fun parseCaptureBody(rawBody: String): CapturePaymentIntentRequest {
        if (rawBody.isBlank()) return CapturePaymentIntentRequest()
        return runCatching { objectMapper.readValue(rawBody, CapturePaymentIntentRequest::class.java) }
            .getOrElse { throw PublicApiException("invalid_request_body", "Request body is invalid JSON.", HttpStatus.BAD_REQUEST) }
    }

    private fun parseBody(rawBody: String): CreatePaymentIntentRequest {
        if (rawBody.isBlank()) {
            throw PublicApiException(
                code = "invalid_request_body",
                message = "Request body is required.",
                status = HttpStatus.BAD_REQUEST,
            )
        }
        return runCatching {
            objectMapper.readValue(rawBody, CreatePaymentIntentRequest::class.java)
        }.getOrElse {
            throw PublicApiException(
                code = "invalid_request_body",
                message = "Request body is invalid JSON.",
                status = HttpStatus.BAD_REQUEST,
            )
        }
    }

    private fun jsonResponse(status: Int, body: String): ResponseEntity<String> =
        ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
}
