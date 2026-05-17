package com.minifin.platform.merchant.webhooks

import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class OutboundWebhookRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun insertEvent(id: UUID, merchantId: UUID, eventType: String, aggregateType: String, aggregateId: UUID, payloadJson: String): Boolean =
        jdbcTemplate.update(
            """
            insert into merchant.webhook_events (id, merchant_id, event_type, aggregate_type, aggregate_id, payload, status)
            values (?, ?, ?, ?, ?, ?::jsonb, 'PENDING')
            on conflict (event_type, aggregate_type, aggregate_id) do nothing
            """.trimIndent(),
            id,
            merchantId,
            eventType,
            aggregateType,
            aggregateId,
            payloadJson,
        ) == 1

    fun findEvent(id: UUID): OutboundWebhookEventRecord? =
        jdbcTemplate.query(
            """
            select id, merchant_id, event_type, aggregate_type, aggregate_id, payload::text as payload_json, status, created_at
            from merchant.webhook_events
            where id = ?
            """.trimIndent(),
            { rs, _ -> rs.toEventRecord() },
            id,
        ).firstOrNull()

    fun activeEndpoints(merchantId: UUID, eventType: String): List<WebhookEndpointRecord> =
        jdbcTemplate.query(
            """
            select id, merchant_id, url, enabled_events::text as enabled_events_json, status, description, signing_secret_hash, secret_prefix, created_at, updated_at, deleted_at
            from merchant.webhook_endpoints
            where merchant_id = ?
              and status = 'ACTIVE'
              and jsonb_exists(enabled_events, ?)
            order by created_at asc
            """.trimIndent(),
            { rs, _ -> rs.toEndpointRecord() },
            merchantId,
            eventType,
        )

    fun nextAttemptNumber(eventId: UUID, endpointId: UUID): Int =
        jdbcTemplate.queryForObject(
            """
            select coalesce(max(attempt_number), 0) + 1
            from merchant.webhook_delivery_attempts
            where event_id = ? and endpoint_id = ?
            """.trimIndent(),
            Int::class.java,
            eventId,
            endpointId,
        ) ?: 1

    fun insertAttempt(
        id: UUID,
        eventId: UUID,
        endpointId: UUID,
        attemptNumber: Int,
        status: String,
        httpStatus: Int?,
        responseBodySnippet: String?,
        errorType: String?,
        errorMessage: String?,
    ) {
        jdbcTemplate.update(
            """
            insert into merchant.webhook_delivery_attempts (
                id, event_id, endpoint_id, attempt_number, status, http_status,
                response_body_snippet, error_type, error_message
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            eventId,
            endpointId,
            attemptNumber,
            status,
            httpStatus,
            responseBodySnippet?.take(1000),
            errorType?.take(120),
            errorMessage?.take(500),
        )
    }

    fun markEvent(id: UUID, status: String) {
        jdbcTemplate.update(
            """
            update merchant.webhook_events
            set status = ?, delivered_at = case when ? = 'DELIVERED' then now() else delivered_at end
            where id = ?
            """.trimIndent(),
            status,
            status,
            id,
        )
    }

    private fun ResultSet.toEventRecord(): OutboundWebhookEventRecord =
        OutboundWebhookEventRecord(
            id = getObject("id", UUID::class.java),
            merchantId = getObject("merchant_id", UUID::class.java),
            eventType = getString("event_type"),
            aggregateType = getString("aggregate_type"),
            aggregateId = getObject("aggregate_id", UUID::class.java),
            payloadJson = getString("payload_json"),
            status = getString("status"),
            createdAt = getObject("created_at", OffsetDateTime::class.java),
        )

    private fun ResultSet.toEndpointRecord(): WebhookEndpointRecord =
        WebhookEndpointRecord(
            id = getObject("id", UUID::class.java),
            merchantId = getObject("merchant_id", UUID::class.java),
            url = getString("url"),
            enabledEventsJson = getString("enabled_events_json"),
            status = getString("status"),
            description = getString("description"),
            signingSecretHash = getString("signing_secret_hash"),
            secretPrefix = getString("secret_prefix"),
            createdAt = getObject("created_at", OffsetDateTime::class.java),
            updatedAt = getObject("updated_at", OffsetDateTime::class.java),
            deletedAt = getObject("deleted_at", OffsetDateTime::class.java),
        )
}
