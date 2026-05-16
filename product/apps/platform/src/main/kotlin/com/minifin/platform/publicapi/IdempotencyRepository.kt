package com.minifin.platform.publicapi

import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

data class IdempotencyRecord(
    val merchantId: UUID,
    val idempotencyKey: String,
    val method: String,
    val route: String,
    val requestFingerprint: String,
    val responseStatus: Int,
    val responseBody: String,
    val createdAt: OffsetDateTime,
)

@Repository
class IdempotencyRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun find(merchantId: UUID, route: String, idempotencyKey: String): IdempotencyRecord? =
        jdbcTemplate.query(
            """
            select merchant_id, idempotency_key, method, route,
                   request_fingerprint, response_status, response_body, created_at
            from idempotency.idempotency_keys
            where merchant_id = ?
              and route = ?
              and idempotency_key = ?
            """.trimIndent(),
            { rs, _ -> rs.toRecord() },
            merchantId,
            route,
            idempotencyKey,
        ).firstOrNull()

    /**
     * Insert a reservation placeholder for [merchantId, route, idempotencyKey].
     *
     * Uses `ON CONFLICT DO NOTHING` rather than catching `DuplicateKeyException`:
     * once a unique-constraint violation aborts a JDBC statement, Postgres marks
     * the entire transaction as aborted (SQLSTATE 25P02) and refuses every
     * subsequent statement until ROLLBACK. With `ON CONFLICT DO NOTHING` the
     * INSERT is a no-op on conflict and the surrounding transaction stays
     * usable, which is what allows the runWriteOnce flow to read the cached
     * row in the same transaction.
     *
     * Returns true when this caller wrote the placeholder, false when another
     * caller had already done so.
     */
    fun insertPlaceholder(
        merchantId: UUID,
        idempotencyKey: String,
        method: String,
        route: String,
        requestFingerprint: String,
    ): Boolean {
        val rows = jdbcTemplate.update(
            """
            insert into idempotency.idempotency_keys (
                merchant_id,
                idempotency_key,
                method,
                route,
                request_fingerprint,
                response_status,
                response_body
            )
            values (?, ?, ?, ?, ?, 0, '')
            on conflict (merchant_id, route, idempotency_key) do nothing
            """.trimIndent(),
            merchantId,
            idempotencyKey,
            method,
            route,
            requestFingerprint,
        )
        return rows == 1
    }

    fun finalize(
        merchantId: UUID,
        route: String,
        idempotencyKey: String,
        responseStatus: Int,
        responseBody: String,
    ): Int =
        jdbcTemplate.update(
            """
            update idempotency.idempotency_keys
            set response_status = ?,
                response_body = ?
            where merchant_id = ?
              and route = ?
              and idempotency_key = ?
              and response_status = 0
            """.trimIndent(),
            responseStatus,
            responseBody,
            merchantId,
            route,
            idempotencyKey,
        )

    private fun ResultSet.toRecord(): IdempotencyRecord =
        IdempotencyRecord(
            merchantId = getObject("merchant_id", UUID::class.java),
            idempotencyKey = getString("idempotency_key"),
            method = getString("method"),
            route = getString("route"),
            requestFingerprint = getString("request_fingerprint"),
            responseStatus = getInt("response_status"),
            responseBody = getString("response_body"),
            createdAt = getObject("created_at", OffsetDateTime::class.java),
        )
}
