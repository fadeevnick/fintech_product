package com.minifin.platform.merchant.webhooks

import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class MerchantWebhookEndpointRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun insert(id: UUID, merchantId: UUID, url: String, enabledEventsJson: String, status: String, description: String?, createdBy: UUID) {
        jdbcTemplate.update(
            """
            insert into merchant.webhook_endpoints (id, merchant_id, url, enabled_events, status, description, created_by_employee_id)
            values (?, ?, ?, ?::jsonb, ?, ?, ?)
            """.trimIndent(),
            id,
            merchantId,
            url,
            enabledEventsJson,
            status,
            description,
            createdBy,
        )
    }

    fun list(merchantId: UUID): List<WebhookEndpointRecord> =
        jdbcTemplate.query(
            """
            select id, merchant_id, url, enabled_events::text as enabled_events_json, status, description, created_at, updated_at, deleted_at
            from merchant.webhook_endpoints
            where merchant_id = ? and status <> 'DELETED'
            order by created_at desc
            """.trimIndent(),
            { rs, _ -> rs.toRecord() },
            merchantId,
        )

    fun findByIdForMerchant(id: UUID, merchantId: UUID): WebhookEndpointRecord? =
        jdbcTemplate.query(
            """
            select id, merchant_id, url, enabled_events::text as enabled_events_json, status, description, created_at, updated_at, deleted_at
            from merchant.webhook_endpoints
            where id = ? and merchant_id = ? and status <> 'DELETED'
            """.trimIndent(),
            { rs, _ -> rs.toRecord() },
            id,
            merchantId,
        ).firstOrNull()

    fun update(id: UUID, merchantId: UUID, url: String, enabledEventsJson: String, status: String, description: String?): Int =
        jdbcTemplate.update(
            """
            update merchant.webhook_endpoints
            set url = ?, enabled_events = ?::jsonb, status = ?, description = ?, updated_at = now(), version = version + 1
            where id = ? and merchant_id = ? and status <> 'DELETED'
            """.trimIndent(),
            url,
            enabledEventsJson,
            status,
            description,
            id,
            merchantId,
        )

    fun delete(id: UUID, merchantId: UUID): Int =
        jdbcTemplate.update(
            """
            update merchant.webhook_endpoints
            set status = 'DELETED', deleted_at = coalesce(deleted_at, now()), updated_at = now(), version = version + 1
            where id = ? and merchant_id = ? and status <> 'DELETED'
            """.trimIndent(),
            id,
            merchantId,
        )

    private fun ResultSet.toRecord(): WebhookEndpointRecord = WebhookEndpointRecord(
        id = getObject("id", UUID::class.java),
        merchantId = getObject("merchant_id", UUID::class.java),
        url = getString("url"),
        enabledEventsJson = getString("enabled_events_json"),
        status = getString("status"),
        description = getString("description"),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        updatedAt = getObject("updated_at", OffsetDateTime::class.java),
        deletedAt = getObject("deleted_at", OffsetDateTime::class.java),
    )
}
