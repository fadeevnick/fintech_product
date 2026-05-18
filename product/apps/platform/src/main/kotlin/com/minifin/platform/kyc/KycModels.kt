package com.minifin.platform.kyc

import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.http.HttpStatus

const val KYC_VENDOR_SUMSUB = "SUMSUB"

data class KycProfileRecord(
    val id: UUID,
    val endUserId: UUID,
    val status: String,
    val vendorApplicantId: String?,
    val levelName: String?,
    val externalUserId: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class KycSessionRecord(
    val id: UUID,
    val profileId: UUID,
    val vendorApplicantId: String?,
    val externalUserId: String,
    val status: String,
    val expiresAt: OffsetDateTime?,
)

data class KycStartResponse(
    val profileId: String,
    val sessionId: String?,
    val status: String,
    val vendor: String,
    val vendorApplicantId: String?,
    val externalUserId: String,
    val levelName: String?,
    val accessToken: String?,
    val configurationStatus: String,
)

data class SumsubWebhookResponse(val vendorEventId: String, val duplicate: Boolean, val status: String?)

class KycException(
    val code: String,
    override val message: String,
    val status: HttpStatus,
) : RuntimeException(message)
