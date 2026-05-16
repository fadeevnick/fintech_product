package com.minifin.platform.merchant.apikeys

import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

data class ApiKeyRecord(
    val id: UUID,
    val merchantId: UUID,
    val label: String,
    val keyPrefix: String,
    val fingerprint: String,
    val status: String,
    val createdByEmployeeId: UUID,
    val lastUsedAt: OffsetDateTime?,
    val revokedAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
)

@Repository
class ApiKeyRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun insert(
        id: UUID,
        merchantId: UUID,
        label: String,
        keyPrefix: String,
        keyHash: String,
        fingerprint: String,
        createdByEmployeeId: UUID,
    ) {
        jdbcTemplate.update(
            """
            insert into merchant.api_keys (
                id,
                merchant_id,
                label,
                key_prefix,
                key_hash,
                fingerprint,
                status,
                created_by_employee_id
            )
            values (?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
            """.trimIndent(),
            id,
            merchantId,
            label,
            keyPrefix,
            keyHash,
            fingerprint,
            createdByEmployeeId,
        )
    }

    fun findById(id: UUID): ApiKeyRecord? =
        jdbcTemplate.query(
            """
            select id, merchant_id, label, key_prefix, fingerprint, status,
                   created_by_employee_id, last_used_at, revoked_at, created_at
            from merchant.api_keys
            where id = ?
            """.trimIndent(),
            { rs, _ -> rs.toApiKeyRecord() },
            id,
        ).firstOrNull()

    fun findActiveByKeyHash(keyHash: String): ApiKeyRecord? =
        jdbcTemplate.query(
            """
            select id, merchant_id, label, key_prefix, fingerprint, status,
                   created_by_employee_id, last_used_at, revoked_at, created_at
            from merchant.api_keys
            where key_hash = ?
              and status = 'ACTIVE'
            """.trimIndent(),
            { rs, _ -> rs.toApiKeyRecord() },
            keyHash,
        ).firstOrNull()

    fun listByMerchant(merchantId: UUID, limit: Int): List<ApiKeyRecord> =
        jdbcTemplate.query(
            """
            select id, merchant_id, label, key_prefix, fingerprint, status,
                   created_by_employee_id, last_used_at, revoked_at, created_at
            from merchant.api_keys
            where merchant_id = ?
            order by created_at desc
            limit ?
            """.trimIndent(),
            { rs, _ -> rs.toApiKeyRecord() },
            merchantId,
            limit,
        )

    fun revoke(id: UUID, merchantId: UUID, revokedByEmployeeId: UUID, now: OffsetDateTime): Int =
        jdbcTemplate.update(
            """
            update merchant.api_keys
            set status = 'REVOKED',
                revoked_at = coalesce(revoked_at, ?),
                revoked_by_employee_id = coalesce(revoked_by_employee_id, ?),
                updated_at = ?,
                version = version + 1
            where id = ?
              and merchant_id = ?
              and status = 'ACTIVE'
            """.trimIndent(),
            now,
            revokedByEmployeeId,
            now,
            id,
            merchantId,
        )

    fun touchLastUsed(id: UUID, now: OffsetDateTime) {
        jdbcTemplate.update(
            "update merchant.api_keys set last_used_at = ? where id = ?",
            now,
            id,
        )
    }

    private fun ResultSet.toApiKeyRecord(): ApiKeyRecord =
        ApiKeyRecord(
            id = getObject("id", UUID::class.java),
            merchantId = getObject("merchant_id", UUID::class.java),
            label = getString("label"),
            keyPrefix = getString("key_prefix"),
            fingerprint = getString("fingerprint"),
            status = getString("status"),
            createdByEmployeeId = getObject("created_by_employee_id", UUID::class.java),
            lastUsedAt = getObject("last_used_at", OffsetDateTime::class.java),
            revokedAt = getObject("revoked_at", OffsetDateTime::class.java),
            createdAt = getObject("created_at", OffsetDateTime::class.java),
        )
}
