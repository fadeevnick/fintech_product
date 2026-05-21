package com.minifin.platform.wallet

import com.minifin.platform.controls.ActorControlException
import com.minifin.platform.controls.ActorControlService
import com.minifin.platform.identity.AuditRepository
import com.minifin.platform.identity.EndUserRecord
import com.minifin.platform.identity.IdentityRepository
import com.minifin.platform.ledger.LedgerJournalRequest
import com.minifin.platform.ledger.LedgerPostingRequest
import com.minifin.platform.ledger.LedgerAccountRequest
import com.minifin.platform.ledger.LedgerService
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WalletService(
    private val walletRepository: WalletRepository,
    private val identityRepository: IdentityRepository,
    private val ledgerService: LedgerService,
    private val actorControlService: ActorControlService,
    private val auditRepository: AuditRepository,
) {
    @Transactional(noRollbackFor = [ActorControlException::class])
    fun createDeposit(user: EndUserRecord, request: DepositRequestCreate): DepositRequestResponse {
        val amount = validateAmount(request.amount, allowHighValueDeposit = true)
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
        if (!requiresSourceOfFunds(amount)) {
            walletRepository.transitionToPendingReview(depositId)
        }
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

    @Transactional(noRollbackFor = [ActorControlException::class])
    fun submitSourceOfFunds(
        user: EndUserRecord,
        depositId: UUID,
        request: SourceOfFundsDeclarationCreate,
    ): SourceOfFundsDeclarationResponse {
        actorControlService.requireWriteAllowed("END_USER", user.id)
        val deposit = walletRepository.findDepositRequest(depositId)
            ?: throw WalletException(
                code = "deposit_not_found",
                message = "Deposit request was not found.",
                status = HttpStatus.NOT_FOUND,
            )
        if (deposit.userId != user.id) {
            throw WalletException(
                code = "deposit_not_found",
                message = "Deposit request was not found.",
                status = HttpStatus.NOT_FOUND,
            )
        }
        if (!requiresSourceOfFunds(deposit.amount)) {
            throw WalletException(
                code = "source_of_funds_not_required",
                message = "Source of Funds is not required for this deposit.",
                status = HttpStatus.BAD_REQUEST,
            )
        }
        if (deposit.state != "REQUESTED") {
            throw WalletException(
                code = "deposit_state_conflict",
                message = "Deposit request is not awaiting Source of Funds.",
                status = HttpStatus.CONFLICT,
            )
        }
        val sourceCategory = validateSourceCategory(request.sourceCategory)
        val description = validateSofDescription(request.description)
        val declarationId = UUID.randomUUID()
        walletRepository.insertSourceOfFundsDeclaration(
            id = declarationId,
            depositRequestId = deposit.id,
            userId = user.id,
            sourceCategory = sourceCategory,
            description = description,
        )
        walletRepository.transitionToPendingReview(deposit.id)
        val declaration = walletRepository.findSourceOfFundsDeclaration(deposit.id)
            ?: error("Source of Funds declaration disappeared after insert")
        val updatedDeposit = walletRepository.findDepositRequest(deposit.id)
            ?: error("Deposit request disappeared after SoF transition")
        auditRepository.write(
            eventType = "wallet.source_of_funds_submitted",
            actorType = "END_USER",
            actorId = user.id,
            subjectType = "WALLET_DEPOSIT_REQUEST",
            subjectId = deposit.id,
            outcome = "SUCCESS",
            metadataJson = """{"declarationId":"${declaration.id}","sourceCategory":"$sourceCategory","amount":"${deposit.amount.toPlainString()}"}""",
        )
        return SourceOfFundsDeclarationResponse(
            declarationId = declaration.id.toString(),
            depositId = deposit.id.toString(),
            userId = user.id.toString(),
            sourceCategory = declaration.sourceCategory,
            submittedAt = declaration.submittedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            depositState = updatedDeposit.state,
        )
    }

    @Transactional
    fun walletSummary(user: EndUserRecord): WalletSummaryResponse {
        val wallet = provisionWallet(user)
        val balance = ledgerService.balance(wallet.ledgerAccountId.toString()).balance
        val deposits = walletRepository.listDepositsForUser(user.id, limit = 50)
            .map { it.toResponse() }
        val withdrawals = walletRepository.listWithdrawalsForUser(user.id, limit = 50)
            .map { it.toResponse() }
        val transfers = walletRepository.listInternalTransfersForUser(user.id, limit = 50)
            .map { it.toResponse() }
        return WalletSummaryResponse(
            walletId = wallet.id.toString(),
            ledgerAccountId = wallet.ledgerAccountId.toString(),
            currency = wallet.currency,
            balance = balance,
            deposits = deposits,
            withdrawals = withdrawals,
            transfers = transfers,
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

    @Transactional(noRollbackFor = [ActorControlException::class])
    fun createInternalTransfer(
        sender: EndUserRecord,
        request: InternalTransferCreate,
        idempotencyKey: String?,
    ): InternalTransferResponse {
        val amount = validateAmount(request.amount, "transfer")
        validateCurrency(request.currency)
        val receiverId = requireUuid(request.receiverUserId, "receiverUserId")
        if (receiverId == sender.id) {
            throw WalletException(
                code = "self_transfer_not_allowed",
                message = "Sender and receiver must be different users.",
                status = HttpStatus.BAD_REQUEST,
                field = "receiverUserId",
            )
        }
        val receiver = identityRepository.findEndUserById(receiverId)
            ?: throw WalletException(
                code = "receiver_not_found",
                message = "Receiver user was not found.",
                status = HttpStatus.NOT_FOUND,
                field = "receiverUserId",
            )
        if (receiver.status != "ACTIVE") {
            throw WalletException(
                code = "receiver_not_active",
                message = "Receiver user is not active.",
                status = HttpStatus.CONFLICT,
                field = "receiverUserId",
            )
        }

        val normalizedIdempotencyKey = validateIdempotencyKey(idempotencyKey)
        val requestFingerprint = transferFingerprint(receiverId, amount)
        if (normalizedIdempotencyKey != null) {
            walletRepository.findInternalTransferByIdempotencyKey(sender.id, normalizedIdempotencyKey)
                ?.let { return idempotentTransferResponse(it, requestFingerprint) }
        }

        actorControlService.requireWriteAllowed("END_USER", sender.id)
        actorControlService.requireWriteAllowed("END_USER", receiverId)

        val senderWallet = provisionWallet(sender)
        val receiverWallet = provisionWallet(receiver)
        val senderBalance = BigDecimal(ledgerService.balance(senderWallet.ledgerAccountId.toString()).balance)
            .setScale(4, RoundingMode.UNNECESSARY)
        if (senderBalance < amount) {
            throw WalletException(
                code = "insufficient_funds",
                message = "Wallet balance is insufficient for transfer.",
                status = HttpStatus.CONFLICT,
                field = "amount",
            )
        }

        val transferId = UUID.randomUUID()
        val inserted = walletRepository.insertInternalTransferPending(
            id = transferId,
            senderUserId = sender.id,
            receiverUserId = receiverId,
            senderWalletAccountId = senderWallet.id,
            receiverWalletAccountId = receiverWallet.id,
            amount = amount,
            idempotencyKey = normalizedIdempotencyKey,
            requestFingerprint = requestFingerprint,
        )
        if (inserted == 0) {
            val existing = walletRepository.findInternalTransferByIdempotencyKey(
                sender.id,
                normalizedIdempotencyKey ?: error("Idempotency key missing after conflict"),
            ) ?: throw WalletException(
                code = "transfer_state_conflict",
                message = "Transfer request state changed concurrently.",
                status = HttpStatus.CONFLICT,
            )
            return idempotentTransferResponse(existing, requestFingerprint)
        }

        val amountText = amount.toPlainString()
        val journal = ledgerService.postJournal(
            LedgerJournalRequest(
                journalType = "WALLET_INTERNAL_TRANSFER",
                referenceType = "WALLET_INTERNAL_TRANSFER",
                referenceId = transferId.toString(),
                currency = "EUR",
                description = "End-user internal wallet transfer",
                postings = listOf(
                    LedgerPostingRequest(
                        accountId = senderWallet.ledgerAccountId.toString(),
                        side = "DEBIT",
                        amount = amountText,
                    ),
                    LedgerPostingRequest(
                        accountId = receiverWallet.ledgerAccountId.toString(),
                        side = "CREDIT",
                        amount = amountText,
                    ),
                ),
            ),
        )

        val updated = walletRepository.markInternalTransferCompleted(
            id = transferId,
            journalEntryId = UUID.fromString(journal.journalId),
        )
        if (updated == 0) {
            throw WalletException(
                code = "transfer_state_conflict",
                message = "Transfer request state changed concurrently.",
                status = HttpStatus.CONFLICT,
            )
        }

        auditRepository.write(
            eventType = "wallet.internal_transfer_completed",
            actorType = "END_USER",
            actorId = sender.id,
            subjectType = "WALLET_INTERNAL_TRANSFER",
            subjectId = transferId,
            outcome = "SUCCESS",
            metadataJson = """{"journalEntryId":"${journal.journalId}","receiverUserId":"$receiverId","amount":"$amountText"}""",
        )

        return walletRepository.findInternalTransfer(transferId)!!.toResponse()
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

    private fun validateAmount(value: String, operation: String = "deposit", allowHighValueDeposit: Boolean = false): BigDecimal {
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
        if (allowHighValueDeposit && amount > MAX_DEPOSIT_AMOUNT) {
            throw WalletException(
                code = "invalid_amount",
                message = "Deposit amount exceeds the supported local limit.",
                status = HttpStatus.BAD_REQUEST,
                field = "amount",
            )
        }
        if (amount >= HIGH_VALUE_THRESHOLD && !allowHighValueDeposit) {
            throw WalletException(
                code = "unsupported_high_value",
                message = "High-value $operation requires SoF and two-eyes approval, not supported in this slice.",
                status = HttpStatus.BAD_REQUEST,
                field = "amount",
            )
        }
        return amount
    }

    private fun requiresSourceOfFunds(amount: BigDecimal): Boolean =
        amount > HIGH_VALUE_THRESHOLD

    private fun validateSourceCategory(value: String): String {
        val normalized = value.trim().uppercase()
        if (normalized !in setOf("SALARY", "BUSINESS_INCOME", "SAVINGS", "INVESTMENT", "OTHER")) {
            throw WalletException(
                code = "invalid_source_category",
                message = "Source category is invalid.",
                status = HttpStatus.BAD_REQUEST,
                field = "sourceCategory",
            )
        }
        return normalized
    }

    private fun validateSofDescription(value: String): String {
        val trimmed = value.trim()
        if (trimmed.length < 20 || trimmed.length > 1000) {
            throw WalletException(
                code = "invalid_source_description",
                message = "Source description must be between 20 and 1000 characters.",
                status = HttpStatus.BAD_REQUEST,
                field = "description",
            )
        }
        return trimmed
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

    private fun validateIdempotencyKey(value: String?): String? {
        val trimmed = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (trimmed.length > 120) {
            throw WalletException(
                code = "invalid_idempotency_key",
                message = "Idempotency key is invalid.",
                status = HttpStatus.BAD_REQUEST,
            )
        }
        return trimmed
    }

    private fun requireUuid(value: String, field: String): UUID =
        runCatching { UUID.fromString(value.trim()) }
            .getOrElse {
                throw WalletException(
                    code = "invalid_uuid",
                    message = "Invalid UUID.",
                    status = HttpStatus.BAD_REQUEST,
                    field = field,
                )
            }

    private fun transferFingerprint(receiverUserId: UUID, amount: BigDecimal): String {
        val normalized = "$receiverUserId|${amount.toPlainString()}|EUR"
        val bytes = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun idempotentTransferResponse(
        existing: InternalTransferRecord,
        requestFingerprint: String,
    ): InternalTransferResponse {
        if (existing.requestFingerprint != requestFingerprint) {
            throw WalletException(
                code = "transfer_idempotency_conflict",
                message = "Idempotency key was already used with a different transfer request.",
                status = HttpStatus.CONFLICT,
            )
        }
        if (existing.state != "COMPLETED" || existing.journalEntryId == null) {
            throw WalletException(
                code = "transfer_in_progress",
                message = "Transfer request is still being processed.",
                status = HttpStatus.CONFLICT,
            )
        }
        return existing.toResponse()
    }

    companion object {
        private val HIGH_VALUE_THRESHOLD = BigDecimal("10000.0000")
        private val MAX_DEPOSIT_AMOUNT = BigDecimal("1000000.0000")
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
        sourceOfFundsRequired = amount > BigDecimal("10000.0000"),
        sourceOfFundsSubmitted = sourceOfFundsSubmitted,
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

internal fun InternalTransferRecord.toResponse(): InternalTransferResponse =
    InternalTransferResponse(
        transferId = id.toString(),
        senderUserId = senderUserId.toString(),
        receiverUserId = receiverUserId.toString(),
        amount = amount.setScale(4, RoundingMode.UNNECESSARY).toPlainString(),
        currency = currency,
        state = state,
        journalEntryId = journalEntryId?.toString(),
        createdAt = createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        completedAt = completedAt?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    )
