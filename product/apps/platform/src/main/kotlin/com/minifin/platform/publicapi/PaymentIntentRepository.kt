package com.minifin.platform.publicapi

import java.math.BigDecimal
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

data class PaymentIntentRecord(
    val id: UUID,
    val merchantId: UUID,
    val apiKeyId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val description: String?,
    val state: String,
    val createdAt: OffsetDateTime,
    val authorizationId: UUID?,
    val authCode: String?,
    val declineCode: String?,
    val declineMessage: String?,
)

@Repository
class PaymentIntentRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun insert(
        id: UUID,
        merchantId: UUID,
        apiKeyId: UUID,
        amount: BigDecimal,
        currency: String,
        description: String?,
    ) {
        jdbcTemplate.update(
            """
            insert into merchant.payment_intents (
                id,
                merchant_id,
                api_key_id,
                amount,
                currency,
                description,
                state
            )
            values (?, ?, ?, ?, ?, ?, 'REQUIRES_PAYMENT_METHOD')
            """.trimIndent(),
            id,
            merchantId,
            apiKeyId,
            amount,
            currency,
            description,
        )
    }

    fun markAuthorizedResult(id: UUID, cardToken: String, authorizationId: UUID?, authCode: String?, declineCode: String?, declineMessage: String?, state: String) {
        jdbcTemplate.update("""
            update merchant.payment_intents
               set state = ?, card_token = ?, authorization_id = ?, auth_code = ?, decline_code = ?, decline_message = ?, updated_at = now(), version = version + 1
             where id = ?
        """.trimIndent(), state, cardToken, authorizationId, authCode, declineCode, declineMessage, id)
    }

    fun findById(id: UUID): PaymentIntentRecord? =
        jdbcTemplate.query(
            """
            select id, merchant_id, api_key_id, amount, currency, description, state, created_at,
                   authorization_id, auth_code, decline_code, decline_message
            from merchant.payment_intents
            where id = ?
            """.trimIndent(),
            { rs, _ -> rs.toRecord() },
            id,
        ).firstOrNull()

    private fun ResultSet.toRecord(): PaymentIntentRecord =
        PaymentIntentRecord(
            id = getObject("id", UUID::class.java),
            merchantId = getObject("merchant_id", UUID::class.java),
            apiKeyId = getObject("api_key_id", UUID::class.java),
            amount = getBigDecimal("amount"),
            currency = getString("currency"),
            description = getString("description"),
            state = getString("state"),
            createdAt = getObject("created_at", OffsetDateTime::class.java),
            authorizationId = getObject("authorization_id", UUID::class.java),
            authCode = getString("auth_code"),
            declineCode = getString("decline_code"),
            declineMessage = getString("decline_message"),
        )
}
