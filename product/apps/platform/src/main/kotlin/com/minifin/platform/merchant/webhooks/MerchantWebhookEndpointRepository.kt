package com.minifin.platform.merchant.webhooks

import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class MerchantWebhookEndpointRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val properties: WebhookDeliveryProperties,
) {
    fun insert(
        id: UUID,
        merchantId: UUID,
        url: String,
        enabledEventsJson: String,
        status: String,
        description: String?,
        createdBy: UUID,
        signingSecretHash: String,
        signingSecret: String,
        secretPrefix: String,
    ) {
        jdbcTemplate.update(
            """
            insert into merchant.webhook_endpoints (
                id, merchant_id, url, enabled_events, status, description, created_by_employee_id,
                signing_secret_hash, signing_secret_ciphertext, secret_prefix, secret_rotated_at
            )
            values (?, ?, ?, ?::jsonb, ?, ?, ?, ?, pgp_sym_encrypt(?, ?), ?, now())
            """.trimIndent(),
            id,
            merchantId,
            url,
            enabledEventsJson,
            status,
            description,
            createdBy,
            signingSecretHash,
            signingSecret,
            properties.signingSecretEncryptionKey,
            secretPrefix,
        )
    }

    fun list(merchantId: UUID): List<WebhookEndpointRecord> =
        jdbcTemplate.query(
            """
            select id, merchant_id, url, enabled_events::text as enabled_events_json, status, description,
                   signing_secret_hash, null::text as signing_secret, secret_prefix, created_at, updated_at, deleted_at
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
            select id, merchant_id, url, enabled_events::text as enabled_events_json, status, description,
                   signing_secret_hash, null::text as signing_secret, secret_prefix, created_at, updated_at, deleted_at
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

    fun rotateSecret(id: UUID, merchantId: UUID, signingSecretHash: String, signingSecret: String, secretPrefix: String): Int =
        jdbcTemplate.update(
            """
            update merchant.webhook_endpoints
            set signing_secret_hash = ?,
                signing_secret_ciphertext = pgp_sym_encrypt(?, ?),
                secret_prefix = ?,
                secret_rotated_at = now(),
                updated_at = now(),
                version = version + 1
            where id = ? and merchant_id = ? and status <> 'DELETED'
            """.trimIndent(),
            signingSecretHash,
            signingSecret,
            properties.signingSecretEncryptionKey,
            secretPrefix,
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
        signingSecretHash = getString("signing_secret_hash"),
        signingSecret = getString("signing_secret"),
        secretPrefix = getString("secret_prefix"),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        updatedAt = getObject("updated_at", OffsetDateTime::class.java),
        deletedAt = getObject("deleted_at", OffsetDateTime::class.java),
    )
}
