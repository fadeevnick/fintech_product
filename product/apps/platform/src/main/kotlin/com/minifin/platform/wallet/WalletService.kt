package com.minifin.platform.wallet

import com.minifin.platform.controls.ActorControlException
import com.minifin.platform.controls.ActorControlService
import com.minifin.platform.identity.AuditRepository
import com.minifin.platform.identity.EndUserRecord
import com.minifin.platform.ledger.LedgerJournalRequest
import com.minifin.platform.ledger.LedgerPostingRequest
import com.minifin.platform.ledger.LedgerAccountRequest
import com.minifin.platform.ledger.LedgerService
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WalletService(
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val actorControlService: ActorControlService,
    private val auditRepository: AuditRepository,
) {
    @Transactional(noRollbackFor = [ActorControlException::class])
    fun createDeposit(user: EndUserRecord, request: DepositRequestCreate): DepositRequestResponse {
        val amount = validateAmount(request.amount)
        validateCurrency(request.currency)
        actorControlService.requireWriteAllowed("END_USER", user.id)

        val wallet = provisionWallet(user)
        val depositId = UUID.randomUUID()
        walletRepository.insertDepositRequest(
            id = depositId,
            userId = user.id,
            walletAccountId = wallet.id,
            amount = amount,
            state = "REQUESTED",
        )
        walletRepository.transitionToPendingReview(depositId)
        val record = walletRepository.findDepositRequest(depositId)
            ?: error("Deposit request disappeared after transition")

        auditRepository.write(
            eventType = "wallet.deposit_created",
            actorType = "END_USER",
            actorId = user.id,
            subjectType = "WALLET_DEPOSIT_REQUEST",
            subjectId = depositId,
            outcome = "SUCCESS",
            metadataJson = """{"state":"${record.state}","amount":"${record.amount.toPlainString()}"}""",
        )
        return record.toResponse()
    }

    @Transactional
    fun walletSummary(user: EndUserRecord): WalletSummaryResponse {
        val wallet = provisionWallet(user)
        val balance = ledgerService.balance(wallet.ledgerAccountId.toString()).balance
        val deposits = walletRepository.listDepositsForUser(user.id, limit = 50)
            .map { it.toResponse() }
        val withdrawals = walletRepository.listWithdrawalsForUser(user.id, limit = 50)
            .map { it.toResponse() }
        return WalletSummaryResponse(
            walletId = wallet.id.toString(),
            ledgerAccountId = wallet.ledgerAccountId.toString(),
            currency = wallet.currency,
            balance = balance,
            deposits = deposits,
            withdrawals = withdrawals,
        )
    }

    @Transactional(noRollbackFor = [ActorControlException::class])
    fun createWithdrawal(user: EndUserRecord, request: WithdrawalRequestCreate): WithdrawalRequestResponse {
        val amount = validateAmount(request.amount, "withdrawal")
        validateCurrency(request.currency)
        actorControlService.requireWriteAllowed("END_USER", user.id)

        val wallet = provisionWallet(user)
        val balance = BigDecimal(ledgerService.balance(wallet.ledgerAccountId.toString()).balance)
            .setScale(4, RoundingMode.UNNECESSARY)
        if (balance < amount) {
            throw WalletException(
                code = "insufficient_funds",
                message = "Wallet balance is insufficient for withdrawal.",
                status = HttpStatus.CONFLICT,
                field = "amount",
            )
        }

        val holdAccount = provisionWithdrawalHoldAccount(user)
        val withdrawalId = UUID.randomUUID()
        walletRepository.insertWithdrawalRequest(
            id = withdrawalId,
            userId = user.id,
            walletAccountId = wallet.id,
            amount = amount,
            state = "PENDING",
        )

        val amountText = amount.toPlainString()
        val holdJournal = ledgerService.postJournal(
            LedgerJournalRequest(
                journalType = "WALLET_WITHDRAW_HOLD",
                referenceType = "WALLET_WITHDRAW_REQUEST",
                referenceId = withdrawalId.toString(),
                currency = "EUR",
                description = "Manual withdrawal hold",
                postings = listOf(
                    LedgerPostingRequest(
                        accountId = wallet.ledgerAccountId.toString(),
                        side = "DEBIT",
                        amount = amountText,
                    ),
                    LedgerPostingRequest(
                        accountId = holdAccount.accountId,
                        side = "CREDIT",
                        amount = amountText,
                    ),
                ),
            ),
        )

        val updated = walletRepository.markWithdrawalHeld(
            id = withdrawalId,
            holdJournalEntryId = UUID.fromString(holdJournal.journalId),
        )
        if (updated == 0) {
            throw WalletException(
                code = "withdrawal_state_conflict",
                message = "Withdrawal request state changed concurrently.",
                status = HttpStatus.CONFLICT,
            )
        }

        auditRepository.write(
            eventType = "wallet.withdrawal_hold_created",
            actorType = "END_USER",
            actorId = user.id,
            subjectType = "WALLET_WITHDRAW_REQUEST",
            subjectId = withdrawalId,
            outcome = "SUCCESS",
            metadataJson = """{"holdJournalEntryId":"${holdJournal.journalId}","amount":"$amountText"}""",
        )

        return walletRepository.findWithdrawalRequest(withdrawalId)!!.toResponse()
    }

    fun provisionWallet(user: EndUserRecord): WalletAccountRecord {
        walletRepository.findWalletByUserId(user.id)?.let { return it }
        val ledgerAccount = ledgerService.upsertAccount(
            LedgerAccountRequest(
                code = "WALLET_USER:${user.id}",
                currency = "EUR",
                accountType = "WALLET_USER",
                normalSide = "CREDIT",
                ownerType = "END_USER",
                ownerId = user.id.toString(),
            ),
        )
        return walletRepository.upsertWallet(
            id = UUID.randomUUID(),
            userId = user.id,
            ledgerAccountId = UUID.fromString(ledgerAccount.accountId),
        )
    }

    fun provisionWithdrawalHoldAccount(user: EndUserRecord) =
        ledgerService.upsertAccount(
            LedgerAccountRequest(
                code = "WALLET_WITHDRAW_HOLD:${user.id}",
                currency = "EUR",
                accountType = "WALLET_HOLD",
                normalSide = "CREDIT",
                ownerType = "END_USER",
                ownerId = user.id.toString(),
            ),
        )

    private fun validateAmount(value: String, operation: String = "deposit"): BigDecimal {
        val amount = runCatching {
            BigDecimal(value.trim()).setScale(4, RoundingMode.UNNECESSARY)
        }
            .getOrElse {
                throw WalletException(
                    code = "invalid_amount",
                    message = "Amount is invalid.",
                    status = HttpStatus.BAD_REQUEST,
                    field = "amount",
                )
            }
        if (amount <= BigDecimal.ZERO) {
            throw WalletException(
                code = "invalid_amount",
                message = "Amount must be positive.",
                status = HttpStatus.BAD_REQUEST,
                field = "amount",
            )
        }
        if (amount >= HIGH_VALUE_THRESHOLD) {
            throw WalletException(
                code = "unsupported_high_value",
                message = "High-value $operation requires SoF and two-eyes approval, not supported in this slice.",
                status = HttpStatus.BAD_REQUEST,
                field = "amount",
            )
        }
        return amount
    }

    private fun validateCurrency(value: String) {
        if (value.trim().uppercase() != "EUR") {
            throw WalletException(
                code = "unsupported_currency",
                message = "Only EUR is supported.",
                status = HttpStatus.BAD_REQUEST,
                field = "currency",
            )
        }
    }

    companion object {
        private val HIGH_VALUE_THRESHOLD = BigDecimal("10000.0000")
    }
}

internal fun DepositRequestRecord.toResponse(): DepositRequestResponse =
    DepositRequestResponse(
        depositId = id.toString(),
        userId = userId.toString(),
        amount = amount.setScale(4, RoundingMode.UNNECESSARY).toPlainString(),
        currency = currency,
        state = state,
        reason = reason,
        journalEntryId = journalEntryId?.toString(),
        createdAt = createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        decidedAt = decidedAt?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    )

internal fun WithdrawalRequestRecord.toResponse(): WithdrawalRequestResponse =
    WithdrawalRequestResponse(
        withdrawalId = id.toString(),
        userId = userId.toString(),
        amount = amount.setScale(4, RoundingMode.UNNECESSARY).toPlainString(),
        currency = currency,
        state = state,
        reason = reason,
        holdJournalEntryId = holdJournalEntryId?.toString(),
        completionJournalEntryId = completionJournalEntryId?.toString(),
        releaseJournalEntryId = releaseJournalEntryId?.toString(),
        createdAt = createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        heldAt = heldAt?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        decidedAt = decidedAt?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    )
