package com.minifin.platform.wallet

import com.minifin.platform.controls.ActorControlException
import com.minifin.platform.controls.ActorControlService
import com.minifin.platform.identity.AuditRepository
import com.minifin.platform.identity.EndUserRecord
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
        return WalletSummaryResponse(
            walletId = wallet.id.toString(),
            ledgerAccountId = wallet.ledgerAccountId.toString(),
            currency = wallet.currency,
            balance = balance,
            deposits = deposits,
        )
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
        return walletRepository.insertWallet(
            id = UUID.randomUUID(),
            userId = user.id,
            ledgerAccountId = UUID.fromString(ledgerAccount.accountId),
        )
    }

    private fun validateAmount(value: String): BigDecimal {
        val amount = runCatching { BigDecimal(value.trim()) }
            .getOrElse {
                throw WalletException(
                    code = "invalid_amount",
                    message = "Amount is invalid.",
                    status = HttpStatus.BAD_REQUEST,
                    field = "amount",
                )
            }
            .setScale(4, RoundingMode.UNNECESSARY)
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
                message = "High-value deposit requires SoF and two-eyes approval, not supported in this slice.",
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
