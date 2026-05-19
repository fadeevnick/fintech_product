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

class SettlementException(
    val code: String,
    override val message: String,
    val status: HttpStatus,
) : RuntimeException(message)
