package com.minifin.platform.ledger

import java.math.BigDecimal
import java.sql.ResultSet
import java.util.UUID
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

data class LedgerAccountRecord(
    val id: UUID,
    val code: String,
    val currency: String,
    val accountType: String,
    val normalSide: String,
)

data class LedgerBalanceRecord(
    val accountId: UUID,
    val code: String,
    val currency: String,
    val normalSide: String,
    val balance: BigDecimal,
)

data class LedgerReconciliationRecord(
    val journalCount: Long,
    val postingCount: Long,
    val imbalancedJournalCount: Long,
)

@Repository
class LedgerRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun upsertAccount(
        id: UUID,
        code: String,
        currency: String,
        accountType: String,
        normalSide: String,
        ownerType: String?,
        ownerId: UUID?,
    ): LedgerAccountRecord {
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
            values (?, ?, ?, ?, ?, ?, ?)
            on conflict (code)
            do update set code = excluded.code
            """.trimIndent(),
            id,
            code,
            currency,
            accountType,
            normalSide,
            ownerType,
            ownerId,
        )
        return findAccountByCode(code) ?: error("Ledger account disappeared after upsert")
    }

    fun findAccountByCode(code: String): LedgerAccountRecord? =
        jdbcTemplate.query(
            """
            select id, code, currency, account_type, normal_side
            from ledger.accounts
            where code = ?
            """.trimIndent(),
            { rs, _ -> rs.toLedgerAccountRecord() },
            code,
        ).firstOrNull()

    fun postJournal(
        journalId: UUID,
        journalType: String,
        referenceType: String,
        referenceId: UUID,
        currency: String,
        description: String?,
        postingsJson: String,
    ): UUID {
        try {
            return jdbcTemplate.queryForObject(
                """
                select ledger.post_journal(
                    ?::uuid,
                    ?,
                    ?,
                    ?::uuid,
                    ?,
                    ?,
                    ?::jsonb
                )
                """.trimIndent(),
                UUID::class.java,
                journalId,
                journalType,
                referenceType,
                referenceId,
                currency,
                description,
                postingsJson,
            ) ?: journalId
        } catch (exception: DataAccessException) {
            throw exception.toLedgerException()
        }
    }

    fun balance(accountId: UUID): LedgerBalanceRecord? =
        jdbcTemplate.query(
            """
            select account_id, code, currency, normal_side, balance
            from ledger.account_balances
            where account_id = ?
            """.trimIndent(),
            { rs, _ ->
                LedgerBalanceRecord(
                    accountId = rs.getObject("account_id", UUID::class.java),
                    code = rs.getString("code"),
                    currency = rs.getString("currency"),
                    normalSide = rs.getString("normal_side"),
                    balance = rs.getBigDecimal("balance"),
                )
            },
            accountId,
        ).firstOrNull()

    fun reconciliation(): LedgerReconciliationRecord =
        jdbcTemplate.queryForObject(
            """
            with journal_sums as (
                select
                    je.id,
                    coalesce(sum(p.amount) filter (where p.side = 'DEBIT'), 0) as debit_total,
                    coalesce(sum(p.amount) filter (where p.side = 'CREDIT'), 0) as credit_total,
                    count(p.id) as posting_count
                from ledger.journal_entries je
                left join ledger.postings p on p.journal_entry_id = je.id
                group by je.id
            )
            select
                (select count(*) from ledger.journal_entries) as journal_count,
                (select count(*) from ledger.postings) as posting_count,
                count(*) filter (
                    where debit_total <> credit_total
                       or posting_count < 2
                ) as imbalanced_journal_count
            from journal_sums
            """.trimIndent(),
            { rs, _ ->
                LedgerReconciliationRecord(
                    journalCount = rs.getLong("journal_count"),
                    postingCount = rs.getLong("posting_count"),
                    imbalancedJournalCount = rs.getLong("imbalanced_journal_count"),
                )
            },
        ) ?: LedgerReconciliationRecord(0, 0, 0)

    private fun ResultSet.toLedgerAccountRecord(): LedgerAccountRecord =
        LedgerAccountRecord(
            id = getObject("id", UUID::class.java),
            code = getString("code"),
            currency = getString("currency"),
            accountType = getString("account_type"),
            normalSide = getString("normal_side"),
        )

    private fun DataAccessException.toLedgerException(): LedgerException {
        val message = mostSpecificCause.message.orEmpty()
        val code = when {
            message.contains("ledger_journal_unbalanced") -> "ledger_journal_unbalanced"
            message.contains("ledger_journal_requires_two_postings") -> "ledger_journal_requires_two_postings"
            message.contains("ledger_journal_amount_required") -> "ledger_journal_amount_required"
            message.contains("ledger_posting_side_invalid") -> "ledger_posting_side_invalid"
            message.contains("ledger_posting_amount_invalid") -> "ledger_posting_amount_invalid"
            message.contains("ledger_account_missing") -> "ledger_account_missing"
            message.contains("ledger_account_currency_mismatch") -> "ledger_account_currency_mismatch"
            else -> "ledger_posting_failed"
        }
        return LedgerException(
            code = code,
            message = when (code) {
                "ledger_journal_unbalanced" -> "Ledger journal is unbalanced."
                "ledger_account_missing" -> "Ledger account is missing."
                "ledger_account_currency_mismatch" -> "Ledger account currency does not match journal currency."
                else -> "Ledger journal could not be posted."
            },
            status = HttpStatus.BAD_REQUEST,
        )
    }
}
