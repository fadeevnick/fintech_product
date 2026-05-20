package com.minifin.platform.settlement

import java.math.BigDecimal
import java.util.UUID
import org.springframework.http.HttpStatus

data class SettlementProcessResponse(
    val batchId: String?,
    val processedCount: Int,
    val itemIds: List<String>,
    val paymentIntentIds: List<String>,
)

data class SettlementProjectionPublishResponse(
    val publishedCount: Int,
    val acquirerReceivedCount: Int,
    val acquirerInsertedCount: Int,
)

data class SettlementCandidate(
    val paymentIntentId: UUID,
    val merchantId: UUID,
)

data class SettlementItemResult(
    val itemId: UUID,
    val paymentIntentId: UUID,
)

data class SettlementFeeSplit(
    val grossAmount: BigDecimal,
    val merchantNetAmount: BigDecimal,
    val interchangeAmount: BigDecimal,
    val networkAssessmentAmount: BigDecimal,
    val acquirerMarginAmount: BigDecimal,
)

data class SettlementProjectionItem(
    val platformSettlementItemId: String,
    val platformBatchId: String,
    val merchantId: String,
    val paymentIntentId: String,
    val grossAmount: String,
    val merchantNetAmount: String,
    val interchangeAmount: String,
    val networkAssessmentAmount: String,
    val acquirerMarginAmount: String,
    val currency: String,
    val platformSettledAt: String?,
)

data class SettlementProjectionRequest(
    val items: List<SettlementProjectionItem>,
)

data class SettlementProjectionAcquirerResponse(
    val receivedCount: Int = 0,
    val insertedCount: Int = 0,
)

data class SettlementProjectionApiResponse(
    val data: SettlementProjectionAcquirerResponse? = null,
    val errors: List<Any> = emptyList(),
)

class SettlementException(
    val code: String,
    override val message: String,
    val status: HttpStatus,
) : RuntimeException(message)
