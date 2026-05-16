package com.minifin.vault.tokenization

import com.fasterxml.jackson.annotation.JsonInclude

data class ApiResponse<T>(val data: T? = null, val errors: List<ApiError> = emptyList())

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiError(val code: String, val message: String, val field: String? = null)

data class TokenizeRequest(val requestId: String? = null, val correlationId: String? = null)

data class TokenizeResponse(
    val cardToken: String,
    val last4: String,
    val expirationMonth: Int,
    val expirationYear: Int,
    val bin: String,
)

data class DetokenizeRequest(val cardToken: String, val requestId: String? = null, val correlationId: String? = null)

data class DetokenizeResponse(val cardToken: String, val pan: String)
