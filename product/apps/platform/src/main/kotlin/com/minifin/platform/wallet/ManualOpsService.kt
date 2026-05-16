package com.minifin.platform.wallet

import com.minifin.platform.backoffice.BackofficePrincipal
import com.minifin.platform.controls.ActorControlException
import com.minifin.platform.controls.ActorControlService
import com.minifin.platform.controls.ReadAuditRepository
import com.minifin.platform.identity.AuditRepository
import com.minifin.platform.ledger.LedgerJournalRequest
import com.minifin.platform.ledger.LedgerPostingRequest
import com.minifin.platform.ledger.LedgerRepository
import com.minifin.platform.ledger.LedgerService
import java.math.RoundingMode
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ManualOpsService(
    private val walletRepository: WalletRepository,
    private val ledgerRepository: LedgerRepository,
    private val ledgerService: LedgerService,
    private val actorControlService: ActorControlService,
    private val auditRepository: AuditRepository,
    private val readAuditRepository: ReadAuditRepository,
) {
    @Transactional
    fun listPendingDeposits(
        principal: BackofficePrincipal,
        limit: Int,
    ): List<DepositRequestResponse> {
        val pending = walletRepository.listPendingDeposits(limit)
        pending.forEach { record ->
            readAuditRepository.write(
                actorType = "BACKOFFICE",
                actorId = principal.subjectUuid,
                actorReference = principal.subject,
                subjectType = "END_USER",
                subjectId = record.userId,
                resourceType = "WALLET_DEPOSIT_REQUEST",
                resourceId = record.id,
                purpose = "manual_ops_review",
                decision = "ALLOW",
                metadataJson = """{"queue":"manual_deposits"}""",
            )
        }
        return pending.map { it.toResponse() }
    }

    @Transactional
    fun listHeldWithdrawals(
        principal: BackofficePrincipal,
        limit: Int,
    ): List<WithdrawalRequestResponse> {
        val held = walletRepository.listHeldWithdrawals(limit)
        held.forEach { record ->
            readAuditRepository.write(
                actorType = "BACKOFFICE",
                actorId = principal.subjectUuid,
                actorReference = principal.subject,
                subjectType = "END_USER",
                subjectId = record.userId,
                resourceType = "WALLET_WITHDRAW_REQUEST",
                resourceId = record.id,
                purpose = "manual_ops_review",
                decision = "ALLOW",
                metadataJson = """{"queue":"manual_withdrawals"}""",
            )
        }
        return held.map { it.toResponse() }
    }

    @Transactional(noRollbackFor = [ActorControlException::class])
    fun decide(
        depositId: UUID,
        request: ManualOpsDepositDecision,
        principal: BackofficePrincipal,
    ): DepositRequestResponse {
        val reason = validateReason(request.reason)
        val decision = request.decision.trim().uppercase()
        val deposit = walletRepository.findDepositRequest(depositId)
            ?: throw WalletException(
                code = "deposit_not_found",
                message = "Deposit request not found.",
                status = HttpStatus.NOT_FOUND,
            )

        if (deposit.state !in setOf("REQUESTED", "PENDING_OPERATOR_REVIEW")) {
            throw WalletException(
                code = "deposit_already_decided",
                message = "Deposit request is in a terminal state.",
                status = HttpStatus.CONFLICT,
            )
        }

        return when (decision) {
            "APPROVE" -> approve(deposit, reason, principal)
            "REJECT" -> reject(deposit, reason, principal)
            else -> throw WalletException(
                code = "invalid_decision",
                message = "Decision must be APPROVE or REJECT.",
                status = HttpStatus.BAD_REQUEST,
                field = "decision",
            )
        }
    }

    @Transactional(noRollbackFor = [ActorControlException::class])
    fun decideWithdrawal(
        withdrawalId: UUID,
        request: ManualOpsWithdrawalDecision,
        principal: BackofficePrincipal,
    ): WithdrawalRequestResponse {
        val reason = validateReason(request.reason)
        val decision = request.decision.trim().uppercase()
        val withdrawal = walletRepository.findWithdrawalRequest(withdrawalId)
            ?: throw WalletException(
                code = "withdrawal_not_found",
                message = "Withdrawal request not found.",
                status = HttpStatus.NOT_FOUND,
            )

        if (withdrawal.state != "HELD") {
            throw WalletException(
                code = "withdrawal_already_decided",
                message = "Withdrawal request is not held for operator decision.",
                status = HttpStatus.CONFLICT,
            )
        }

        return when (decision) {
            "COMPLETE" -> completeWithdrawal(withdrawal, reason, principal)
            "REJECT" -> rejectWithdrawal(withdrawal, reason, principal)
            else -> throw WalletException(
                code = "invalid_decision",
                message = "Decision must be COMPLETE or REJECT.",
                status = HttpStatus.BAD_REQUEST,
                field = "decision",
            )
        }
    }

    private fun approve(
        deposit: DepositRequestRecord,
        reason: String,
        principal: BackofficePrincipal,
    ): DepositRequestResponse {
        actorControlService.requireWriteAllowed("END_USER", deposit.userId)

        val clearing = ledgerRepository.findAccountByCode("EXTERNAL_DEPOSIT_CLEARING")
            ?: throw WalletException(
                code = "clearing_account_missing",
                message = "External clearing ledger account is missing.",
                status = HttpStatus.INTERNAL_SERVER_ERROR,
            )
        val wallet = walletRepository.findWalletByUserId(deposit.userId)
            ?: throw WalletException(
                code = "wallet_missing",
                message = "Wallet for deposit owner is missing.",
                status = HttpStatus.INTERNAL_SERVER_ERROR,
            )

        val amountText = deposit.amount.setScale(4, RoundingMode.UNNECESSARY).toPlainString()
        val journal = ledgerService.postJournal(
            LedgerJournalRequest(
                journalType = "WALLET_DEPOSIT",
                referenceType = "WALLET_DEPOSIT_REQUEST",
                referenceId = deposit.id.toString(),
                currency = "EUR",
                description = "Manual deposit approve",
                postings = listOf(
                    LedgerPostingRequest(
                        accountId = clearing.id.toString(),
                        side = "DEBIT",
                        amount = amountText,
                    ),
                    LedgerPostingRequest(
                        accountId = wallet.ledgerAccountId.toString(),
                        side = "CREDIT",
                        amount = amountText,
                    ),
                ),
            ),
        )

        val updated = walletRepository.markCompleted(
            id = deposit.id,
            journalEntryId = UUID.fromString(journal.journalId),
            reason = reason,
            decidedByActorType = "BACKOFFICE",
            decidedByActorId = principal.subjectUuid,
            decidedByReference = principal.subject,
        )
        if (updated == 0) {
            throw WalletException(
                code = "deposit_state_conflict",
                message = "Deposit request state changed concurrently.",
                status = HttpStatus.CONFLICT,
            )
        }

        auditRepository.write(
            eventType = "wallet.deposit_approved",
            actorType = "BACKOFFICE",
            actorId = principal.subjectUuid,
            subjectType = "WALLET_DEPOSIT_REQUEST",
            subjectId = deposit.id,
            outcome = "SUCCESS",
            metadataJson = """{"journalEntryId":"${journal.journalId}","amount":"${amountText}"}""",
        )

        return walletRepository.findDepositRequest(deposit.id)!!.toResponse()
    }

    private fun reject(
        deposit: DepositRequestRecord,
        reason: String,
        principal: BackofficePrincipal,
    ): DepositRequestResponse {
        val updated = walletRepository.markRejected(
            id = deposit.id,
            reason = reason,
            decidedByActorType = "BACKOFFICE",
            decidedByActorId = principal.subjectUuid,
            decidedByReference = principal.subject,
        )
        if (updated == 0) {
            throw WalletException(
                code = "deposit_state_conflict",
                message = "Deposit request state changed concurrently.",
                status = HttpStatus.CONFLICT,
            )
        }
        auditRepository.write(
            eventType = "wallet.deposit_rejected",
            actorType = "BACKOFFICE",
            actorId = principal.subjectUuid,
            subjectType = "WALLET_DEPOSIT_REQUEST",
            subjectId = deposit.id,
            outcome = "SUCCESS",
            metadataJson = """{"reasonCode":"${reason.take(80)}"}""",
        )
        return walletRepository.findDepositRequest(deposit.id)!!.toResponse()
    }

    private fun completeWithdrawal(
        withdrawal: WithdrawalRequestRecord,
        reason: String,
        principal: BackofficePrincipal,
    ): WithdrawalRequestResponse {
        actorControlService.requireWriteAllowed("END_USER", withdrawal.userId)

        val clearing = ledgerRepository.findAccountByCode("EXTERNAL_WITHDRAWAL_CLEARING")
            ?: throw WalletException(
                code = "clearing_account_missing",
                message = "External withdrawal clearing ledger account is missing.",
                status = HttpStatus.INTERNAL_SERVER_ERROR,
            )
        val holdAccount = ledgerRepository.findAccountByCode("WALLET_WITHDRAW_HOLD:${withdrawal.userId}")
            ?: throw WalletException(
                code = "hold_account_missing",
                message = "Withdrawal hold ledger account is missing.",
                status = HttpStatus.INTERNAL_SERVER_ERROR,
            )

        val amountText = withdrawal.amount.setScale(4, RoundingMode.UNNECESSARY).toPlainString()
        val journal = ledgerService.postJournal(
            LedgerJournalRequest(
                journalType = "WALLET_WITHDRAW_COMPLETE",
                referenceType = "WALLET_WITHDRAW_REQUEST",
                referenceId = withdrawal.id.toString(),
                currency = "EUR",
                description = "Manual withdrawal complete",
                postings = listOf(
                    LedgerPostingRequest(
                        accountId = holdAccount.id.toString(),
                        side = "DEBIT",
                        amount = amountText,
                    ),
                    LedgerPostingRequest(
                        accountId = clearing.id.toString(),
                        side = "CREDIT",
                        amount = amountText,
                    ),
                ),
            ),
        )

        val updated = walletRepository.markWithdrawalCompleted(
            id = withdrawal.id,
            completionJournalEntryId = UUID.fromString(journal.journalId),
            reason = reason,
            decidedByActorType = "BACKOFFICE",
            decidedByActorId = principal.subjectUuid,
            decidedByReference = principal.subject,
        )
        if (updated == 0) {
            throw WalletException(
                code = "withdrawal_state_conflict",
                message = "Withdrawal request state changed concurrently.",
                status = HttpStatus.CONFLICT,
            )
        }

        auditRepository.write(
            eventType = "wallet.withdrawal_completed",
            actorType = "BACKOFFICE",
            actorId = principal.subjectUuid,
            subjectType = "WALLET_WITHDRAW_REQUEST",
            subjectId = withdrawal.id,
            outcome = "SUCCESS",
            metadataJson = """{"journalEntryId":"${journal.journalId}","amount":"$amountText"}""",
        )

        return walletRepository.findWithdrawalRequest(withdrawal.id)!!.toResponse()
    }

    private fun rejectWithdrawal(
        withdrawal: WithdrawalRequestRecord,
        reason: String,
        principal: BackofficePrincipal,
    ): WithdrawalRequestResponse {
        val wallet = walletRepository.findWalletByUserId(withdrawal.userId)
            ?: throw WalletException(
                code = "wallet_missing",
                message = "Wallet for withdrawal owner is missing.",
                status = HttpStatus.INTERNAL_SERVER_ERROR,
            )
        val holdAccount = ledgerRepository.findAccountByCode("WALLET_WITHDRAW_HOLD:${withdrawal.userId}")
            ?: throw WalletException(
                code = "hold_account_missing",
                message = "Withdrawal hold ledger account is missing.",
                status = HttpStatus.INTERNAL_SERVER_ERROR,
            )

        val amountText = withdrawal.amount.setScale(4, RoundingMode.UNNECESSARY).toPlainString()
        val journal = ledgerService.postJournal(
            LedgerJournalRequest(
                journalType = "WALLET_WITHDRAW_RELEASE",
                referenceType = "WALLET_WITHDRAW_REQUEST",
                referenceId = withdrawal.id.toString(),
                currency = "EUR",
                description = "Manual withdrawal release",
                postings = listOf(
                    LedgerPostingRequest(
                        accountId = holdAccount.id.toString(),
                        side = "DEBIT",
                        amount = amountText,
                    ),
                    LedgerPostingRequest(
                        accountId = wallet.ledgerAccountId.toString(),
                        side = "CREDIT",
                        amount = amountText,
                    ),
                ),
            ),
        )

        val updated = walletRepository.markWithdrawalRejected(
            id = withdrawal.id,
            releaseJournalEntryId = UUID.fromString(journal.journalId),
            reason = reason,
            decidedByActorType = "BACKOFFICE",
            decidedByActorId = principal.subjectUuid,
            decidedByReference = principal.subject,
        )
        if (updated == 0) {
            throw WalletException(
                code = "withdrawal_state_conflict",
                message = "Withdrawal request state changed concurrently.",
                status = HttpStatus.CONFLICT,
            )
        }

        auditRepository.write(
            eventType = "wallet.withdrawal_rejected",
            actorType = "BACKOFFICE",
            actorId = principal.subjectUuid,
            subjectType = "WALLET_WITHDRAW_REQUEST",
            subjectId = withdrawal.id,
            outcome = "SUCCESS",
            metadataJson = """{"releaseJournalEntryId":"${journal.journalId}","reasonCode":"${reason.take(80)}"}""",
        )

        return walletRepository.findWithdrawalRequest(withdrawal.id)!!.toResponse()
    }

    private fun validateReason(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank() || trimmed.length > 200) {
            throw WalletException(
                code = "invalid_reason",
                message = "Reason is required.",
                status = HttpStatus.BAD_REQUEST,
                field = "reason",
            )
        }
        return trimmed
    }
}
