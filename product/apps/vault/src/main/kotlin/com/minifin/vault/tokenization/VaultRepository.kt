package com.minifin.vault.tokenization

import java.sql.ResultSet
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class VaultRepository(private val jdbcTemplate: JdbcTemplate, private val properties: VaultProperties) {
    fun insertToken(id: UUID, token: String, pan: String, last4: String, bin: String, expMonth: Int, expYear: Int) {
        jdbcTemplate.update(
            """
            insert into vault.card_tokens (id, card_token, pan_ciphertext, pan_last4, bin, expiration_month, expiration_year, status, key_version)
            values (?, ?, pgp_sym_encrypt(?, ?), ?, ?, ?, ?, 'ACTIVE', 1)
            """.trimIndent(),
            id, token, pan, properties.panEncryptionKey, last4, bin, expMonth, expYear,
        )
    }

    fun findPan(token: String): String? =
        jdbcTemplate.query(
            """
            select pgp_sym_decrypt(pan_ciphertext, ?) as pan
            from vault.card_tokens
            where card_token = ? and status = 'ACTIVE'
            """.trimIndent(),
            { rs: ResultSet, _: Int -> rs.getString("pan") },
            properties.panEncryptionKey, token,
        ).firstOrNull()

    fun writeDetokenizeAudit(token: String, caller: String, outcome: String, reason: String?, requestId: String?, correlationId: String?) {
        jdbcTemplate.update(
            """
            insert into vault.detokenize_audit_log (id, card_token, caller_service, outcome, reason, request_id, correlation_id)
            values (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(), token, caller, outcome, reason, requestId, correlationId,
        )
    }
}
