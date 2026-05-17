package com.minifin.platform.merchant.webhooks

import java.time.OffsetDateTime
import java.util.UUID

data class WebhookEndpointRecord(
    val id: UUID,
    val merchantId: UUID,
    val url: String,
    val enabledEventsJson: String,
    val status: String,
    val description: String?,
    val signingSecretHash: String,
    val secretPrefix: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val deletedAt: OffsetDateTime?,
)

data class WebhookEndpointRequest(
    val url: String? = null,
    val enabledEvents: List<String>? = null,
    val status: String? = null,
    val description: String? = null,
)

data class WebhookEndpointDto(
    val id: String,
    val url: String,
    val enabledEvents: List<String>,
    val status: String,
    val description: String?,
    val secretPrefix: String,
    val signingSecret: String?,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
)
