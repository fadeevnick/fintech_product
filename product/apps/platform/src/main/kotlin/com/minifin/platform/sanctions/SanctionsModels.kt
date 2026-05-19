package com.minifin.platform.sanctions

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.http.HttpStatus

const val SANCTIONS_VENDOR_OPENSANCTIONS = "OPENSANCTIONS"

data class OpenSanctionsScreeningResult(
    val outcome: OpenSanctionsScreeningOutcome,
    val requestId: String?,
    val matchScore: BigDecimal? = null,
    val matchedEntityId: String? = null,
    val matchedName: String? = null,
)

enum class OpenSanctionsScreeningOutcome {
    NO_MATCH,
    POSSIBLE_MATCH,
    UNAVAILABLE,
}

class SanctionsException(
    val code: String,
    override val message: String,
    val status: HttpStatus,
) : RuntimeException(message)

data class SanctionsHitRecord(
    val id: UUID,
    val endUserId: UUID,
    val kycProfileId: UUID?,
    val status: String,
    val reason: String,
    val vendor: String,
    val matchScore: BigDecimal?,
    val matchedEntityId: String?,
    val matchedName: String?,
    val requestId: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class SanctionsHitResponse(
    val id: String,
    val endUserId: String,
    val kycProfileId: String?,
    val status: String,
    val reason: String,
    val vendor: String,
    val matchScore: BigDecimal?,
    val matchedEntityId: String?,
    val matchedName: String?,
    val requestId: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class SanctionsHitDecisionRequest(
    val decision: String,
    val rationale: String?,
)

data class SanctionsHitDecisionResponse(
    val hitId: String,
    val previousStatus: String,
    val status: String,
    val decision: String,
    val exceptionId: String,
    val decidedBySubject: String,
    val decidedByRole: String?,
    val decidedAt: OffsetDateTime,
)
