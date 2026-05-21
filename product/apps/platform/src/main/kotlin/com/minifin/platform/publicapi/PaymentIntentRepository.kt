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
    val capturedAt: OffsetDateTime?,
    val capturedAmount: BigDecimal?,
    val captureRequestId: String?,
)

data class RefundRecord(
    val id: UUID,
    val paymentIntentId: UUID,
    val merchantId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val reason: String?,
    val state: String,
    val ledgerJournalId: UUID?,
    val createdAt: OffsetDateTime,
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

    fun markAuthorizedResult(
        id: UUID,
        cardToken: String,
        authorizationId: UUID?,
        authCode: String?,
        declineCode: String?,
        declineMessage: String?,
        state: String,
    ) {
        jdbcTemplate.update(
            """
            update merchant.payment_intents
               set state = ?, card_token = ?, authorization_id = ?, auth_code = ?,
                   decline_code = ?, decline_message = ?, updated_at = now(), version = version + 1
             where id = ?
            """.trimIndent(),
            state, cardToken, authorizationId, authCode, declineCode, declineMessage, id,
        )
    }

    fun markCaptured(
        id: UUID,
        capturedAmount: BigDecimal,
        captureRequestId: String,
    ): Int = jdbcTemplate.update(
        """
        update merchant.payment_intents
           set state             = 'CAPTURED',
               captured_at       = now(),
               captured_amount   = ?,
               capture_request_id = ?,
               updated_at        = now(),
               version           = version + 1
         where id = ?
           and state = 'AUTHORIZED'
        """.trimIndent(),
        capturedAmount,
        captureRequestId,
        id,
    )

    fun findById(id: UUID): PaymentIntentRecord? =
        jdbcTemplate.query(
            """
            select id, merchant_id, api_key_id, amount, currency, description, state, created_at,
                   authorization_id, auth_code, decline_code, decline_message,
                   captured_at, captured_amount, capture_request_id
            from merchant.payment_intents
            where id = ?
            """.trimIndent(),
            { rs, _ -> rs.toRecord() },
            id,
        ).firstOrNull()

    fun findByIdForUpdate(id: UUID): PaymentIntentRecord? =
        jdbcTemplate.query(
            """
            select id, merchant_id, api_key_id, amount, currency, description, state, created_at,
                   authorization_id, auth_code, decline_code, decline_message,
                   captured_at, captured_amount, capture_request_id
            from merchant.payment_intents
            where id = ?
            for update
            """.trimIndent(),
            { rs, _ -> rs.toRecord() },
            id,
        ).firstOrNull()

    fun successfulRefundTotal(paymentIntentId: UUID): BigDecimal =
        jdbcTemplate.queryForObject(
            """
            select coalesce(sum(amount), 0)
            from merchant.refunds
            where payment_intent_id = ?
              and state = 'SUCCEEDED'
            """.trimIndent(),
            BigDecimal::class.java,
            paymentIntentId,
        ) ?: BigDecimal.ZERO

    fun cardholderWalletLedgerAccountId(paymentIntentId: UUID): UUID? =
        jdbcTemplate.query(
            """
            select wa.ledger_account_id
              from merchant.payment_intents pi
              join cards.issued_cards ic on ic.card_token = pi.card_token
              join wallet.wallet_accounts wa on wa.id = ic.wallet_account_id
             where pi.id = ?
            """.trimIndent(),
            { rs, _ -> rs.getObject("ledger_account_id", UUID::class.java) },
            paymentIntentId,
        ).firstOrNull()

    fun ledgerAccountByCode(code: String): UUID? =
        jdbcTemplate.query(
            """
            select id
            from ledger.accounts
            where code = ?
            """.trimIndent(),
            { rs, _ -> rs.getObject("id", UUID::class.java) },
            code,
        ).firstOrNull()

    fun insertRefund(
        id: UUID,
        paymentIntentId: UUID,
        merchantId: UUID,
        apiKeyId: UUID,
        amount: BigDecimal,
        currency: String,
        reason: String?,
        ledgerJournalId: UUID,
    ) {
        jdbcTemplate.update(
            """
            insert into merchant.refunds (
                id,
                payment_intent_id,
                merchant_id,
                api_key_id,
                amount,
                currency,
                reason,
                state,
                ledger_journal_id
            )
            values (?, ?, ?, ?, ?, ?, ?, 'SUCCEEDED', ?)
            """.trimIndent(),
            id,
            paymentIntentId,
            merchantId,
            apiKeyId,
            amount,
            currency,
            reason,
            ledgerJournalId,
        )
    }

    fun updateRefundedState(id: UUID, state: String): Int =
        jdbcTemplate.update(
            """
            update merchant.payment_intents
               set state = ?,
                   updated_at = now(),
                   version = version + 1
             where id = ?
               and state in ('SETTLED', 'PARTIALLY_REFUNDED')
            """.trimIndent(),
            state,
            id,
        )

    fun findRefundById(id: UUID): RefundRecord? =
        jdbcTemplate.query(
            """
            select id, payment_intent_id, merchant_id, amount, currency, reason, state, ledger_journal_id, created_at
            from merchant.refunds
            where id = ?
            """.trimIndent(),
            { rs, _ -> rs.toRefundRecord() },
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
            capturedAt = getObject("captured_at", OffsetDateTime::class.java),
            capturedAmount = getBigDecimal("captured_amount"),
            captureRequestId = getString("capture_request_id"),
        )

    private fun ResultSet.toRefundRecord(): RefundRecord =
        RefundRecord(
            id = getObject("id", UUID::class.java),
            paymentIntentId = getObject("payment_intent_id", UUID::class.java),
            merchantId = getObject("merchant_id", UUID::class.java),
            amount = getBigDecimal("amount"),
            currency = getString("currency"),
            reason = getString("reason"),
            state = getString("state"),
            ledgerJournalId = getObject("ledger_journal_id", UUID::class.java),
            createdAt = getObject("created_at", OffsetDateTime::class.java),
        )
}
