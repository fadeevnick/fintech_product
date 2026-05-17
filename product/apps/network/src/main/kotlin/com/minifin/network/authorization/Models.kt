package com.minifin.network.authorization

import com.fasterxml.jackson.annotation.JsonInclude

data class ApiResponse<T>(val data: T? = null, val errors: List<ApiError> = emptyList())
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiError(val code: String, val message: String, val field: String? = null)
data class NetworkAuthorizeRequest(val paymentIntentId: String, val merchantId: String, val amount: String, val currency: String, val cardToken: String, val requestId: String? = null, val correlationId: String? = null)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class NetworkAuthorizeResponse(val routeId: String, val status: String, val authorizationId: String? = null, val authCode: String? = null, val expiresAt: String? = null, val declineCode: String? = null, val declineMessage: String? = null)
data class IssuerAuthorizeResponse(val status: String? = null, val authorizationId: String? = null, val authCode: String? = null, val expiresAt: String? = null, val declineCode: String? = null, val declineMessage: String? = null, val ledgerHoldJournalEntryId: String? = null)
data class IssuerApiResponse(val data: IssuerAuthorizeResponse? = null, val errors: List<ApiError> = emptyList())
