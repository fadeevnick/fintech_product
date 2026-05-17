package com.minifin.platform.merchant.dashboard

import com.minifin.platform.publicapi.PaymentIntentRecord
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class MerchantPaymentDashboardRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun list(merchantId: UUID, state: String?, limit: Int): List<PaymentIntentRecord> =
        if (state == null) {
            jdbcTemplate.query(
                """
                select id, merchant_id, api_key_id, amount, currency, description, state, created_at
                from merchant.payment_intents
                where merchant_id = ?
                order by created_at desc, id desc
                limit ?
                """.trimIndent(),
                { rs, _ -> rs.toRecord() },
                merchantId,
                limit,
            )
        } else {
            jdbcTemplate.query(
                """
                select id, merchant_id, api_key_id, amount, currency, description, state, created_at
                from merchant.payment_intents
                where merchant_id = ? and state = ?
                order by created_at desc, id desc
                limit ?
                """.trimIndent(),
                { rs, _ -> rs.toRecord() },
                merchantId,
                state,
                limit,
            )
        }

    fun findByIdForMerchant(id: UUID, merchantId: UUID): PaymentIntentRecord? =
        jdbcTemplate.query(
            """
            select id, merchant_id, api_key_id, amount, currency, description, state, created_at
            from merchant.payment_intents
            where id = ? and merchant_id = ?
            """.trimIndent(),
            { rs, _ -> rs.toRecord() },
            id,
            merchantId,
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
        )
}
