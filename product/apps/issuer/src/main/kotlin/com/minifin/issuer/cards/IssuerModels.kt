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


data class AuthorizeCardRequest(val paymentIntentId: String, val merchantId: String, val amount: String, val currency: String, val cardToken: String, val networkRouteId: String? = null, val requestId: String? = null, val correlationId: String? = null)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AuthorizeCardResponse(val status: String, val authorizationId: String? = null, val authCode: String? = null, val expiresAt: String? = null, val declineCode: String? = null, val declineMessage: String? = null, val ledgerHoldJournalEntryId: String? = null)
data class PlatformActorControlResponse(val state: String)
data class PlatformBalanceResponse(val walletAccountId: String, val ledgerAccountId: String, val availableBalance: String, val currency: String)
data class PlatformHoldRequest(val authorizationId: String, val walletAccountId: String, val amount: String, val currency: String, val requestId: String? = null, val correlationId: String? = null)
data class PlatformHoldResponse(val journalEntryId: String, val walletAccountId: String, val holdAccountId: String, val amount: String, val currency: String)
data class PlatformApiResponse<T>(val data: T? = null, val errors: List<ApiError> = emptyList())
