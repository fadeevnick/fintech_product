package com.minifin.platform.settlement

import com.fasterxml.jackson.databind.ObjectMapper
import com.minifin.platform.ledger.LedgerRepository
import java.math.RoundingMode
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SettlementService(
    private val settlementRepository: SettlementRepository,
    private val ledgerRepository: LedgerRepository,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun processCaptured(limit: Int): SettlementProcessResponse {
        val boundedLimit = limit.coerceIn(1, 200)
        val candidates = settlementRepository.findCapturedCandidates(boundedLimit)
        if (candidates.isEmpty()) {
            return SettlementProcessResponse(null, 0, emptyList(), emptyList())
        }

        val batchId = UUID.randomUUID()
        settlementRepository.createBatch(batchId)
        val clearingAccountId = runCatching { settlementRepository.settlementClearingAccount() }
            .getOrElse {
                throw SettlementException(
                    code = "settlement_clearing_account_missing",
                    message = "Settlement clearing ledger account is missing.",
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                )
            }

        val results = candidates.mapNotNull { candidate ->
            val merchantAccountId = settlementRepository.findOrCreateMerchantSettlementAccount(candidate.merchantId)
            val amount = settlementRepository.paymentAmount(candidate.paymentIntentId).setScale(4, RoundingMode.UNNECESSARY)
            val journalId = UUID.randomUUID()
            val postings = listOf(
                mapOf(
                    "postingId" to UUID.randomUUID().toString(),
                    "accountId" to clearingAccountId.toString(),
                    "side" to "DEBIT",
                    "amount" to amount.toPlainString(),
                ),
                mapOf(
                    "postingId" to UUID.randomUUID().toString(),
                    "accountId" to merchantAccountId.toString(),
                    "side" to "CREDIT",
                    "amount" to amount.toPlainString(),
                ),
            )
            ledgerRepository.postJournal(
                journalId = journalId,
                journalType = "CARD_PAYMENT_SETTLEMENT",
                referenceType = "PAYMENT_INTENT",
                referenceId = candidate.paymentIntentId,
                currency = "EUR",
                description = "Captured payment settlement",
                postingsJson = objectMapper.writeValueAsString(postings),
            )
            val itemId = UUID.randomUUID()
            if (settlementRepository.insertItem(
                    id = itemId,
                    batchId = batchId,
                    paymentIntentId = candidate.paymentIntentId,
                    merchantId = candidate.merchantId,
                    grossAmount = amount,
                    currency = "EUR",
                    ledgerJournalId = journalId,
                ) == 0
            ) {
                null
            } else {
                settlementRepository.markPaymentSettled(candidate.paymentIntentId)
                SettlementItemResult(itemId, candidate.paymentIntentId)
            }
        }

        return SettlementProcessResponse(
            batchId = batchId.toString(),
            processedCount = results.size,
            itemIds = results.map { it.itemId.toString() },
            paymentIntentIds = results.map { it.paymentIntentId.toString() },
        )
    }
}
