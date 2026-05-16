package com.minifin.platform.wallet

import java.math.BigDecimal
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

data class WalletAccountRecord(
    val id: UUID,
    val userId: UUID,
    val ledgerAccountId: UUID,
    val currency: String,
)

data class DepositRequestRecord(
    val id: UUID,
    val userId: UUID,
    val walletAccountId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val state: String,
    val reason: String?,
    val journalEntryId: UUID?,
    val createdAt: OffsetDateTime,
    val decidedAt: OffsetDateTime?,
)

@Repository
class WalletRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun findWalletByUserId(userId: UUID): WalletAccountRecord? =
        jdbcTemplate.query(
            """
            select id, user_id, ledger_account_id, currency
            from wallet.wallet_accounts
            where user_id = ?
            """.trimIndent(),
            { rs, _ -> rs.toWalletAccountRecord() },
            userId,
        ).firstOrNull()

    fun insertWallet(
        id: UUID,
        userId: UUID,
        ledgerAccountId: UUID,
    ): WalletAccountRecord {
        jdbcTemplate.update(
            """
            insert into wallet.wallet_accounts (id, user_id, ledger_account_id)
            values (?, ?, ?)
            """.trimIndent(),
            id,
            userId,
            ledgerAccountId,
        )
        return findWalletByUserId(userId) ?: error("Wallet disappeared after insert")
    }

    fun insertDepositRequest(
        id: UUID,
        userId: UUID,
        walletAccountId: UUID,
        amount: BigDecimal,
        state: String,
    ): DepositRequestRecord {
        jdbcTemplate.update(
            """
            insert into wallet.deposit_requests (
                id, user_id, wallet_account_id, amount, currency, state
            )
            values (?, ?, ?, ?, 'EUR', ?)
            """.trimIndent(),
            id,
            userId,
            walletAccountId,
            amount,
            state,
        )
        return findDepositRequest(id) ?: error("Deposit request disappeared after insert")
    }

    fun findDepositRequest(id: UUID): DepositRequestRecord? =
        jdbcTemplate.query(
            """
            select id, user_id, wallet_account_id, amount, currency, state, reason,
                   journal_entry_id, created_at, decided_at
            from wallet.deposit_requests
            where id = ?
            """.trimIndent(),
            { rs, _ -> rs.toDepositRequestRecord() },
            id,
        ).firstOrNull()

    fun listDepositsForUser(userId: UUID, limit: Int): List<DepositRequestRecord> =
        jdbcTemplate.query(
            """
            select id, user_id, wallet_account_id, amount, currency, state, reason,
                   journal_entry_id, created_at, decided_at
            from wallet.deposit_requests
            where user_id = ?
            order by created_at desc
            limit ?
            """.trimIndent(),
            { rs, _ -> rs.toDepositRequestRecord() },
            userId,
            limit,
        )

    fun listPendingDeposits(limit: Int): List<DepositRequestRecord> =
        jdbcTemplate.query(
            """
            select id, user_id, wallet_account_id, amount, currency, state, reason,
                   journal_entry_id, created_at, decided_at
            from wallet.deposit_requests
            where state = 'PENDING_OPERATOR_REVIEW'
            order by created_at asc
            limit ?
            """.trimIndent(),
            { rs, _ -> rs.toDepositRequestRecord() },
            limit,
        )

    fun transitionToPendingReview(id: UUID): Int =
        jdbcTemplate.update(
            """
            update wallet.deposit_requests
               set state = 'PENDING_OPERATOR_REVIEW'
             where id = ?
               and state = 'REQUESTED'
            """.trimIndent(),
            id,
        )

    fun markCompleted(
        id: UUID,
        journalEntryId: UUID,
        reason: String,
        decidedByActorType: String,
        decidedByActorId: UUID?,
        decidedByReference: String?,
    ): Int =
        jdbcTemplate.update(
            """
            update wallet.deposit_requests
               set state = 'COMPLETED',
                   journal_entry_id = ?,
                   reason = ?,
                   decided_by_actor_type = ?,
                   decided_by_actor_id = ?,
                   decided_by_reference = ?,
                   decided_at = now()
             where id = ?
               and state = 'PENDING_OPERATOR_REVIEW'
            """.trimIndent(),
            journalEntryId,
            reason,
            decidedByActorType,
            decidedByActorId,
            decidedByReference,
            id,
        )

    fun markRejected(
        id: UUID,
        reason: String,
        decidedByActorType: String,
        decidedByActorId: UUID?,
        decidedByReference: String?,
    ): Int =
        jdbcTemplate.update(
            """
            update wallet.deposit_requests
               set state = 'REJECTED',
                   reason = ?,
                   decided_by_actor_type = ?,
                   decided_by_actor_id = ?,
                   decided_by_reference = ?,
                   decided_at = now()
             where id = ?
               and state in ('REQUESTED', 'PENDING_OPERATOR_REVIEW')
            """.trimIndent(),
            reason,
            decidedByActorType,
            decidedByActorId,
            decidedByReference,
            id,
        )

    private fun ResultSet.toWalletAccountRecord(): WalletAccountRecord =
        WalletAccountRecord(
            id = getObject("id", UUID::class.java),
            userId = getObject("user_id", UUID::class.java),
            ledgerAccountId = getObject("ledger_account_id", UUID::class.java),
            currency = getString("currency"),
        )

    private fun ResultSet.toDepositRequestRecord(): DepositRequestRecord =
        DepositRequestRecord(
            id = getObject("id", UUID::class.java),
            userId = getObject("user_id", UUID::class.java),
            walletAccountId = getObject("wallet_account_id", UUID::class.java),
            amount = getBigDecimal("amount"),
            currency = getString("currency"),
            state = getString("state"),
            reason = getString("reason"),
            journalEntryId = getObject("journal_entry_id", UUID::class.java),
            createdAt = getObject("created_at", OffsetDateTime::class.java),
            decidedAt = getObject("decided_at", OffsetDateTime::class.java),
        )
}
