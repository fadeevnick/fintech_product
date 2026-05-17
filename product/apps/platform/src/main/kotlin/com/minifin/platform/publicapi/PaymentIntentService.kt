package com.minifin.platform.publicapi

import com.fasterxml.jackson.annotation.JsonInclude
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class CreatePaymentIntentRequest(
    val amount: String? = null,
    val currency: String? = null,
    val description: String? = null,
)

data class AuthorizePaymentIntentRequest(
    val cardToken: String? = null,
    val amount: String? = null,
    val currency: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PaymentAuthorizationDto(
    val id: String? = null,
    val status: String,
    val authCode: String? = null,
    val expiresAt: String? = null,
    val declineCode: String? = null,
    val declineMessage: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PaymentIntentDto(
    val id: String,
    val `object`: String,
    val amount: String,
    val currency: String,
    val state: String,
    val description: String?,
    val createdAt: String,
    val authorization: PaymentAuthorizationDto? = null,
)

@Service
class PaymentIntentService(
    private val repository: PaymentIntentRepository,
    private val properties: com.minifin.platform.cards.CardProperties,
) {
    private val restTemplate = org.springframework.web.client.RestTemplate()
    @Transactional
    fun create(principal: PublicApiPrincipal, request: CreatePaymentIntentRequest): PaymentIntentDto {
        val amount = validateAmount(request.amount)
        val currency = validateCurrency(request.currency)
        val description = request.description?.trim()?.takeIf { it.isNotEmpty() }?.also {
            if (it.length > 500) {
                throw PublicApiException(
                    code = "invalid_description",
                    message = "Description must be at most 500 characters.",
                    status = HttpStatus.BAD_REQUEST,
                    field = "description",
                )
            }
        }
        val id = UUID.randomUUID()
        repository.insert(
            id = id,
            merchantId = principal.merchantId,
            apiKeyId = principal.apiKeyId,
            amount = amount,
            currency = currency,
            description = description,
        )
        val record = repository.findById(id) ?: error("Payment intent disappeared after insert")
        return record.toDto()
    }


    @Transactional
    fun authorize(principal: PublicApiPrincipal, id: UUID, request: AuthorizePaymentIntentRequest): PaymentIntentDto {
        val record = repository.findById(id) ?: throw PublicApiException("payment_intent_not_found", "Payment intent was not found.", HttpStatus.NOT_FOUND)
        if (record.merchantId != principal.merchantId) throw PublicApiException("payment_intent_not_found", "Payment intent was not found.", HttpStatus.NOT_FOUND)
        val cardToken = request.cardToken?.trim()?.takeIf { it.isNotEmpty() } ?: throw PublicApiException("invalid_card_token", "Card token is required.", HttpStatus.BAD_REQUEST, "cardToken")
        val amount = request.amount?.let { validateAmount(it) } ?: record.amount
        val currency = validateCurrency(request.currency ?: record.currency)
        val acquirer = callAcquirer(record.id, principal.merchantId, amount, currency, cardToken)
        repository.markAuthorizedResult(record.id, cardToken, acquirer.authorizationId?.let { UUID.fromString(it) }, acquirer.authCode, acquirer.declineCode, acquirer.declineMessage, if (acquirer.state == "AUTHORIZED") "AUTHORIZED" else "FAILED")
        return repository.findById(record.id)!!.toDto(acquirer.toAuthorizationDto())
    }

    private fun callAcquirer(paymentIntentId: UUID, merchantId: UUID, amount: BigDecimal, currency: String, cardToken: String): AcquirerAuthorizeResponse {
        val headers = HttpHeaders(); headers.set("X-Service-Name", "platform"); headers.set("X-Service-Secret", properties.serviceAuthSecret)
        val body = AcquirerAuthorizeRequest(paymentIntentId.toString(), merchantId.toString(), amount.toPlainString(), currency, cardToken, paymentIntentId.toString(), paymentIntentId.toString())
        val response = runCatching { restTemplate.exchange("${properties.acquirerBaseUrl}/internal/acquirer/authorize", HttpMethod.POST, HttpEntity(body, headers), AcquirerApiResponse::class.java) }
            .getOrElse { throw PublicApiException("network_unavailable", "Authorization network is unavailable.", HttpStatus.BAD_GATEWAY) }
        return response.body?.data ?: throw PublicApiException("network_unavailable", "Authorization network returned no response.", HttpStatus.BAD_GATEWAY)
    }

    @Transactional(readOnly = true)
    fun get(principal: PublicApiPrincipal, id: UUID): PaymentIntentDto {
        val record = repository.findById(id)
            ?: throw PublicApiException(
                code = "payment_intent_not_found",
                message = "Payment intent was not found.",
                status = HttpStatus.NOT_FOUND,
            )
        if (record.merchantId != principal.merchantId) {
            throw PublicApiException(
                code = "payment_intent_not_found",
                message = "Payment intent was not found.",
                status = HttpStatus.NOT_FOUND,
            )
        }
        return record.toDto()
    }

    private fun validateAmount(value: String?): BigDecimal {
        if (value.isNullOrBlank()) {
            throw PublicApiException(
                code = "invalid_amount",
                message = "Amount is required.",
                status = HttpStatus.BAD_REQUEST,
                field = "amount",
            )
        }
        val amount = runCatching { BigDecimal(value.trim()).setScale(4, RoundingMode.UNNECESSARY) }
            .getOrElse {
                throw PublicApiException(
                    code = "invalid_amount",
                    message = "Amount is invalid.",
                    status = HttpStatus.BAD_REQUEST,
                    field = "amount",
                )
            }
        if (amount <= BigDecimal.ZERO) {
            throw PublicApiException(
                code = "invalid_amount",
                message = "Amount must be positive.",
                status = HttpStatus.BAD_REQUEST,
                field = "amount",
            )
        }
        return amount
    }

    private fun validateCurrency(value: String?): String {
        val currency = (value ?: "EUR").trim().uppercase()
        if (currency != "EUR") {
            throw PublicApiException(
                code = "unsupported_currency",
                message = "Only EUR is supported.",
                status = HttpStatus.BAD_REQUEST,
                field = "currency",
            )
        }
        return currency
    }
}

fun PaymentIntentRecord.toDto(authorization: PaymentAuthorizationDto? = null): PaymentIntentDto =
    PaymentIntentDto(
        id = id.toString(),
        `object` = "payment_intent",
        amount = amount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
        currency = currency,
        state = state,
        description = description,
        createdAt = createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        authorization = authorization ?: declineAuthorization(),
    )

private fun PaymentIntentRecord.declineAuthorization(): PaymentAuthorizationDto? = when (state) {
    "AUTHORIZED" -> PaymentAuthorizationDto(authorizationId?.toString(), "AUTH_APPROVED", authCode)
    "FAILED" -> PaymentAuthorizationDto(status = "AUTH_DECLINED", declineCode = declineCode, declineMessage = declineMessage)
    else -> null
}

data class AcquirerAuthorizeRequest(val paymentIntentId: String, val merchantId: String, val amount: String, val currency: String, val cardToken: String, val requestId: String? = null, val correlationId: String? = null)
data class AcquirerAuthorizeResponse(val paymentIntentId: String? = null, val state: String? = null, val status: String? = null, val authorizationId: String? = null, val authCode: String? = null, val expiresAt: String? = null, val declineCode: String? = null, val declineMessage: String? = null, val routeId: String? = null)
data class AcquirerApiResponse(val data: AcquirerAuthorizeResponse? = null, val errors: List<PublicApiError> = emptyList())
private fun AcquirerAuthorizeResponse.toAuthorizationDto(): PaymentAuthorizationDto = if (status == "AUTH_APPROVED") PaymentAuthorizationDto(authorizationId, "AUTH_APPROVED", authCode, expiresAt) else PaymentAuthorizationDto(status = "AUTH_DECLINED", declineCode = declineCode, declineMessage = declineMessage)
