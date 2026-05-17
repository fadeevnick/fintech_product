package com.minifin.platform.publicapi

import com.fasterxml.jackson.annotation.JsonInclude
import com.minifin.platform.merchant.webhooks.OutboundWebhookService
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class CreatePaymentIntentRequest(
    val amount: String? = null,
    val currency: String? = null,
    val description: String? = null,
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
)

@Service
class PaymentIntentService(
    private val repository: PaymentIntentRepository,
    private val outboundWebhookService: OutboundWebhookService,
) {
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
        outboundWebhookService.publishPaymentIntentCreated(record)
        return record.toDto()
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

fun PaymentIntentRecord.toDto(): PaymentIntentDto =
    PaymentIntentDto(
        id = id.toString(),
        `object` = "payment_intent",
        amount = amount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
        currency = currency,
        state = state,
        description = description,
        createdAt = createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    )
