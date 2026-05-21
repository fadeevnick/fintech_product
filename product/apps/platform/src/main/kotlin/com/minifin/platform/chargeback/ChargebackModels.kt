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
    val provisionalCreditJournalId: String? = null,
    val createdAt: String,
)

data class SubmitEvidenceRequest(
    val narrative: String? = null,
    val attachments: List<EvidenceAttachmentRequest> = emptyList(),
)

data class EvidenceAttachmentRequest(
    val fileName: String? = null,
    val contentType: String? = null,
    val storageKey: String? = null,
    val sizeBytes: Long? = null,
)

data class EvidenceSubmissionDto(
    val id: String,
    val disputeId: String,
    val state: String,
    val narrative: String,
    val attachments: List<EvidenceAttachmentDto>,
    val createdAt: String,
)

data class EvidenceAttachmentDto(
    val id: String,
    val fileName: String,
    val contentType: String,
    val storageKey: String,
    val sizeBytes: Long,
)

data class ArbitrationDecisionRequest(
    val outcome: String? = null,
    val rationale: String? = null,
)

data class ArbitrationDecisionDto(
    val disputeId: String,
    val state: String,
    val outcome: String,
    val arbitrationJournalId: String,
)

data class MerchantAcceptChargebackDto(
    val disputeId: String,
    val state: String,
    val externalState: String,
    val merchantDebitJournalId: String,
)

data class DeadlineExpiryProcessDto(
    val processedCount: Int,
    val processed: List<DeadlineExpiryItemDto>,
)

data class DeadlineExpiryItemDto(
    val disputeId: String,
    val state: String,
    val externalState: String,
    val merchantDebitJournalId: String,
)
