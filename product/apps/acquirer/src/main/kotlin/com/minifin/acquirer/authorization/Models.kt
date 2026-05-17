package com.minifin.acquirer.authorization

import com.fasterxml.jackson.annotation.JsonInclude

data class ApiResponse<T>(val data: T? = null, val errors: List<ApiError> = emptyList())
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiError(val code: String, val message: String, val field: String? = null)
data class AcquirerAuthorizeRequest(val paymentIntentId: String, val merchantId: String, val amount: String, val currency: String, val cardToken: String, val requestId: String? = null, val correlationId: String? = null)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AcquirerAuthorizeResponse(val paymentIntentId: String, val state: String, val status: String, val authorizationId: String? = null, val authCode: String? = null, val expiresAt: String? = null, val declineCode: String? = null, val declineMessage: String? = null, val routeId: String? = null)
data class NetworkAuthorizeResponse(val routeId: String? = null, val status: String? = null, val authorizationId: String? = null, val authCode: String? = null, val expiresAt: String? = null, val declineCode: String? = null, val declineMessage: String? = null)
data class NetworkApiResponse(val data: NetworkAuthorizeResponse? = null, val errors: List<ApiError> = emptyList())
