package com.minifin.platform.publicapi

import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.dao.DuplicateKeyException
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
    fun find(merchantId: UUID, idempotencyKey: String): IdempotencyRecord? =
        jdbcTemplate.query(
            """
            select merchant_id, idempotency_key, method, route,
                   request_fingerprint, response_status, response_body, created_at
            from idempotency.idempotency_keys
            where merchant_id = ?
              and idempotency_key = ?
            """.trimIndent(),
            { rs, _ -> rs.toRecord() },
            merchantId,
            idempotencyKey,
        ).firstOrNull()

    fun insert(
        merchantId: UUID,
        idempotencyKey: String,
        method: String,
        route: String,
        requestFingerprint: String,
        responseStatus: Int,
        responseBody: String,
    ): Boolean =
        try {
            jdbcTemplate.update(
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
                values (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                merchantId,
                idempotencyKey,
                method,
                route,
                requestFingerprint,
                responseStatus,
                responseBody,
            )
            true
        } catch (_: DuplicateKeyException) {
            false
        }

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
