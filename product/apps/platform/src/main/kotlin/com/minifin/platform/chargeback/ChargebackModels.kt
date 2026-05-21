package com.minifin.platform.chargeback

import com.fasterxml.jackson.annotation.JsonInclude

data class CreateDisputeRequest(
    val reasonCode: String? = null,
    val narrative: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ChargebackDisputeDto(
    val id: String,
    val paymentIntentId: String,
    val merchantId: String,
    val cardholderUserId: String,
    val amount: String,
    val currency: String,
    val reasonCode: String,
    val narrative: String?,
    val state: String,
    val merchantResponseDeadline: String,
    val createdAt: String,
)
