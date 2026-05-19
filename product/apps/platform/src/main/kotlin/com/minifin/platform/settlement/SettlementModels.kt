package com.minifin.platform.settlement

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

class SettlementException(
    val code: String,
    override val message: String,
    val status: HttpStatus,
) : RuntimeException(message)
