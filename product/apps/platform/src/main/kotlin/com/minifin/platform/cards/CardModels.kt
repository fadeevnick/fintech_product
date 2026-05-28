package com.minifin.platform.cards

import com.fasterxml.jackson.annotation.JsonInclude

data class CardIssueResponse(val card: CardMetadataResponse)

data class CardListResponse(val items: List<CardMetadataResponse>)

data class CardMetadataResponse(
    val id: String,
    val state: String,
    val last4: String,
    val expirationMonth: Int,
    val expirationYear: Int,
    val bin: String,
)

data class IssuerIssueCardRequest(val endUserId: String, val walletAccountId: String, val requestId: String? = null, val correlationId: String? = null)
data class IssuerCardResponse(val cardId: String, val state: String, val cardToken: String? = null, val last4: String, val expirationMonth: Int, val expirationYear: Int, val bin: String)
data class IssuerApiResponse(val data: IssuerCardResponse? = null, val errors: List<IssuerApiError> = emptyList())
@JsonInclude(JsonInclude.Include.NON_NULL)
data class IssuerApiError(val code: String, val message: String, val field: String? = null)
