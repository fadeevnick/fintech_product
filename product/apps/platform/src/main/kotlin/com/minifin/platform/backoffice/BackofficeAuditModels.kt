package com.minifin.platform.backoffice

data class BackofficeAuditFeedResponse(
    val items: List<BackofficeAuditFeedItemResponse>,
)

data class BackofficeAuditFeedItemResponse(
    val entryId: String,
    val stream: String,
    val code: String,
    val actorType: String,
    val actorId: String? = null,
    val actorReference: String? = null,
    val subjectType: String,
    val subjectId: String? = null,
    val resourceType: String? = null,
    val resourceId: String? = null,
    val result: String,
    val metadataJson: String,
    val requestId: String? = null,
    val correlationId: String? = null,
    val createdAt: String,
)

