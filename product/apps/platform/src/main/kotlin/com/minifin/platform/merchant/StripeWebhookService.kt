package com.minifin.platform.merchant

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.minifin.platform.identity.AuditRepository
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

class StripeWebhookException(
    val code: String,
    override val message: String,
    val status: HttpStatus,
) : RuntimeException(message)

data class StripeWebhookProcessResult(
    val eventId: String?,
    val eventType: String?,
    val outcome: String,
)

private const val ACCOUNT_UPDATED = "account.updated"

@Service
class StripeWebhookService(
    private val signatureVerifier: StripeWebhookSignatureVerifier,
    private val processor: StripeWebhookProcessor,
    private val repository: MerchantStripeRepository,
    private val auditRepository: AuditRepository,
    private val objectMapper: ObjectMapper,
) {
    fun handle(rawBody: ByteArray, signatureHeader: String?): StripeWebhookProcessResult {
        val verified = signatureVerifier.verify(rawBody, signatureHeader)
        when (verified) {
            is StripeWebhookSignatureVerifier.Outcome.Valid -> Unit
            StripeWebhookSignatureVerifier.Outcome.MissingHeader -> rejectAndThrow(
                rawBody, signatureHeader,
                outcome = "REJECTED_SIGNATURE",
                auditEvent = "stripe.webhook_signature_missing",
                code = "stripe_webhook_signature_missing",
                message = "Stripe-Signature header is required.",
            )
            StripeWebhookSignatureVerifier.Outcome.MissingTimestamp,
            StripeWebhookSignatureVerifier.Outcome.MissingSignature,
            StripeWebhookSignatureVerifier.Outcome.SignatureInvalid -> rejectAndThrow(
                rawBody, signatureHeader,
                outcome = "REJECTED_SIGNATURE",
                auditEvent = "stripe.webhook_signature_invalid",
                code = "stripe_webhook_signature_invalid",
                message = "Stripe webhook signature did not match.",
            )
            StripeWebhookSignatureVerifier.Outcome.TimestampOutsideTolerance -> rejectAndThrow(
                rawBody, signatureHeader,
                outcome = "REJECTED_TIMESTAMP",
                auditEvent = "stripe.webhook_timestamp_outside_tolerance",
                code = "stripe_webhook_timestamp_outside_tolerance",
                message = "Stripe webhook timestamp is outside the allowed tolerance window.",
            )
        }

        val payload = try {
            objectMapper.readTree(rawBody)
        } catch (_: Exception) {
            rejectAndThrow(
                rawBody, signatureHeader,
                outcome = "REJECTED_PAYLOAD",
                auditEvent = "stripe.webhook_payload_invalid",
                code = "stripe_webhook_payload_invalid",
                message = "Stripe webhook payload is not valid JSON.",
            )
        }
        val eventId = payload.path("id").takeIf { it.isTextual }?.asText()
        val eventType = payload.path("type").takeIf { it.isTextual }?.asText()
        if (eventId.isNullOrBlank() || eventType.isNullOrBlank()) {
            rejectAndThrow(
                rawBody, signatureHeader,
                outcome = "REJECTED_PAYLOAD",
                auditEvent = "stripe.webhook_payload_invalid",
                code = "stripe_webhook_payload_invalid",
                message = "Stripe webhook payload is missing required fields.",
            )
        }
        return processor.processVerifiedEvent(rawBody, signatureHeader, payload, eventId!!, eventType!!)
    }

    private fun rejectAndThrow(
        rawBody: ByteArray,
        signatureHeader: String?,
        outcome: String,
        auditEvent: String,
        code: String,
        message: String,
    ): Nothing {
        val (eventId, eventType) = bestEffortIdentifiers(rawBody)
        val payloadJson = String(rawBody, Charsets.UTF_8)
        val safePayloadJson = if (looksLikeJson(payloadJson)) payloadJson
            else """{"raw":${objectMapper.writeValueAsString(payloadJson)}}"""
        repository.insertRejectionRow(
            id = UUID.randomUUID(),
            stripeEventId = eventId ?: "rejected:${UUID.randomUUID()}",
            eventType = eventType ?: "unknown",
            outcome = outcome,
            payloadJson = safePayloadJson,
            signatureHeader = signatureHeader,
        )
        auditRepository.write(
            eventType = auditEvent,
            actorType = "VENDOR_STRIPE",
            actorId = null,
            subjectType = "STRIPE_WEBHOOK_EVENT",
            subjectId = null,
            outcome = "FAILURE",
            metadataJson = stableJson(
                "stripeEventId" to (eventId ?: "null"),
                "eventType" to (eventType ?: "null"),
                "outcome" to outcome,
            ),
        )
        throw StripeWebhookException(code, message, HttpStatus.BAD_REQUEST)
    }

    private fun bestEffortIdentifiers(rawBody: ByteArray): Pair<String?, String?> = try {
        val node = objectMapper.readTree(rawBody)
        val id = node.path("id").takeIf { it.isTextual }?.asText()
        val type = node.path("type").takeIf { it.isTextual }?.asText()
        id to type
    } catch (_: Exception) {
        null to null
    }

    private fun looksLikeJson(s: String): Boolean {
        val t = s.trim()
        return t.startsWith("{") || t.startsWith("[")
    }

    private fun stableJson(vararg pairs: Pair<String, String>): String =
        objectMapper.writeValueAsString(linkedMapOf(*pairs))
}

@Service
class StripeWebhookProcessor(
    private val repository: MerchantStripeRepository,
    private val auditRepository: AuditRepository,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun processVerifiedEvent(
        rawBody: ByteArray,
        signatureHeader: String?,
        payload: JsonNode,
        eventId: String,
        eventType: String,
    ): StripeWebhookProcessResult {
        val payloadJson = String(rawBody, Charsets.UTF_8)
        val planned = planOutcome(payload, eventType)
        val inserted = repository.insertEventRow(
            id = UUID.randomUUID(),
            stripeEventId = eventId,
            eventType = eventType,
            outcome = planned.outcome,
            payloadJson = payloadJson,
            signatureHeader = signatureHeader,
            processedAt = OffsetDateTime.now(),
        )
        if (!inserted) {
            auditRepository.write(
                eventType = "stripe.webhook_duplicate",
                actorType = "VENDOR_STRIPE",
                actorId = null,
                subjectType = "STRIPE_WEBHOOK_EVENT",
                subjectId = null,
                outcome = "SUCCESS",
                metadataJson = stableJson(
                    "stripeEventId" to eventId,
                    "eventType" to eventType,
                ),
            )
            return StripeWebhookProcessResult(eventId, eventType, "DUPLICATE")
        }
        when (planned) {
            is PlannedOutcome.AccountUpdated -> applyAccountUpdated(eventId, planned)
            PlannedOutcome.IgnoredUnknownAccount -> auditRepository.write(
                eventType = "stripe.webhook_ignored_unknown_account",
                actorType = "VENDOR_STRIPE",
                actorId = null,
                subjectType = "STRIPE_WEBHOOK_EVENT",
                subjectId = null,
                outcome = "SUCCESS",
                metadataJson = stableJson("stripeEventId" to eventId, "eventType" to eventType),
            )
            PlannedOutcome.IgnoredUnhandledType -> auditRepository.write(
                eventType = "stripe.webhook_ignored_unhandled_type",
                actorType = "VENDOR_STRIPE",
                actorId = null,
                subjectType = "STRIPE_WEBHOOK_EVENT",
                subjectId = null,
                outcome = "SUCCESS",
                metadataJson = stableJson("stripeEventId" to eventId, "eventType" to eventType),
            )
        }
        return StripeWebhookProcessResult(eventId, eventType, planned.outcome)
    }

    private fun applyAccountUpdated(eventId: String, planned: PlannedOutcome.AccountUpdated) {
        val link = planned.link
        repository.updateAccountLinkFromAccountUpdated(
            link = link,
            chargesEnabled = planned.chargesEnabled,
            payoutsEnabled = planned.payoutsEnabled,
            detailsSubmitted = planned.detailsSubmitted,
            lastEventId = eventId,
        )
        val previous = repository.currentMerchantKybStatus(link.merchantId)
        val next = computeKybStatus(
            previous = previous,
            chargesEnabled = planned.chargesEnabled,
            payoutsEnabled = planned.payoutsEnabled,
            detailsSubmitted = planned.detailsSubmitted,
        )
        if (next != null && next != previous) {
            repository.updateMerchantKybStatus(link.merchantId, next)
        }
        auditRepository.write(
            eventType = "stripe.account_updated",
            actorType = "VENDOR_STRIPE",
            actorId = null,
            subjectType = "MERCHANT",
            subjectId = link.merchantId,
            outcome = "SUCCESS",
            metadataJson = stableJson(
                "stripeEventId" to eventId,
                "stripeAccountId" to link.stripeAccountId,
                "chargesEnabled" to planned.chargesEnabled.toString(),
                "payoutsEnabled" to planned.payoutsEnabled.toString(),
                "detailsSubmitted" to planned.detailsSubmitted.toString(),
                "previousKybStatus" to (previous ?: "null"),
                "newKybStatus" to (next ?: previous ?: "null"),
            ),
        )
    }

    private fun planOutcome(payload: JsonNode, eventType: String): PlannedOutcome {
        if (eventType != ACCOUNT_UPDATED) return PlannedOutcome.IgnoredUnhandledType
        val obj = payload.path("data").path("object")
        val stripeAccountId = obj.path("id").takeIf { it.isTextual }?.asText()
            ?: return PlannedOutcome.IgnoredUnhandledType
        val link = repository.findAccountLinkByStripeAccountId(stripeAccountId)
            ?: return PlannedOutcome.IgnoredUnknownAccount
        return PlannedOutcome.AccountUpdated(
            link = link,
            chargesEnabled = obj.path("charges_enabled").asBoolean(false),
            payoutsEnabled = obj.path("payouts_enabled").asBoolean(false),
            detailsSubmitted = obj.path("details_submitted").asBoolean(false),
        )
    }

    private fun computeKybStatus(
        previous: String?,
        chargesEnabled: Boolean,
        payoutsEnabled: Boolean,
        detailsSubmitted: Boolean,
    ): String? {
        if (chargesEnabled && payoutsEnabled) return "VERIFIED"
        if (detailsSubmitted) return "PENDING"
        return previous
    }

    private fun stableJson(vararg pairs: Pair<String, String>): String =
        objectMapper.writeValueAsString(linkedMapOf(*pairs))

    private sealed class PlannedOutcome(val outcome: String) {
        data object IgnoredUnhandledType : PlannedOutcome("IGNORED_UNHANDLED_TYPE")
        data object IgnoredUnknownAccount : PlannedOutcome("IGNORED_UNKNOWN_ACCOUNT")
        data class AccountUpdated(
            val link: StripeAccountLinkRecord,
            val chargesEnabled: Boolean,
            val payoutsEnabled: Boolean,
            val detailsSubmitted: Boolean,
        ) : PlannedOutcome("PROCESSED")
    }
}
