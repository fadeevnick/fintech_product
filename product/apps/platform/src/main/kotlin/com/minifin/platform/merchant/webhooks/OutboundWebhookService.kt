package com.minifin.platform.merchant.webhooks

import com.fasterxml.jackson.databind.ObjectMapper
import com.minifin.platform.publicapi.PaymentIntentRecord
import com.minifin.platform.publicapi.toDto
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
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

    fun deliver(event: OutboundWebhookEventRecord) {
        val endpoints = repository.activeEndpoints(event.merchantId, event.eventType)
        if (endpoints.isEmpty()) return
        var delivered = false
        endpoints.forEach { endpoint ->
            val attemptNumber = repository.nextAttemptNumber(event.id, endpoint.id)
            val headers = signedHeaders(event, endpoint, attemptNumber)
            val outcome = post(endpoint.url, headers, event.payloadJson)
            val nextRetryAt = if (outcome.succeeded) null else nextRetryAt(attemptNumber, event.maxAttempts)
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
            )
            delivered = delivered || outcome.succeeded
            if (!outcome.succeeded) {
                val record = DeliveryOutcomeRecord(outcome.httpStatus, outcome.errorType ?: "HTTP_${outcome.httpStatus}", outcome.errorMessage ?: outcome.responseBody)
                if (nextRetryAt == null) {
                    repository.markDlq(event.id, record)
                } else {
                    repository.markRetryableFailure(event.id, nextRetryAt, record)
                }
            }
        }
        if (delivered) repository.markEvent(event.id, "DELIVERED")
    }

    fun dispatchDueRetries(limit: Int = 25): Int {
        val events = repository.dueRetryEvents(limit)
        events.forEach { deliver(it) }
        return events.size
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

    private data class DeliveryOutcome(
        val succeeded: Boolean,
        val httpStatus: Int?,
        val responseBody: String?,
        val errorType: String? = null,
        val errorMessage: String? = null,
    )
}
