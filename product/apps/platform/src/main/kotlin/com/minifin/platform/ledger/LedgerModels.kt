package com.minifin.platform.ledger

import org.springframework.http.HttpStatus

data class LedgerAccountRequest(
    val code: String,
    val currency: String = "EUR",
    val accountType: String,
    val normalSide: String,
    val ownerType: String? = null,
    val ownerId: String? = null,
)

data class LedgerAccountResponse(
    val accountId: String,
    val code: String,
    val currency: String,
    val accountType: String,
    val normalSide: String,
)

data class LedgerPostingRequest(
    val accountId: String,
    val side: String,
    val amount: String,
)

data class LedgerJournalRequest(
    val journalType: String,
    val referenceType: String,
    val referenceId: String,
    val currency: String = "EUR",
    val description: String? = null,
    val postings: List<LedgerPostingRequest>,
)

data class LedgerJournalResponse(
    val journalId: String,
    val balanced: Boolean,
)

data class LedgerBalanceResponse(
    val accountId: String,
    val code: String,
    val currency: String,
    val normalSide: String,
    val balance: String,
)

data class LedgerReconciliationResponse(
    val balancedJournals: Boolean,
    val journalCount: Long,
    val postingCount: Long,
    val imbalancedJournalCount: Long,
)

class LedgerException(
    val code: String,
    override val message: String,
    val status: HttpStatus,
) : RuntimeException(message)
