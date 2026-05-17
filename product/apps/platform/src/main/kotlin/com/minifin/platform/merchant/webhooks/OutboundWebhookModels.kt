package com.minifin.platform.merchant.webhooks

import java.time.OffsetDateTime
import java.util.UUID

data class OutboundWebhookEventRecord(
    val id: UUID,
    val merchantId: UUID,
    val eventType: String,
    val aggregateType: String,
    val aggregateId: UUID,
    val payloadJson: String,
    val status: String,
    val createdAt: OffsetDateTime,
)
