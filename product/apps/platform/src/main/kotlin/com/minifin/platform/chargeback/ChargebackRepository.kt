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
    val cardholderWalletLedgerAccountId: UUID?,
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
    val provisionalCreditJournalId: UUID?,
    val arbitrationJournalId: UUID?,
    val createdAt: OffsetDateTime,
)

data class EvidenceSubmissionRecord(
    val id: UUID,
    val disputeId: UUID,
    val merchantId: UUID,
    val submittedByEmployeeId: UUID,
    val narrative: String,
    val createdAt: OffsetDateTime,
)

data class EvidenceAttachmentRecord(
    val id: UUID,
    val evidenceSubmissionId: UUID,
    val fileName: String,
    val contentType: String,
    val storageKey: String,
    val sizeBytes: Long,
)

@Repository
class ChargebackRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun findPayment(id: UUID): DisputablePaymentRecord? =
        jdbcTemplate.query(
            """
            select pi.id, pi.merchant_id, pi.amount, pi.currency, pi.state, pi.captured_at,
                   si.created_at as settled_at, ic.end_user_id as cardholder_user_id,
                   wa.ledger_account_id as cardholder_wallet_ledger_account_id
              from merchant.payment_intents pi
              left join settlement.settlement_items si on si.payment_intent_id = pi.id
              left join cards.issued_cards ic on ic.card_token = pi.card_token
              left join wallet.wallet_accounts wa on wa.id = ic.wallet_account_id
             where pi.id = ?
            """.trimIndent(),
            { rs, _ -> rs.toPaymentRecord() },
            id,
        ).firstOrNull()

    fun findDisputeByPaymentIntent(paymentIntentId: UUID): ChargebackDisputeRecord? =
        jdbcTemplate.query(
            """
            select id, payment_intent_id, merchant_id, cardholder_user_id, amount, currency, reason_code,
                   narrative, state, merchant_response_deadline, provisional_credit_journal_id, arbitration_journal_id, created_at
              from chargeback.disputes
             where payment_intent_id = ?
            """.trimIndent(),
            { rs, _ -> rs.toDisputeRecord() },
            paymentIntentId,
        ).firstOrNull()

    fun findDisputeById(id: UUID): ChargebackDisputeRecord? =
        jdbcTemplate.query(
            """
            select id, payment_intent_id, merchant_id, cardholder_user_id, amount, currency, reason_code,
                   narrative, state, merchant_response_deadline, provisional_credit_journal_id, arbitration_journal_id, created_at
              from chargeback.disputes
             where id = ?
            """.trimIndent(),
            { rs, _ -> rs.toDisputeRecord() },
            id,
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

    fun markProvisionalCreditJournal(id: UUID, journalId: UUID): Int = jdbcTemplate.update(
        """
        update chargeback.disputes
           set provisional_credit_journal_id = ?,
               updated_at = now(),
               version = version + 1
         where id = ?
           and provisional_credit_journal_id is null
        """.trimIndent(),
        journalId,
        id,
    )

    fun accountByCode(code: String): UUID? =
        jdbcTemplate.query(
            """
            select id
            from ledger.accounts
            where code = ?
            """.trimIndent(),
            { rs, _ -> rs.getObject("id", UUID::class.java) },
            code,
        ).firstOrNull()

    fun cardholderWalletLedgerAccountId(disputeId: UUID): UUID? =
        jdbcTemplate.query(
            """
            select wa.ledger_account_id
              from chargeback.disputes d
              join cards.issued_cards ic on ic.end_user_id = d.cardholder_user_id
              join merchant.payment_intents pi on pi.id = d.payment_intent_id and pi.card_token = ic.card_token
              join wallet.wallet_accounts wa on wa.id = ic.wallet_account_id
             where d.id = ?
            """.trimIndent(),
            { rs, _ -> rs.getObject("ledger_account_id", UUID::class.java) },
            disputeId,
        ).firstOrNull()

    fun insertEvidenceSubmission(id: UUID, disputeId: UUID, merchantId: UUID, employeeId: UUID, narrative: String): Int =
        jdbcTemplate.update(
            """
            insert into chargeback.evidence_submissions (
                id, dispute_id, merchant_id, submitted_by_employee_id, narrative
            )
            values (?, ?, ?, ?, ?)
            on conflict (dispute_id) do nothing
            """.trimIndent(),
            id,
            disputeId,
            merchantId,
            employeeId,
            narrative,
        )

    fun insertEvidenceAttachment(
        id: UUID,
        evidenceSubmissionId: UUID,
        fileName: String,
        contentType: String,
        storageKey: String,
        sizeBytes: Long,
    ) {
        jdbcTemplate.update(
            """
            insert into chargeback.evidence_attachments (
                id, evidence_submission_id, file_name, content_type, storage_key, size_bytes
            )
            values (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            evidenceSubmissionId,
            fileName,
            contentType,
            storageKey,
            sizeBytes,
        )
    }

    fun markEvidenceSubmitted(disputeId: UUID): Int = jdbcTemplate.update(
        """
        update chargeback.disputes
           set state = 'EVIDENCE_SUBMITTED',
               updated_at = now(),
               version = version + 1
         where id = ?
           and state = 'MERCHANT_NOTIFIED'
        """.trimIndent(),
        disputeId,
    )

    fun markArbitrationWon(
        disputeId: UUID,
        rationale: String,
        decidedBySubject: String,
        decidedByRole: String?,
        journalId: UUID,
    ): Int = jdbcTemplate.update(
        """
        update chargeback.disputes
           set state = 'WON',
               arbitration_outcome = 'WON',
               arbitration_rationale = ?,
               arbitration_decided_by_subject = ?,
               arbitration_decided_by_role = ?,
               arbitration_decided_at = now(),
               arbitration_journal_id = ?,
               updated_at = now(),
               version = version + 1
         where id = ?
           and state = 'EVIDENCE_SUBMITTED'
           and arbitration_journal_id is null
        """.trimIndent(),
        rationale,
        decidedBySubject,
        decidedByRole,
        journalId,
        disputeId,
    )

    fun markArbitrationLost(
        disputeId: UUID,
        rationale: String,
        decidedBySubject: String,
        decidedByRole: String?,
        journalId: UUID,
    ): Int = jdbcTemplate.update(
        """
        update chargeback.disputes
           set state = 'LOST',
               arbitration_outcome = 'LOST',
               arbitration_rationale = ?,
               arbitration_decided_by_subject = ?,
               arbitration_decided_by_role = ?,
               arbitration_decided_at = now(),
               arbitration_journal_id = ?,
               updated_at = now(),
               version = version + 1
         where id = ?
           and state = 'EVIDENCE_SUBMITTED'
           and arbitration_journal_id is null
        """.trimIndent(),
        rationale,
        decidedBySubject,
        decidedByRole,
        journalId,
        disputeId,
    )

    fun markMerchantAccepted(disputeId: UUID, employeeId: UUID, journalId: UUID): Int = jdbcTemplate.update(
        """
        update chargeback.disputes
           set state = 'MERCHANT_ACCEPTED',
               merchant_acceptance_journal_id = ?,
               merchant_accepted_by_employee_id = ?,
               merchant_accepted_at = now(),
               updated_at = now(),
               version = version + 1
         where id = ?
           and state = 'MERCHANT_NOTIFIED'
           and merchant_acceptance_journal_id is null
        """.trimIndent(),
        journalId,
        employeeId,
        disputeId,
    )

    fun findEvidenceSubmission(disputeId: UUID): EvidenceSubmissionRecord? =
        jdbcTemplate.query(
            """
            select id, dispute_id, merchant_id, submitted_by_employee_id, narrative, created_at
              from chargeback.evidence_submissions
             where dispute_id = ?
            """.trimIndent(),
            { rs, _ -> rs.toEvidenceSubmissionRecord() },
            disputeId,
        ).firstOrNull()

    fun listEvidenceAttachments(evidenceSubmissionId: UUID): List<EvidenceAttachmentRecord> =
        jdbcTemplate.query(
            """
            select id, evidence_submission_id, file_name, content_type, storage_key, size_bytes
              from chargeback.evidence_attachments
             where evidence_submission_id = ?
             order by created_at asc, id asc
            """.trimIndent(),
            { rs, _ -> rs.toEvidenceAttachmentRecord() },
            evidenceSubmissionId,
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
            cardholderWalletLedgerAccountId = getObject("cardholder_wallet_ledger_account_id", UUID::class.java),
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
            provisionalCreditJournalId = getObject("provisional_credit_journal_id", UUID::class.java),
            arbitrationJournalId = getObject("arbitration_journal_id", UUID::class.java),
            createdAt = getObject("created_at", OffsetDateTime::class.java),
        )

    private fun ResultSet.toEvidenceSubmissionRecord(): EvidenceSubmissionRecord =
        EvidenceSubmissionRecord(
            id = getObject("id", UUID::class.java),
            disputeId = getObject("dispute_id", UUID::class.java),
            merchantId = getObject("merchant_id", UUID::class.java),
            submittedByEmployeeId = getObject("submitted_by_employee_id", UUID::class.java),
            narrative = getString("narrative"),
            createdAt = getObject("created_at", OffsetDateTime::class.java),
        )

    private fun ResultSet.toEvidenceAttachmentRecord(): EvidenceAttachmentRecord =
        EvidenceAttachmentRecord(
            id = getObject("id", UUID::class.java),
            evidenceSubmissionId = getObject("evidence_submission_id", UUID::class.java),
            fileName = getString("file_name"),
            contentType = getString("content_type"),
            storageKey = getString("storage_key"),
            sizeBytes = getLong("size_bytes"),
        )
}
