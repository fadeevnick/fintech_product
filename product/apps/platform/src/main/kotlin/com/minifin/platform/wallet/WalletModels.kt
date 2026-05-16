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
    val createdAt: String,
    val decidedAt: String?,
)

data class WalletSummaryResponse(
    val walletId: String,
    val ledgerAccountId: String,
    val currency: String,
    val balance: String,
    val deposits: List<DepositRequestResponse>,
)

data class ManualOpsDepositDecision(
    val decision: String,
    val reason: String,
)

class WalletException(
    val code: String,
    override val message: String,
    val status: HttpStatus,
    val field: String? = null,
) : RuntimeException(message)
