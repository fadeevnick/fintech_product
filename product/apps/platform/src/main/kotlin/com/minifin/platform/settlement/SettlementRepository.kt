package com.minifin.platform.settlement

import java.math.BigDecimal
import java.sql.ResultSet
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class SettlementRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun findCapturedCandidates(limit: Int): List<SettlementCandidate> =
        jdbcTemplate.query(
            """
            select pi.id, pi.merchant_id
            from merchant.payment_intents pi
            where pi.state = 'CAPTURED'
              and not exists (
                  select 1
                  from settlement.settlement_items si
                  where si.payment_intent_id = pi.id
              )
            order by pi.captured_at asc, pi.id asc
            limit ?
            for update skip locked
            """.trimIndent(),
            { rs, _ -> rs.toCandidate() },
            limit,
        )

    fun createBatch(id: UUID) {
        jdbcTemplate.update(
            """
            insert into settlement.settlement_batches (
                id,
                status,
                settled_at
            )
            values (?, 'SETTLED', now())
            """.trimIndent(),
            id,
        )
    }

    fun findOrCreateMerchantSettlementAccount(merchantId: UUID): UUID {
        val code = "MERCHANT_SETTLEMENT:$merchantId"
        jdbcTemplate.query(
            """
            select id
            from ledger.accounts
            where code = ?
            """.trimIndent(),
            { rs, _ -> rs.getObject("id", UUID::class.java) },
            code,
        ).firstOrNull()?.let { return it }

        val id = UUID.randomUUID()
        jdbcTemplate.update(
            """
            insert into ledger.accounts (
                id,
                code,
                currency,
                account_type,
                normal_side,
                owner_type,
                owner_id
            )
            values (?, ?, 'EUR', 'MERCHANT_SETTLEMENT', 'CREDIT', 'MERCHANT', ?)
            on conflict (code) do nothing
            """.trimIndent(),
            id,
            code,
            merchantId,
        )
        return jdbcTemplate.queryForObject(
            """
            select id
            from ledger.accounts
            where code = ?
            """.trimIndent(),
            UUID::class.java,
            code,
        ) ?: id
    }

    fun settlementClearingAccount(): UUID =
        jdbcTemplate.queryForObject(
            """
            select id
            from ledger.accounts
            where code = 'CARD_SETTLEMENT_CLEARING'
            """.trimIndent(),
            UUID::class.java,
        ) ?: throw IllegalStateException("CARD_SETTLEMENT_CLEARING ledger account is missing")

    fun accountByCode(code: String): UUID =
        jdbcTemplate.queryForObject(
            """
            select id
            from ledger.accounts
            where code = ?
            """.trimIndent(),
            UUID::class.java,
            code,
        ) ?: throw IllegalStateException("$code ledger account is missing")

    fun paymentAmount(paymentIntentId: UUID): BigDecimal =
        jdbcTemplate.queryForObject(
            """
            select coalesce(captured_amount, amount)
            from merchant.payment_intents
            where id = ?
            """.trimIndent(),
            BigDecimal::class.java,
            paymentIntentId,
        ) ?: throw IllegalStateException("Payment intent amount is missing")

    fun insertItem(
        id: UUID,
        batchId: UUID,
        paymentIntentId: UUID,
        merchantId: UUID,
        grossAmount: BigDecimal,
        merchantNetAmount: BigDecimal,
        interchangeAmount: BigDecimal,
        networkAssessmentAmount: BigDecimal,
        acquirerMarginAmount: BigDecimal,
        currency: String,
        ledgerJournalId: UUID,
    ): Int =
        jdbcTemplate.update(
            """
            insert into settlement.settlement_items (
                id,
                batch_id,
                payment_intent_id,
                merchant_id,
                gross_amount,
                merchant_net_amount,
                interchange_amount,
                network_assessment_amount,
                acquirer_margin_amount,
                currency,
                status,
                ledger_journal_id
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'SETTLED', ?)
            on conflict (payment_intent_id) do nothing
            """.trimIndent(),
            id,
            batchId,
            paymentIntentId,
            merchantId,
            grossAmount,
            merchantNetAmount,
            interchangeAmount,
            networkAssessmentAmount,
            acquirerMarginAmount,
            currency,
            ledgerJournalId,
        )

    fun markPaymentSettled(paymentIntentId: UUID): Int =
        jdbcTemplate.update(
            """
            update merchant.payment_intents
               set state = 'SETTLED',
                   updated_at = now(),
                   version = version + 1
             where id = ?
               and state = 'CAPTURED'
            """.trimIndent(),
            paymentIntentId,
        )

    fun projectionItems(limit: Int): List<SettlementProjectionItem> =
        jdbcTemplate.query(
            """
            select si.id,
                   si.batch_id,
                   si.merchant_id,
                   si.payment_intent_id,
                   si.gross_amount,
                   si.merchant_net_amount,
                   si.interchange_amount,
                   si.network_assessment_amount,
                   si.acquirer_margin_amount,
                   si.currency,
                   sb.settled_at
            from settlement.settlement_items si
            join settlement.settlement_batches sb on sb.id = si.batch_id
            order by si.created_at asc, si.id asc
            limit ?
            """.trimIndent(),
            { rs, _ ->
                SettlementProjectionItem(
                    platformSettlementItemId = rs.getObject("id", UUID::class.java).toString(),
                    platformBatchId = rs.getObject("batch_id", UUID::class.java).toString(),
                    merchantId = rs.getObject("merchant_id", UUID::class.java).toString(),
                    paymentIntentId = rs.getObject("payment_intent_id", UUID::class.java).toString(),
                    grossAmount = rs.getBigDecimal("gross_amount").toPlainString(),
                    merchantNetAmount = rs.getBigDecimal("merchant_net_amount").toPlainString(),
                    interchangeAmount = rs.getBigDecimal("interchange_amount").toPlainString(),
                    networkAssessmentAmount = rs.getBigDecimal("network_assessment_amount").toPlainString(),
                    acquirerMarginAmount = rs.getBigDecimal("acquirer_margin_amount").toPlainString(),
                    currency = rs.getString("currency"),
                    platformSettledAt = rs.getObject("settled_at", java.time.OffsetDateTime::class.java)?.toString(),
                )
            },
            limit,
        )

    private fun ResultSet.toCandidate(): SettlementCandidate =
        SettlementCandidate(
            paymentIntentId = getObject("id", UUID::class.java),
            merchantId = getObject("merchant_id", UUID::class.java),
        )
}
