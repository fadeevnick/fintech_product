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
    val retryCount: Int,
    val maxAttempts: Int,
    val nextRetryAt: OffsetDateTime?,
    val lastAttemptAt: OffsetDateTime?,
    val lastErrorType: String?,
    val lastErrorMessage: String?,
    val lastHttpStatus: Int?,
    val dlqAt: OffsetDateTime?,
)

data class WebhookEventListResponse(
    val items: List<WebhookEventDto>,
    val nextCursor: String? = null,
)

data class WebhookEventDto(
    val id: String,
    val type: String,
    val aggregateType: String,
    val aggregateId: String,
    val status: String,
    val createdAt: String,
    val retryCount: Int,
    val maxAttempts: Int,
    val nextRetryAt: String?,
    val lastAttemptAt: String?,
    val lastErrorType: String?,
    val lastErrorMessage: String?,
    val lastHttpStatus: Int?,
    val dlqAt: String?,
)

data class WebhookReplayResponse(
    val eventId: String,
    val status: String,
    val replayAttemptNumber: Int?,
    val httpStatus: Int?,
)
