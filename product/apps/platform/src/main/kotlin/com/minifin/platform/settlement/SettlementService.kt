package com.minifin.platform.settlement

import com.fasterxml.jackson.databind.ObjectMapper
import com.minifin.platform.cards.CardProperties
import com.minifin.platform.identity.MerchantEmployeeRecord
import com.minifin.platform.ledger.LedgerRepository
import com.minifin.platform.merchant.dashboard.MerchantDashboardException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate

@Service
class SettlementService(
    private val settlementRepository: SettlementRepository,
    private val ledgerRepository: LedgerRepository,
    private val objectMapper: ObjectMapper,
    private val cardProperties: CardProperties,
) {
    private val oneHundred = BigDecimal("100.00")
    private val interchangeRate = BigDecimal("1.20")
    private val networkAssessmentRate = BigDecimal("0.15")
    private val acquirerMarginRate = BigDecimal("0.65")
    private val restTemplate = RestTemplate()

    fun listMerchantSettlements(employee: MerchantEmployeeRecord, limit: Int): MerchantSettlementBatchListResponse {
        requireActiveMerchant(employee)
        val normalizedLimit = limit.coerceIn(1, 100)
        return MerchantSettlementBatchListResponse(
            items = settlementRepository.listMerchantBatches(employee.merchantId, normalizedLimit).map { it.toSummaryDto() },
        )
    }

    fun getMerchantSettlement(employee: MerchantEmployeeRecord, batchId: UUID): MerchantSettlementBatchDetailDto {
        requireActiveMerchant(employee)
        val batch = settlementRepository.findMerchantBatch(batchId, employee.merchantId)
            ?: throw MerchantDashboardException("settlement_batch_not_found", "Settlement batch was not found.", HttpStatus.NOT_FOUND)
        val items = settlementRepository.listMerchantBatchItems(batchId, employee.merchantId).map { it.toItemDto() }
        return MerchantSettlementBatchDetailDto(batch = batch.toSummaryDto(), items = items)
    }

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
        val issuerInterchangeAccountId = settlementRepository.accountByCode("ISSUER_INTERCHANGE_REVENUE")
        val networkAssessmentAccountId = settlementRepository.accountByCode("NETWORK_ASSESSMENT_REVENUE")
        val acquirerMarginAccountId = settlementRepository.accountByCode("ACQUIRER_MARGIN_REVENUE")

        val results = candidates.mapNotNull { candidate ->
            val merchantAccountId = settlementRepository.findOrCreateMerchantSettlementAccount(candidate.merchantId)
            val amount = settlementRepository.paymentAmount(candidate.paymentIntentId).setScale(4, RoundingMode.UNNECESSARY)
            val feeSplit = calculateFeeSplit(amount)
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
                    "amount" to feeSplit.merchantNetAmount.toPlainString(),
                ),
                mapOf(
                    "postingId" to UUID.randomUUID().toString(),
                    "accountId" to issuerInterchangeAccountId.toString(),
                    "side" to "CREDIT",
                    "amount" to feeSplit.interchangeAmount.toPlainString(),
                ),
                mapOf(
                    "postingId" to UUID.randomUUID().toString(),
                    "accountId" to networkAssessmentAccountId.toString(),
                    "side" to "CREDIT",
                    "amount" to feeSplit.networkAssessmentAmount.toPlainString(),
                ),
                mapOf(
                    "postingId" to UUID.randomUUID().toString(),
                    "accountId" to acquirerMarginAccountId.toString(),
                    "side" to "CREDIT",
                    "amount" to feeSplit.acquirerMarginAmount.toPlainString(),
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
                    grossAmount = feeSplit.grossAmount,
                    merchantNetAmount = feeSplit.merchantNetAmount,
                    interchangeAmount = feeSplit.interchangeAmount,
                    networkAssessmentAmount = feeSplit.networkAssessmentAmount,
                    acquirerMarginAmount = feeSplit.acquirerMarginAmount,
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

    fun publishProjections(limit: Int): SettlementProjectionPublishResponse {
        val items = settlementRepository.projectionItems(limit.coerceIn(1, 500))
        if (items.isEmpty()) {
            return SettlementProjectionPublishResponse(0, 0, 0)
        }
        val headers = HttpHeaders()
        headers.set("X-Service-Name", "platform")
        headers.set("X-Service-Secret", cardProperties.serviceAuthSecret)
        val response = runCatching {
            restTemplate.exchange(
                "${cardProperties.acquirerBaseUrl}/internal/settlement/projections",
                HttpMethod.POST,
                HttpEntity(SettlementProjectionRequest(items), headers),
                SettlementProjectionApiResponse::class.java,
            )
        }.getOrElse {
            throw SettlementException(
                code = "acquirer_projection_unavailable",
                message = "Acquirer settlement projection service is unavailable.",
                status = HttpStatus.BAD_GATEWAY,
            )
        }
        val data = response.body?.data
            ?: throw SettlementException(
                code = "acquirer_projection_empty_response",
                message = "Acquirer settlement projection service returned no data.",
                status = HttpStatus.BAD_GATEWAY,
            )
        return SettlementProjectionPublishResponse(items.size, data.receivedCount, data.insertedCount)
    }

    private fun calculateFeeSplit(grossAmount: BigDecimal): SettlementFeeSplit {
        val interchange = fee(grossAmount, interchangeRate)
        val networkAssessment = fee(grossAmount, networkAssessmentRate)
        val acquirerMargin = fee(grossAmount, acquirerMarginRate)
        val merchantNet = grossAmount.setScale(2, RoundingMode.HALF_UP)
            .subtract(interchange)
            .subtract(networkAssessment)
            .subtract(acquirerMargin)
        return SettlementFeeSplit(
            grossAmount = grossAmount.setScale(4, RoundingMode.UNNECESSARY),
            merchantNetAmount = merchantNet.setScale(4, RoundingMode.UNNECESSARY),
            interchangeAmount = interchange.setScale(4, RoundingMode.UNNECESSARY),
            networkAssessmentAmount = networkAssessment.setScale(4, RoundingMode.UNNECESSARY),
            acquirerMarginAmount = acquirerMargin.setScale(4, RoundingMode.UNNECESSARY),
        )
    }

    private fun fee(grossAmount: BigDecimal, rate: BigDecimal): BigDecimal =
        grossAmount.multiply(rate)
            .divide(oneHundred, 2, RoundingMode.HALF_UP)

    private fun requireActiveMerchant(employee: MerchantEmployeeRecord) {
        if (employee.status != "ACTIVE") {
            throw MerchantDashboardException("merchant_employee_not_active", "Merchant employee is not active.", HttpStatus.FORBIDDEN)
        }
    }

    private fun MerchantSettlementBatchSummaryRecord.toSummaryDto(): MerchantSettlementBatchSummaryDto =
        MerchantSettlementBatchSummaryDto(
            batchId = batchId.toString(),
            status = status,
            currency = currency,
            itemCount = itemCount,
            grossAmount = grossAmount.setScale(4, RoundingMode.HALF_UP).toPlainString(),
            merchantNetAmount = merchantNetAmount.setScale(4, RoundingMode.HALF_UP).toPlainString(),
            interchangeAmount = interchangeAmount.setScale(4, RoundingMode.HALF_UP).toPlainString(),
            networkAssessmentAmount = networkAssessmentAmount.setScale(4, RoundingMode.HALF_UP).toPlainString(),
            acquirerMarginAmount = acquirerMarginAmount.setScale(4, RoundingMode.HALF_UP).toPlainString(),
            settledAt = settledAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            createdAt = createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        )

    private fun MerchantSettlementItemRecord.toItemDto(): MerchantSettlementItemDto =
        MerchantSettlementItemDto(
            settlementItemId = settlementItemId.toString(),
            paymentIntentId = paymentIntentId.toString(),
            grossAmount = grossAmount.setScale(4, RoundingMode.HALF_UP).toPlainString(),
            merchantNetAmount = merchantNetAmount.setScale(4, RoundingMode.HALF_UP).toPlainString(),
            interchangeAmount = interchangeAmount.setScale(4, RoundingMode.HALF_UP).toPlainString(),
            networkAssessmentAmount = networkAssessmentAmount.setScale(4, RoundingMode.HALF_UP).toPlainString(),
            acquirerMarginAmount = acquirerMarginAmount.setScale(4, RoundingMode.HALF_UP).toPlainString(),
            currency = currency,
            status = status,
            ledgerJournalId = ledgerJournalId.toString(),
            createdAt = createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        )
}
