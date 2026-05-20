package com.minifin.acquirer.settlement

import com.fasterxml.jackson.annotation.JsonInclude

data class ApiResponse<T>(val data: T? = null, val errors: List<ApiError> = emptyList())

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiError(val code: String, val message: String, val field: String? = null)

data class SettlementProjectionRequest(
    val items: List<SettlementProjectionItem>,
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

data class SettlementProjectionResponse(
    val receivedCount: Int,
    val insertedCount: Int,
)
