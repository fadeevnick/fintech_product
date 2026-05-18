package com.minifin.platform.kyc

import com.fasterxml.jackson.databind.ObjectMapper
import com.minifin.platform.controls.ActorControlService
import com.minifin.platform.identity.EndUserRecord
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class KycService(
    private val repository: KycRepository,
    private val actorControlService: ActorControlService,
    private val sumsubClient: SumsubClient,
    private val properties: SumsubProperties,
    private val objectMapper: ObjectMapper,
) {
    @Transactional(noRollbackFor = [KycException::class])
    fun start(user: EndUserRecord): KycStartResponse {
        if (user.status != "ACTIVE") {
            throw KycException("email_not_verified", "Email must be verified before starting KYC.", HttpStatus.FORBIDDEN)
        }
        actorControlService.requireWriteAllowed("END_USER", user.id)
        val profile = repository.findProfileByEndUserId(user.id)
            ?: repository.insertProfile(UUID.randomUUID(), user.id, "enduser:${user.id}", properties.levelName)
        val activeSession = repository.findActiveSession(profile.id)
        if (activeSession != null) return profile.toResponse(activeSession, null, "configured")
        if (!sumsubClient.configured()) {
            repository.markStartFailed(profile.id, "sumsub_not_configured", "Sumsub sandbox credentials are not configured.")
            throw KycException("sumsub_not_configured", "Sumsub sandbox credentials are not configured.", HttpStatus.SERVICE_UNAVAILABLE)
        }
        val result = runCatching { sumsubClient.start(profile.externalUserId) }
            .getOrElse { throwable ->
                repository.markStartFailed(profile.id, (throwable as? KycException)?.code ?: "sumsub_start_failed", throwable.message ?: "Sumsub start failed.")
                throw throwable
            }
        val session = repository.createSession(profile.id, result.applicantId, sha256Hex(result.accessToken), result.expiresAt)
        return (repository.findProfileByEndUserId(user.id) ?: profile).toResponse(session, result.accessToken, "configured")
    }

    @Transactional
    fun processWebhook(rawBody: String): SumsubWebhookResponse {
        val json = objectMapper.readTree(rawBody)
        val vendorEventId = json.textAny("id", "eventId", "correlationId")
            ?: throw KycException("invalid_webhook_payload", "Webhook event id is required.", HttpStatus.BAD_REQUEST)
        val vendorApplicantId = json.textAny("applicantId", "applicant_id")
            ?: throw KycException("invalid_webhook_payload", "Webhook applicant id is required.", HttpStatus.BAD_REQUEST)
        val eventType = json.textAny("type", "eventType") ?: "applicantReviewed"
        val reviewStatus = json.textAny("reviewStatus", "review_status") ?: json.path("reviewResult").textAny("reviewStatus", "reviewAnswer")
        val reviewAnswer = json.path("reviewResult").textAny("reviewAnswer", "answer") ?: json.textAny("reviewAnswer")
        val inserted = repository.insertWebhookEvent(UUID.randomUUID(), vendorEventId, vendorApplicantId, eventType, reviewStatus, reviewAnswer, sha256Hex(rawBody))
        if (!inserted) return SumsubWebhookResponse(vendorEventId, true, repository.findProfileByVendorApplicantId(vendorApplicantId)?.status)
        val mapped = mapStatus(eventType, reviewStatus, reviewAnswer, json.path("reviewResult").textAny("rejectType"))
        if (mapped != null && repository.findProfileByVendorApplicantId(vendorApplicantId) != null) {
            repository.applyWebhookEvent(
                vendorEventId,
                vendorApplicantId,
                mapped,
                reviewAnswer,
                json.path("reviewResult").textAny("rejectType"),
                json.path("reviewResult").textAny("moderationComment", "clientComment"),
            )
        }
        return SumsubWebhookResponse(vendorEventId, false, repository.findProfileByVendorApplicantId(vendorApplicantId)?.status)
    }

    private fun mapStatus(eventType: String, reviewStatus: String?, reviewAnswer: String?, rejectType: String?): String? {
        val event = eventType.uppercase()
        val status = reviewStatus?.uppercase()
        val answer = reviewAnswer?.uppercase()
        return when {
            answer == "GREEN" -> "APPROVED"
            answer == "RED" && rejectType?.uppercase() == "FINAL" -> "REJECTED"
            answer == "RED" && rejectType?.uppercase() == "RETRY" -> "NEEDS_RESUBMIT"
            answer == "RED" -> "IN_REVIEW"
            status in setOf("PENDING", "QUEUED", "INIT", "ON_HOLD") -> "IN_REVIEW"
            event.contains("PENDING") || event.contains("REVIEW") -> "IN_REVIEW"
            else -> null
        }
    }

    private fun KycProfileRecord.toResponse(session: KycSessionRecord?, accessToken: String?, configurationStatus: String): KycStartResponse =
        KycStartResponse(
            profileId = id.toString(),
            sessionId = session?.id?.toString(),
            status = status,
            vendor = KYC_VENDOR_SUMSUB,
            vendorApplicantId = session?.vendorApplicantId ?: vendorApplicantId,
            externalUserId = externalUserId,
            levelName = levelName,
            accessToken = accessToken,
            configurationStatus = configurationStatus,
        )
}
