package com.minifin.platform.merchant.webhooks

import com.fasterxml.jackson.databind.ObjectMapper
import com.minifin.platform.identity.MerchantEmployeeRecord
import com.minifin.platform.merchant.dashboard.MerchantDashboardException
import com.minifin.platform.publicapi.PaymentIntentRecord
import com.minifin.platform.publicapi.toDto
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestTemplate

@Service
class OutboundWebhookService(
    private val repository: OutboundWebhookRepository,
    private val objectMapper: ObjectMapper,
    private val properties: WebhookDeliveryProperties,
) {
    private val restTemplate = RestTemplate(
        SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(2_000)
            setReadTimeout(3_000)
        },
    )

    fun publishPaymentIntentCreated(record: PaymentIntentRecord) {
        val eventId = UUID.randomUUID()
        val payload = linkedMapOf(
            "id" to "evt_$eventId",
            "type" to "payment_intent.created",
            "createdAt" to record.createdAt.toString(),
            "merchantId" to record.merchantId.toString(),
            "data" to mapOf("object" to record.toDto()),
        )
        val inserted = repository.insertEvent(
            id = eventId,
            merchantId = record.merchantId,
            eventType = "payment_intent.created",
            aggregateType = "payment_intent",
            aggregateId = record.id,
            payloadJson = objectMapper.writeValueAsString(payload),
        )
        if (inserted) {
            repository.findEvent(eventId)?.let { deliver(it) }
        }
    }

    fun publishDisputeCreated(
        disputeId: UUID,
        merchantId: UUID,
        paymentIntentId: UUID,
        amount: String,
        currency: String,
        reasonCode: String,
        state: String,
        merchantResponseDeadline: String,
        createdAt: String,
    ) {
        val eventId = UUID.randomUUID()
        val payload = linkedMapOf(
            "id" to "evt_$eventId",
            "type" to "dispute.created",
            "createdAt" to createdAt,
            "merchantId" to merchantId.toString(),
            "data" to mapOf(
                "object" to mapOf(
                    "id" to disputeId.toString(),
                    "object" to "dispute",
                    "paymentIntentId" to paymentIntentId.toString(),
                    "amount" to amount,
                    "currency" to currency,
                    "reasonCode" to reasonCode,
                    "state" to state,
                    "merchantResponseDeadline" to merchantResponseDeadline,
                ),
            ),
        )
        val inserted = repository.insertEvent(
            id = eventId,
            merchantId = merchantId,
            eventType = "dispute.created",
            aggregateType = "dispute",
            aggregateId = disputeId,
            payloadJson = objectMapper.writeValueAsString(payload),
        )
        if (inserted) {
            repository.findEvent(eventId)?.let { deliver(it) }
        }
    }

    fun publishDisputeEvidenceReceived(
        disputeId: UUID,
        merchantId: UUID,
        paymentIntentId: UUID,
        evidenceSubmissionId: UUID,
        state: String,
        createdAt: String,
    ) {
        val eventId = UUID.randomUUID()
        val payload = linkedMapOf(
            "id" to "evt_$eventId",
            "type" to "dispute.evidence_received",
            "createdAt" to createdAt,
            "merchantId" to merchantId.toString(),
            "data" to mapOf(
                "object" to mapOf(
                    "id" to disputeId.toString(),
                    "object" to "dispute",
                    "paymentIntentId" to paymentIntentId.toString(),
                    "state" to state,
                    "evidenceSubmissionId" to evidenceSubmissionId.toString(),
                ),
            ),
        )
        val inserted = repository.insertEvent(
            id = eventId,
            merchantId = merchantId,
            eventType = "dispute.evidence_received",
            aggregateType = "dispute",
            aggregateId = disputeId,
            payloadJson = objectMapper.writeValueAsString(payload),
        )
        if (inserted) {
            repository.findEvent(eventId)?.let { deliver(it) }
        }
    }

    fun publishDisputeWon(
        disputeId: UUID,
        merchantId: UUID,
        paymentIntentId: UUID,
        arbitrationJournalId: UUID,
    ) {
        val eventId = UUID.randomUUID()
        val createdAt = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val payload = linkedMapOf(
            "id" to "evt_$eventId",
            "type" to "dispute.won",
            "createdAt" to createdAt,
            "merchantId" to merchantId.toString(),
            "data" to mapOf(
                "object" to mapOf(
                    "id" to disputeId.toString(),
                    "object" to "dispute",
                    "paymentIntentId" to paymentIntentId.toString(),
                    "state" to "WON",
                    "arbitrationJournalId" to arbitrationJournalId.toString(),
                ),
            ),
        )
        val inserted = repository.insertEvent(
            id = eventId,
            merchantId = merchantId,
            eventType = "dispute.won",
            aggregateType = "dispute",
            aggregateId = disputeId,
            payloadJson = objectMapper.writeValueAsString(payload),
        )
        if (inserted) {
            repository.findEvent(eventId)?.let { deliver(it) }
        }
    }

    fun publishDisputeLost(
        disputeId: UUID,
        merchantId: UUID,
        paymentIntentId: UUID,
        arbitrationJournalId: UUID,
    ) {
        val eventId = UUID.randomUUID()
        val createdAt = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val payload = linkedMapOf(
            "id" to "evt_$eventId",
            "type" to "dispute.lost",
            "createdAt" to createdAt,
            "merchantId" to merchantId.toString(),
            "data" to mapOf(
                "object" to mapOf(
                    "id" to disputeId.toString(),
                    "object" to "dispute",
                    "paymentIntentId" to paymentIntentId.toString(),
                    "state" to "LOST",
                    "arbitrationJournalId" to arbitrationJournalId.toString(),
                ),
            ),
        )
        val inserted = repository.insertEvent(
            id = eventId,
            merchantId = merchantId,
            eventType = "dispute.lost",
            aggregateType = "dispute",
            aggregateId = disputeId,
            payloadJson = objectMapper.writeValueAsString(payload),
        )
        if (inserted) {
            repository.findEvent(eventId)?.let { deliver(it) }
        }
    }

    fun deliver(event: OutboundWebhookEventRecord) {
        deliver(event, manualReplay = false)
    }

    fun deliver(event: OutboundWebhookEventRecord, manualReplay: Boolean): WebhookDeliveryAttemptRecord? {
        val endpoints = repository.activeEndpoints(event.merchantId, event.eventType)
        if (endpoints.isEmpty()) return null
        var delivered = false
        var latestAttempt: WebhookDeliveryAttemptRecord? = null
        endpoints.forEach { endpoint ->
            val attemptNumber = repository.nextAttemptNumber(event.id, endpoint.id)
            val headers = signedHeaders(event, endpoint, attemptNumber)
            val outcome = post(endpoint.url, headers, event.payloadJson)
            val nextRetryAt = if (outcome.succeeded || manualReplay) null else nextRetryAt(attemptNumber, event.maxAttempts)
            repository.insertAttempt(
                id = UUID.randomUUID(),
                eventId = event.id,
                endpointId = endpoint.id,
                attemptNumber = attemptNumber,
                status = if (outcome.succeeded) "SUCCEEDED" else "FAILED",
                httpStatus = outcome.httpStatus,
                responseBodySnippet = outcome.responseBody,
                errorType = outcome.errorType,
                errorMessage = outcome.errorMessage,
                nextRetryAt = nextRetryAt,
                triggerType = if (manualReplay) "MANUAL_REPLAY" else "AUTO",
            )
            latestAttempt = repository.latestAttempt(event.id)
            delivered = delivered || outcome.succeeded
            if (!outcome.succeeded) {
                val record = DeliveryOutcomeRecord(outcome.httpStatus, outcome.errorType ?: "HTTP_${outcome.httpStatus}", outcome.errorMessage ?: outcome.responseBody)
                if (manualReplay) {
                    repository.markDlq(event.id, record)
                } else if (nextRetryAt == null) {
                    repository.markDlq(event.id, record)
                } else {
                    repository.markRetryableFailure(event.id, nextRetryAt, record)
                }
            }
        }
        if (delivered) repository.markEvent(event.id, "DELIVERED")
        return latestAttempt
    }

    fun dispatchDueRetries(limit: Int = 25): Int {
        val events = repository.dueRetryEvents(limit)
        events.forEach { deliver(it) }
        return events.size
    }

    fun listEvents(employee: MerchantEmployeeRecord, status: String?, limit: Int): WebhookEventListResponse {
        requireActive(employee)
        val normalizedStatus = status?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        if (normalizedStatus != null && normalizedStatus !in setOf("PENDING", "DELIVERED", "FAILED", "DLQ")) {
            throw MerchantDashboardException("invalid_status", "Webhook event status filter is invalid.", HttpStatus.BAD_REQUEST, "status")
        }
        return WebhookEventListResponse(repository.listEventsForMerchant(employee.merchantId, normalizedStatus, limit.coerceIn(1, 100)).map { it.toDto() })
    }

    fun detail(employee: MerchantEmployeeRecord, id: UUID): WebhookEventDto {
        requireActive(employee)
        return (repository.findEventForMerchant(id, employee.merchantId)
            ?: throw MerchantDashboardException("webhook_event_not_found", "Webhook event was not found.", HttpStatus.NOT_FOUND)).toDto()
    }

    fun replay(employee: MerchantEmployeeRecord, id: UUID): WebhookReplayResponse {
        requireAdmin(employee)
        val event = repository.findEventForMerchant(id, employee.merchantId)
            ?: throw MerchantDashboardException("webhook_event_not_found", "Webhook event was not found.", HttpStatus.NOT_FOUND)
        if (event.status != "DLQ") {
            throw MerchantDashboardException("invalid_state", "Only DLQ webhook events can be replayed.", HttpStatus.CONFLICT)
        }
        val attempt = deliver(event, manualReplay = true)
        val refreshed = repository.findEventForMerchant(id, employee.merchantId) ?: event
        return WebhookReplayResponse(
            eventId = id.toString(),
            status = refreshed.status,
            replayAttemptNumber = attempt?.attemptNumber,
            httpStatus = attempt?.httpStatus,
        )
    }

    private fun signedHeaders(event: OutboundWebhookEventRecord, endpoint: WebhookEndpointRecord, attemptNumber: Int): HttpHeaders {
        val timestamp = Instant.now().epochSecond.toString()
        val signingSecret = endpoint.signingSecret ?: error("Webhook endpoint ${endpoint.id} has no signing secret available for delivery")
        val signature = hmac(signingSecret, "$timestamp.${event.payloadJson}")
        return HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("MiniFin-Webhook-Id", "evt_${event.id}")
            set("MiniFin-Webhook-Timestamp", timestamp)
            set("MiniFin-Webhook-Signature", "t=$timestamp,v1=$signature")
            set("MiniFin-Webhook-Event", event.eventType)
            set("MiniFin-Webhook-Attempt", attemptNumber.toString())
        }
    }

    private fun post(url: String, headers: HttpHeaders, body: String): DeliveryOutcome =
        try {
            val response = restTemplate.postForEntity(url, HttpEntity(body, headers), String::class.java)
            DeliveryOutcome(
                succeeded = response.statusCode.is2xxSuccessful,
                httpStatus = response.statusCode.value(),
                responseBody = response.body,
            )
        } catch (e: RestClientResponseException) {
            DeliveryOutcome(false, e.statusCode.value(), e.responseBodyAsString, e.javaClass.simpleName, e.message)
        } catch (e: RestClientException) {
            DeliveryOutcome(false, null, null, e.javaClass.simpleName, e.message)
        }

    private fun nextRetryAt(attemptNumber: Int, maxAttempts: Int): OffsetDateTime? {
        if (attemptNumber >= maxAttempts) return null
        val delay = properties.retryDelaysSeconds.getOrElse(attemptNumber - 1) { properties.retryDelaysSeconds.lastOrNull() ?: 1 }
        return OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(delay)
    }

    private fun hmac(secret: String, payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(payload.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun requireActive(employee: MerchantEmployeeRecord) {
        if (employee.status != "ACTIVE") throw MerchantDashboardException("merchant_employee_not_active", "Merchant employee is not active.", HttpStatus.FORBIDDEN)
    }

    private fun requireAdmin(employee: MerchantEmployeeRecord) {
        requireActive(employee)
        if (employee.role != "merchant_admin") throw MerchantDashboardException("forbidden_role", "merchant_admin role is required to replay webhook events.", HttpStatus.FORBIDDEN)
    }

    private fun OutboundWebhookEventRecord.toDto(): WebhookEventDto = WebhookEventDto(
        id = id.toString(),
        type = eventType,
        aggregateType = aggregateType,
        aggregateId = aggregateId.toString(),
        status = status,
        createdAt = createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        retryCount = retryCount,
        maxAttempts = maxAttempts,
        nextRetryAt = nextRetryAt?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        lastAttemptAt = lastAttemptAt?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        lastErrorType = lastErrorType,
        lastErrorMessage = lastErrorMessage,
        lastHttpStatus = lastHttpStatus,
        dlqAt = dlqAt?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    )

    private data class DeliveryOutcome(
        val succeeded: Boolean,
        val httpStatus: Int?,
        val responseBody: String?,
        val errorType: String? = null,
        val errorMessage: String? = null,
    )
}
