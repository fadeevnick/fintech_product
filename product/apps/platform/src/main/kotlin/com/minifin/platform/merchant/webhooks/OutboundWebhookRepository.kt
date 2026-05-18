package com.minifin.platform.merchant.webhooks

import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class OutboundWebhookRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val properties: WebhookDeliveryProperties,
) {
    fun insertEvent(id: UUID, merchantId: UUID, eventType: String, aggregateType: String, aggregateId: UUID, payloadJson: String): Boolean =
        jdbcTemplate.update(
            """
            insert into merchant.webhook_events (id, merchant_id, event_type, aggregate_type, aggregate_id, payload, status, max_attempts)
            values (?, ?, ?, ?, ?, ?::jsonb, 'PENDING', ?)
            on conflict (event_type, aggregate_type, aggregate_id) do nothing
            """.trimIndent(),
            id,
            merchantId,
            eventType,
            aggregateType,
            aggregateId,
            payloadJson,
            properties.maxAttempts,
        ) == 1

    fun findEvent(id: UUID): OutboundWebhookEventRecord? =
        jdbcTemplate.query(
            eventSelectSql("where id = ?"),
            { rs, _ -> rs.toEventRecord() },
            id,
        ).firstOrNull()

    fun listEventsForMerchant(merchantId: UUID, status: String?, limit: Int): List<OutboundWebhookEventRecord> =
        jdbcTemplate.query(
            eventSelectSql(
                """
                where merchant_id = ?
                  and (? is null or status = ?)
                order by created_at desc
                limit ?
                """.trimIndent(),
            ),
            { rs, _ -> rs.toEventRecord() },
            merchantId,
            status,
            status,
            limit,
        )

    fun findEventForMerchant(id: UUID, merchantId: UUID): OutboundWebhookEventRecord? =
        jdbcTemplate.query(
            eventSelectSql("where id = ? and merchant_id = ?"),
            { rs, _ -> rs.toEventRecord() },
            id,
            merchantId,
        ).firstOrNull()

    fun dueRetryEvents(limit: Int): List<OutboundWebhookEventRecord> =
        jdbcTemplate.query(
            eventSelectSql(
                """
                where status = 'FAILED'
                  and next_retry_at is not null
                  and next_retry_at <= now()
                order by next_retry_at asc, created_at asc
                limit ?
                """.trimIndent(),
            ),
            { rs, _ -> rs.toEventRecord() },
            limit,
        )

    fun activeEndpoints(merchantId: UUID, eventType: String): List<WebhookEndpointRecord> =
        jdbcTemplate.query(
            """
            select id, merchant_id, url, enabled_events::text as enabled_events_json, status, description,
                   signing_secret_hash,
                   pgp_sym_decrypt(signing_secret_ciphertext, ?) as signing_secret,
                   secret_prefix, created_at, updated_at, deleted_at
            from merchant.webhook_endpoints
            where merchant_id = ?
              and status = 'ACTIVE'
              and signing_secret_ciphertext is not null
              and jsonb_exists(enabled_events, ?)
            order by created_at asc
            """.trimIndent(),
            { rs, _ -> rs.toEndpointRecord() },
            properties.signingSecretEncryptionKey,
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
        nextRetryAt: OffsetDateTime?,
        triggerType: String = "AUTO",
    ) {
        jdbcTemplate.update(
            """
            insert into merchant.webhook_delivery_attempts (
                id, event_id, endpoint_id, attempt_number, status, http_status,
                response_body_snippet, error_type, error_message, next_retry_at, trigger_type
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            nextRetryAt,
            triggerType,
        )
    }

    fun markEvent(id: UUID, status: String) {
        jdbcTemplate.update(
            """
            update merchant.webhook_events
            set status = ?,
                delivered_at = case when ? = 'DELIVERED' then now() else delivered_at end,
                next_retry_at = case when ? = 'DELIVERED' then null else next_retry_at end,
                last_attempt_at = case when ? = 'DELIVERED' then now() else last_attempt_at end,
                last_http_status = case when ? = 'DELIVERED' then null else last_http_status end,
                last_error_type = case when ? = 'DELIVERED' then null else last_error_type end,
                last_error_message = case when ? = 'DELIVERED' then null else last_error_message end,
                dlq_at = case when ? = 'DELIVERED' then null else dlq_at end
            where id = ?
            """.trimIndent(),
            status,
            status,
            status,
            status,
            status,
            status,
            status,
            status,
            id,
        )
    }

    fun markRetryableFailure(id: UUID, nextRetryAt: OffsetDateTime, outcome: DeliveryOutcomeRecord) {
        jdbcTemplate.update(
            """
            update merchant.webhook_events
            set status = 'FAILED',
                retry_count = retry_count + 1,
                next_retry_at = ?,
                last_attempt_at = now(),
                last_http_status = ?,
                last_error_type = ?,
                last_error_message = ?,
                dlq_at = null
            where id = ?
            """.trimIndent(),
            nextRetryAt,
            outcome.httpStatus,
            outcome.errorType?.take(120),
            outcome.errorMessage?.take(500),
            id,
        )
    }

    fun markDlq(id: UUID, outcome: DeliveryOutcomeRecord) {
        jdbcTemplate.update(
            """
            update merchant.webhook_events
            set status = 'DLQ',
                retry_count = retry_count + 1,
                next_retry_at = null,
                last_attempt_at = now(),
                last_http_status = ?,
                last_error_type = ?,
                last_error_message = ?,
                dlq_at = now()
            where id = ?
            """.trimIndent(),
            outcome.httpStatus,
            outcome.errorType?.take(120),
            outcome.errorMessage?.take(500),
            id,
        )
    }

    fun latestAttempt(eventId: UUID): WebhookDeliveryAttemptRecord? =
        jdbcTemplate.query(
            """
            select id, event_id, endpoint_id, attempt_number, status, http_status,
                   response_body_snippet, error_type, error_message, attempted_at, next_retry_at, trigger_type
            from merchant.webhook_delivery_attempts
            where event_id = ?
            order by attempted_at desc, attempt_number desc
            limit 1
            """.trimIndent(),
            { rs, _ -> rs.toAttemptRecord() },
            eventId,
        ).firstOrNull()

    private fun eventSelectSql(whereClause: String): String =
        """
        select id, merchant_id, event_type, aggregate_type, aggregate_id, payload::text as payload_json, status, created_at,
               retry_count, max_attempts, next_retry_at, last_attempt_at, last_error_type, last_error_message, last_http_status, dlq_at
        from merchant.webhook_events
        $whereClause
        """.trimIndent()

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
            retryCount = getInt("retry_count"),
            maxAttempts = getInt("max_attempts"),
            nextRetryAt = getObject("next_retry_at", OffsetDateTime::class.java),
            lastAttemptAt = getObject("last_attempt_at", OffsetDateTime::class.java),
            lastErrorType = getString("last_error_type"),
            lastErrorMessage = getString("last_error_message"),
            lastHttpStatus = getNullableInt("last_http_status"),
            dlqAt = getObject("dlq_at", OffsetDateTime::class.java),
        )

    private fun ResultSet.getNullableInt(column: String): Int? {
        val value = getInt(column)
        return if (wasNull()) null else value
    }

    private fun ResultSet.toEndpointRecord(): WebhookEndpointRecord =
        WebhookEndpointRecord(
            id = getObject("id", UUID::class.java),
            merchantId = getObject("merchant_id", UUID::class.java),
            url = getString("url"),
            enabledEventsJson = getString("enabled_events_json"),
            status = getString("status"),
            description = getString("description"),
            signingSecretHash = getString("signing_secret_hash"),
            signingSecret = getString("signing_secret"),
            secretPrefix = getString("secret_prefix"),
            createdAt = getObject("created_at", OffsetDateTime::class.java),
            updatedAt = getObject("updated_at", OffsetDateTime::class.java),
            deletedAt = getObject("deleted_at", OffsetDateTime::class.java),
        )

    private fun ResultSet.toAttemptRecord(): WebhookDeliveryAttemptRecord =
        WebhookDeliveryAttemptRecord(
            id = getObject("id", UUID::class.java),
            eventId = getObject("event_id", UUID::class.java),
            endpointId = getObject("endpoint_id", UUID::class.java),
            attemptNumber = getInt("attempt_number"),
            status = getString("status"),
            httpStatus = getNullableInt("http_status"),
            responseBodySnippet = getString("response_body_snippet"),
            errorType = getString("error_type"),
            errorMessage = getString("error_message"),
            attemptedAt = getObject("attempted_at", OffsetDateTime::class.java),
            nextRetryAt = getObject("next_retry_at", OffsetDateTime::class.java),
            triggerType = getString("trigger_type"),
        )
}

data class DeliveryOutcomeRecord(
    val httpStatus: Int?,
    val errorType: String?,
    val errorMessage: String?,
)

data class WebhookDeliveryAttemptRecord(
    val id: UUID,
    val eventId: UUID,
    val endpointId: UUID,
    val attemptNumber: Int,
    val status: String,
    val httpStatus: Int?,
    val responseBodySnippet: String?,
    val errorType: String?,
    val errorMessage: String?,
    val attemptedAt: OffsetDateTime,
    val nextRetryAt: OffsetDateTime?,
    val triggerType: String,
)
