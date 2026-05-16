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

data class WithdrawalRequestRecord(
    val id: UUID,
    val userId: UUID,
    val walletAccountId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val state: String,
    val reason: String?,
    val holdJournalEntryId: UUID?,
    val completionJournalEntryId: UUID?,
    val releaseJournalEntryId: UUID?,
    val createdAt: OffsetDateTime,
    val heldAt: OffsetDateTime?,
    val decidedAt: OffsetDateTime?,
)

data class InternalTransferRecord(
    val id: UUID,
    val senderUserId: UUID,
    val receiverUserId: UUID,
    val senderWalletAccountId: UUID,
    val receiverWalletAccountId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val state: String,
    val journalEntryId: UUID?,
    val idempotencyKey: String?,
    val requestFingerprint: String?,
    val createdAt: OffsetDateTime,
    val completedAt: OffsetDateTime?,
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

    fun upsertWallet(
        id: UUID,
        userId: UUID,
        ledgerAccountId: UUID,
    ): WalletAccountRecord {
        jdbcTemplate.update(
            """
            insert into wallet.wallet_accounts (id, user_id, ledger_account_id)
            values (?, ?, ?)
            on conflict (user_id) do nothing
            """.trimIndent(),
            id,
            userId,
            ledgerAccountId,
        )
        return findWalletByUserId(userId) ?: error("Wallet disappeared after upsert")
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

    fun insertWithdrawalRequest(
        id: UUID,
        userId: UUID,
        walletAccountId: UUID,
        amount: BigDecimal,
        state: String,
    ): WithdrawalRequestRecord {
        jdbcTemplate.update(
            """
            insert into wallet.withdraw_requests (
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
        return findWithdrawalRequest(id) ?: error("Withdrawal request disappeared after insert")
    }

    fun findWithdrawalRequest(id: UUID): WithdrawalRequestRecord? =
        jdbcTemplate.query(
            """
            select id, user_id, wallet_account_id, amount, currency, state, reason,
                   hold_journal_entry_id, completion_journal_entry_id, release_journal_entry_id,
                   created_at, held_at, decided_at
            from wallet.withdraw_requests
            where id = ?
            """.trimIndent(),
            { rs, _ -> rs.toWithdrawalRequestRecord() },
            id,
        ).firstOrNull()

    fun listWithdrawalsForUser(userId: UUID, limit: Int): List<WithdrawalRequestRecord> =
        jdbcTemplate.query(
            """
            select id, user_id, wallet_account_id, amount, currency, state, reason,
                   hold_journal_entry_id, completion_journal_entry_id, release_journal_entry_id,
                   created_at, held_at, decided_at
            from wallet.withdraw_requests
            where user_id = ?
            order by created_at desc
            limit ?
            """.trimIndent(),
            { rs, _ -> rs.toWithdrawalRequestRecord() },
            userId,
            limit,
        )

    fun listHeldWithdrawals(limit: Int): List<WithdrawalRequestRecord> =
        jdbcTemplate.query(
            """
            select id, user_id, wallet_account_id, amount, currency, state, reason,
                   hold_journal_entry_id, completion_journal_entry_id, release_journal_entry_id,
                   created_at, held_at, decided_at
            from wallet.withdraw_requests
            where state = 'HELD'
            order by created_at asc
            limit ?
            """.trimIndent(),
            { rs, _ -> rs.toWithdrawalRequestRecord() },
            limit,
        )

    fun markWithdrawalHeld(
        id: UUID,
        holdJournalEntryId: UUID,
    ): Int =
        jdbcTemplate.update(
            """
            update wallet.withdraw_requests
               set state = 'HELD',
                   hold_journal_entry_id = ?,
                   held_at = now()
             where id = ?
               and state = 'PENDING'
            """.trimIndent(),
            holdJournalEntryId,
            id,
        )

    fun markWithdrawalCompleted(
        id: UUID,
        completionJournalEntryId: UUID,
        reason: String,
        decidedByActorType: String,
        decidedByActorId: UUID?,
        decidedByReference: String?,
    ): Int =
        jdbcTemplate.update(
            """
            update wallet.withdraw_requests
               set state = 'COMPLETED',
                   completion_journal_entry_id = ?,
                   reason = ?,
                   decided_by_actor_type = ?,
                   decided_by_actor_id = ?,
                   decided_by_reference = ?,
                   decided_at = now()
             where id = ?
               and state = 'HELD'
            """.trimIndent(),
            completionJournalEntryId,
            reason,
            decidedByActorType,
            decidedByActorId,
            decidedByReference,
            id,
        )

    fun markWithdrawalRejected(
        id: UUID,
        releaseJournalEntryId: UUID,
        reason: String,
        decidedByActorType: String,
        decidedByActorId: UUID?,
        decidedByReference: String?,
    ): Int =
        jdbcTemplate.update(
            """
            update wallet.withdraw_requests
               set state = 'REJECTED',
                   release_journal_entry_id = ?,
                   reason = ?,
                   decided_by_actor_type = ?,
                   decided_by_actor_id = ?,
                   decided_by_reference = ?,
                   decided_at = now()
             where id = ?
               and state = 'HELD'
            """.trimIndent(),
            releaseJournalEntryId,
            reason,
            decidedByActorType,
            decidedByActorId,
            decidedByReference,
            id,
        )

    fun insertInternalTransferPending(
        id: UUID,
        senderUserId: UUID,
        receiverUserId: UUID,
        senderWalletAccountId: UUID,
        receiverWalletAccountId: UUID,
        amount: BigDecimal,
        idempotencyKey: String?,
        requestFingerprint: String?,
    ): Int =
        jdbcTemplate.update(
            """
            insert into wallet.internal_transfers (
                id,
                sender_user_id,
                receiver_user_id,
                sender_wallet_account_id,
                receiver_wallet_account_id,
                amount,
                currency,
                state,
                idempotency_key,
                request_fingerprint
            )
            values (?, ?, ?, ?, ?, ?, 'EUR', 'PENDING', ?, ?)
            on conflict (sender_user_id, idempotency_key)
            where idempotency_key is not null
            do nothing
            """.trimIndent(),
            id,
            senderUserId,
            receiverUserId,
            senderWalletAccountId,
            receiverWalletAccountId,
            amount,
            idempotencyKey,
            requestFingerprint,
        )

    fun markInternalTransferCompleted(
        id: UUID,
        journalEntryId: UUID,
    ): Int =
        jdbcTemplate.update(
            """
            update wallet.internal_transfers
               set state = 'COMPLETED',
                   journal_entry_id = ?,
                   completed_at = now()
             where id = ?
               and state = 'PENDING'
            """.trimIndent(),
            journalEntryId,
            id,
        )

    fun findInternalTransfer(id: UUID): InternalTransferRecord? =
        jdbcTemplate.query(
            """
            select id, sender_user_id, receiver_user_id, sender_wallet_account_id,
                   receiver_wallet_account_id, amount, currency, state, journal_entry_id,
                   idempotency_key, request_fingerprint, created_at, completed_at
            from wallet.internal_transfers
            where id = ?
            """.trimIndent(),
            { rs, _ -> rs.toInternalTransferRecord() },
            id,
        ).firstOrNull()

    fun findInternalTransferByIdempotencyKey(
        senderUserId: UUID,
        idempotencyKey: String,
    ): InternalTransferRecord? =
        jdbcTemplate.query(
            """
            select id, sender_user_id, receiver_user_id, sender_wallet_account_id,
                   receiver_wallet_account_id, amount, currency, state, journal_entry_id,
                   idempotency_key, request_fingerprint, created_at, completed_at
            from wallet.internal_transfers
            where sender_user_id = ?
              and idempotency_key = ?
            """.trimIndent(),
            { rs, _ -> rs.toInternalTransferRecord() },
            senderUserId,
            idempotencyKey,
        ).firstOrNull()

    fun listInternalTransfersForUser(userId: UUID, limit: Int): List<InternalTransferRecord> =
        jdbcTemplate.query(
            """
            select id, sender_user_id, receiver_user_id, sender_wallet_account_id,
                   receiver_wallet_account_id, amount, currency, state, journal_entry_id,
                   idempotency_key, request_fingerprint, created_at, completed_at
            from wallet.internal_transfers
            where sender_user_id = ?
               or receiver_user_id = ?
            order by created_at desc
            limit ?
            """.trimIndent(),
            { rs, _ -> rs.toInternalTransferRecord() },
            userId,
            userId,
            limit,
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

    private fun ResultSet.toWithdrawalRequestRecord(): WithdrawalRequestRecord =
        WithdrawalRequestRecord(
            id = getObject("id", UUID::class.java),
            userId = getObject("user_id", UUID::class.java),
            walletAccountId = getObject("wallet_account_id", UUID::class.java),
            amount = getBigDecimal("amount"),
            currency = getString("currency"),
            state = getString("state"),
            reason = getString("reason"),
            holdJournalEntryId = getObject("hold_journal_entry_id", UUID::class.java),
            completionJournalEntryId = getObject("completion_journal_entry_id", UUID::class.java),
            releaseJournalEntryId = getObject("release_journal_entry_id", UUID::class.java),
            createdAt = getObject("created_at", OffsetDateTime::class.java),
            heldAt = getObject("held_at", OffsetDateTime::class.java),
            decidedAt = getObject("decided_at", OffsetDateTime::class.java),
        )

    private fun ResultSet.toInternalTransferRecord(): InternalTransferRecord =
        InternalTransferRecord(
            id = getObject("id", UUID::class.java),
            senderUserId = getObject("sender_user_id", UUID::class.java),
            receiverUserId = getObject("receiver_user_id", UUID::class.java),
            senderWalletAccountId = getObject("sender_wallet_account_id", UUID::class.java),
            receiverWalletAccountId = getObject("receiver_wallet_account_id", UUID::class.java),
            amount = getBigDecimal("amount"),
            currency = getString("currency"),
            state = getString("state"),
            journalEntryId = getObject("journal_entry_id", UUID::class.java),
            idempotencyKey = getString("idempotency_key"),
            requestFingerprint = getString("request_fingerprint"),
            createdAt = getObject("created_at", OffsetDateTime::class.java),
            completedAt = getObject("completed_at", OffsetDateTime::class.java),
        )
}
