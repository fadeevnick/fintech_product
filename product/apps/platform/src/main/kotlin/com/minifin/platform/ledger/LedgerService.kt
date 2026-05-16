package com.minifin.platform.ledger

import com.fasterxml.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LedgerService(
    private val repository: LedgerRepository,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun upsertAccount(request: LedgerAccountRequest): LedgerAccountResponse {
        val code = validateText(request.code, "code", 120)
        val currency = validateCurrency(request.currency)
        val accountType = validateText(request.accountType, "accountType", 80)
        val normalSide = validateSide(request.normalSide)
        val ownerType = request.ownerType?.trim()?.takeIf { it.isNotBlank() }
        val ownerId = request.ownerId?.trim()?.takeIf { it.isNotBlank() }?.let { requireUuid(it, "ownerId") }

        return repository.upsertAccount(
            id = UUID.randomUUID(),
            code = code,
            currency = currency,
            accountType = accountType,
            normalSide = normalSide,
            ownerType = ownerType,
            ownerId = ownerId,
        ).toResponse()
    }

    @Transactional
    fun postJournal(request: LedgerJournalRequest): LedgerJournalResponse {
        val journalId = UUID.randomUUID()
        val referenceId = requireUuid(request.referenceId, "referenceId")
        val currency = validateCurrency(request.currency)
        val journalType = validateText(request.journalType, "journalType", 80)
        val referenceType = validateText(request.referenceType, "referenceType", 80)
        if (request.postings.size < 2) {
            throw LedgerException(
                code = "ledger_journal_requires_two_postings",
                message = "Ledger journal requires at least two postings.",
                status = HttpStatus.BAD_REQUEST,
            )
        }

        val postings = request.postings.map {
            mapOf(
                "postingId" to UUID.randomUUID().toString(),
                "accountId" to requireUuid(it.accountId, "accountId").toString(),
                "side" to validateSide(it.side),
                "amount" to validateAmount(it.amount).toPlainString(),
            )
        }

        repository.postJournal(
            journalId = journalId,
            journalType = journalType,
            referenceType = referenceType,
            referenceId = referenceId,
            currency = currency,
            description = request.description?.trim()?.takeIf { it.isNotBlank() },
            postingsJson = objectMapper.writeValueAsString(postings),
        )
        return LedgerJournalResponse(journalId.toString(), balanced = true)
    }

    fun balance(accountId: String): LedgerBalanceResponse {
        val accountUuid = requireUuid(accountId, "accountId")
        val balance = repository.balance(accountUuid)
            ?: throw LedgerException(
                code = "ledger_account_missing",
                message = "Ledger account is missing.",
                status = HttpStatus.NOT_FOUND,
            )
        return LedgerBalanceResponse(
            accountId = balance.accountId.toString(),
            code = balance.code,
            currency = balance.currency,
            normalSide = balance.normalSide,
            balance = balance.balance.setScale(4, RoundingMode.UNNECESSARY).toPlainString(),
        )
    }

    fun reconciliation(): LedgerReconciliationResponse {
        val result = repository.reconciliation()
        return LedgerReconciliationResponse(
            balancedJournals = result.imbalancedJournalCount == 0L,
            journalCount = result.journalCount,
            postingCount = result.postingCount,
            imbalancedJournalCount = result.imbalancedJournalCount,
        )
    }

    private fun LedgerAccountRecord.toResponse(): LedgerAccountResponse =
        LedgerAccountResponse(
            accountId = id.toString(),
            code = code,
            currency = currency,
            accountType = accountType,
            normalSide = normalSide,
        )

    private fun validateText(value: String, field: String, maxLength: Int): String {
        val trimmed = value.trim()
        if (trimmed.isBlank() || trimmed.length > maxLength) {
            throw LedgerException(
                code = "invalid_$field",
                message = "Ledger field is invalid.",
                status = HttpStatus.BAD_REQUEST,
            )
        }
        return trimmed
    }

    private fun validateCurrency(value: String): String {
        val trimmed = value.trim().uppercase()
        if (!Regex("[A-Z]{3}").matches(trimmed)) {
            throw LedgerException(
                code = "invalid_currency",
                message = "Currency is invalid.",
                status = HttpStatus.BAD_REQUEST,
            )
        }
        return trimmed
    }

    private fun validateSide(value: String): String {
        val normalized = value.trim().uppercase()
        if (normalized !in setOf("DEBIT", "CREDIT")) {
            throw LedgerException(
                code = "invalid_ledger_side",
                message = "Ledger side is invalid.",
                status = HttpStatus.BAD_REQUEST,
            )
        }
        return normalized
    }

    private fun validateAmount(value: String): BigDecimal {
        val amount = runCatching { BigDecimal(value.trim()) }
            .getOrElse {
                throw LedgerException(
                    code = "invalid_amount",
                    message = "Amount is invalid.",
                    status = HttpStatus.BAD_REQUEST,
                )
            }
            .setScale(4, RoundingMode.UNNECESSARY)
        if (amount <= BigDecimal.ZERO) {
            throw LedgerException(
                code = "invalid_amount",
                message = "Amount is invalid.",
                status = HttpStatus.BAD_REQUEST,
            )
        }
        return amount
    }

    private fun requireUuid(value: String, field: String): UUID =
        runCatching { UUID.fromString(value) }
            .getOrElse {
                throw LedgerException(
                    code = "invalid_uuid",
                    message = "Invalid UUID.",
                    status = HttpStatus.BAD_REQUEST,
                )
            }
}
