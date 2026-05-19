package com.minifin.platform.sanctions

import java.math.BigDecimal
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
