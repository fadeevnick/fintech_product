package com.minifin.platform.chargeback

import java.math.BigDecimal
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

data class DisputablePaymentRecord(
    val id: UUID,
    val merchantId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val state: String,
    val capturedAt: OffsetDateTime?,
    val settledAt: OffsetDateTime?,
    val cardholderUserId: UUID?,
)

data class ChargebackDisputeRecord(
    val id: UUID,
    val paymentIntentId: UUID,
    val merchantId: UUID,
    val cardholderUserId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val reasonCode: String,
    val narrative: String?,
    val state: String,
    val merchantResponseDeadline: OffsetDateTime,
    val createdAt: OffsetDateTime,
)

@Repository
class ChargebackRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun findPayment(id: UUID): DisputablePaymentRecord? =
        jdbcTemplate.query(
            """
            select pi.id, pi.merchant_id, pi.amount, pi.currency, pi.state, pi.captured_at,
                   si.created_at as settled_at, ic.end_user_id as cardholder_user_id
              from merchant.payment_intents pi
              left join settlement.settlement_items si on si.payment_intent_id = pi.id
              left join cards.issued_cards ic on ic.card_token = pi.card_token
             where pi.id = ?
            """.trimIndent(),
            { rs, _ -> rs.toPaymentRecord() },
            id,
        ).firstOrNull()

    fun findDisputeByPaymentIntent(paymentIntentId: UUID): ChargebackDisputeRecord? =
        jdbcTemplate.query(
            """
            select id, payment_intent_id, merchant_id, cardholder_user_id, amount, currency, reason_code,
                   narrative, state, merchant_response_deadline, created_at
              from chargeback.disputes
             where payment_intent_id = ?
            """.trimIndent(),
            { rs, _ -> rs.toDisputeRecord() },
            paymentIntentId,
        ).firstOrNull()

    fun insertDispute(
        id: UUID,
        paymentIntentId: UUID,
        merchantId: UUID,
        cardholderUserId: UUID,
        amount: BigDecimal,
        currency: String,
        reasonCode: String,
        narrative: String?,
        merchantResponseDeadline: OffsetDateTime,
    ): Int = jdbcTemplate.update(
        """
        insert into chargeback.disputes (
            id, payment_intent_id, merchant_id, cardholder_user_id, amount, currency, reason_code,
            narrative, state, merchant_response_deadline
        )
        values (?, ?, ?, ?, ?, ?, ?, ?, 'MERCHANT_NOTIFIED', ?)
        on conflict (payment_intent_id) do nothing
        """.trimIndent(),
        id,
        paymentIntentId,
        merchantId,
        cardholderUserId,
        amount,
        currency,
        reasonCode,
        narrative,
        merchantResponseDeadline,
    )

    fun markPaymentDisputed(paymentIntentId: UUID): Int = jdbcTemplate.update(
        """
        update merchant.payment_intents
           set state = 'DISPUTED', updated_at = now(), version = version + 1
         where id = ?
           and state = 'SETTLED'
        """.trimIndent(),
        paymentIntentId,
    )

    private fun ResultSet.toPaymentRecord(): DisputablePaymentRecord =
        DisputablePaymentRecord(
            id = getObject("id", UUID::class.java),
            merchantId = getObject("merchant_id", UUID::class.java),
            amount = getBigDecimal("amount"),
            currency = getString("currency"),
            state = getString("state"),
            capturedAt = getObject("captured_at", OffsetDateTime::class.java),
            settledAt = getObject("settled_at", OffsetDateTime::class.java),
            cardholderUserId = getObject("cardholder_user_id", UUID::class.java),
        )

    private fun ResultSet.toDisputeRecord(): ChargebackDisputeRecord =
        ChargebackDisputeRecord(
            id = getObject("id", UUID::class.java),
            paymentIntentId = getObject("payment_intent_id", UUID::class.java),
            merchantId = getObject("merchant_id", UUID::class.java),
            cardholderUserId = getObject("cardholder_user_id", UUID::class.java),
            amount = getBigDecimal("amount"),
            currency = getString("currency"),
            reasonCode = getString("reason_code"),
            narrative = getString("narrative"),
            state = getString("state"),
            merchantResponseDeadline = getObject("merchant_response_deadline", OffsetDateTime::class.java),
            createdAt = getObject("created_at", OffsetDateTime::class.java),
        )
}
