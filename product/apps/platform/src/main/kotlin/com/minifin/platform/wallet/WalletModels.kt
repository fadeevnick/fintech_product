package com.minifin.platform.wallet

import org.springframework.http.HttpStatus

data class DepositRequestCreate(
    val amount: String,
    val currency: String = "EUR",
)

data class DepositRequestResponse(
    val depositId: String,
    val userId: String,
    val amount: String,
    val currency: String,
    val state: String,
    val reason: String?,
    val journalEntryId: String?,
    val sourceOfFundsRequired: Boolean,
    val sourceOfFundsSubmitted: Boolean,
    val createdAt: String,
    val decidedAt: String?,
)

data class SourceOfFundsDeclarationCreate(
    val sourceCategory: String,
    val description: String,
)

data class SourceOfFundsDeclarationResponse(
    val declarationId: String,
    val depositId: String,
    val userId: String,
    val sourceCategory: String,
    val submittedAt: String,
    val depositState: String,
)

data class WithdrawalRequestCreate(
    val amount: String,
    val currency: String = "EUR",
)

data class WithdrawalRequestResponse(
    val withdrawalId: String,
    val userId: String,
    val amount: String,
    val currency: String,
    val state: String,
    val reason: String?,
    val holdJournalEntryId: String?,
    val completionJournalEntryId: String?,
    val releaseJournalEntryId: String?,
    val createdAt: String,
    val heldAt: String?,
    val decidedAt: String?,
)

data class InternalTransferCreate(
    val receiverUserId: String,
    val amount: String,
    val currency: String = "EUR",
)

data class InternalTransferResponse(
    val transferId: String,
    val senderUserId: String,
    val receiverUserId: String,
    val amount: String,
    val currency: String,
    val state: String,
    val journalEntryId: String?,
    val createdAt: String,
    val completedAt: String?,
)

data class WalletSummaryResponse(
    val walletId: String,
    val ledgerAccountId: String,
    val currency: String,
    val balance: String,
    val deposits: List<DepositRequestResponse>,
    val withdrawals: List<WithdrawalRequestResponse>,
    val transfers: List<InternalTransferResponse>,
)

data class ManualOpsDepositDecision(
    val decision: String,
    val reason: String,
)

data class ManualOpsWithdrawalDecision(
    val decision: String,
    val reason: String,
)

class WalletException(
    val code: String,
    override val message: String,
    val status: HttpStatus,
    val field: String? = null,
) : RuntimeException(message)
