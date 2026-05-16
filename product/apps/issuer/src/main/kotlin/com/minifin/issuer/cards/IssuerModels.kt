package com.minifin.issuer.cards

import com.fasterxml.jackson.annotation.JsonInclude

data class ApiResponse<T>(val data: T? = null, val errors: List<ApiError> = emptyList())

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiError(val code: String, val message: String, val field: String? = null)

data class IssueCardRequest(val endUserId: String, val walletAccountId: String, val requestId: String? = null, val correlationId: String? = null)

data class CardResponse(
    val cardId: String,
    val state: String,
    val cardToken: String,
    val last4: String,
    val expirationMonth: Int,
    val expirationYear: Int,
    val bin: String,
)

data class VaultTokenizeRequest(val requestId: String? = null, val correlationId: String? = null)
data class VaultTokenizeResponse(val cardToken: String, val last4: String, val expirationMonth: Int, val expirationYear: Int, val bin: String)
