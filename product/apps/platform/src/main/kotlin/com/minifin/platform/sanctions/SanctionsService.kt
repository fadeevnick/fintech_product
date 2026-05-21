package com.minifin.platform.sanctions

import com.minifin.platform.identity.AuditRepository
import com.minifin.platform.backoffice.BackofficePrincipal
import com.minifin.platform.controls.ReadAuditRepository
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
open class SanctionsService(
    private val client: OpenSanctionsClient,
    private val repository: SanctionsRepository,
    private val auditRepository: AuditRepository,
    private val readAuditRepository: ReadAuditRepository,
) {
    open fun requireKycApprovalAllowed(endUserId: UUID, kycProfileId: UUID, actorId: UUID?) {
        val result = client.screenEndUser(endUserId)
        when (result.outcome) {
            OpenSanctionsScreeningOutcome.NO_MATCH -> auditRepository.write(
                eventType = "sanctions.opensanctions_screening_passed",
                actorType = "BACKOFFICE",
                actorId = actorId,
                subjectType = "KYC_PROFILE",
                subjectId = kycProfileId,
                outcome = "SUCCESS",
                metadataJson = """{"vendor":"OPENSANCTIONS","endUserId":"$endUserId","requestId":"${result.requestId ?: ""}"}""",
            )
            OpenSanctionsScreeningOutcome.UNAVAILABLE -> {
                val hitId = UUID.randomUUID()
                repository.insertHit(hitId, endUserId, kycProfileId, "SCREENING_UNAVAILABLE", null, null, null, result.requestId)
                auditRepository.write(
                    eventType = "sanctions.opensanctions_screening_unavailable",
                    actorType = "BACKOFFICE",
                    actorId = actorId,
                    subjectType = "KYC_PROFILE",
                    subjectId = kycProfileId,
                    outcome = "FAIL_CLOSED",
                    metadataJson = """{"vendor":"OPENSANCTIONS","endUserId":"$endUserId","hitId":"$hitId","requestId":"${result.requestId ?: ""}"}""",
                )
                throw SanctionsException("sanctions_screening_unavailable", "OpenSanctions screening is unavailable; KYC approval remains in review.", HttpStatus.SERVICE_UNAVAILABLE)
            }
            OpenSanctionsScreeningOutcome.POSSIBLE_MATCH -> {
                val matchedEntityId = result.matchedEntityId
                if (matchedEntityId != null && repository.hasActiveFalsePositiveException(endUserId, SANCTIONS_VENDOR_OPENSANCTIONS, matchedEntityId)) {
                    auditRepository.write(
                        eventType = "sanctions.opensanctions_screening_suppressed",
                        actorType = "BACKOFFICE",
                        actorId = actorId,
                        subjectType = "KYC_PROFILE",
                        subjectId = kycProfileId,
                        outcome = "SUCCESS",
                        metadataJson = """{"vendor":"OPENSANCTIONS","endUserId":"$endUserId","matchedEntityId":"$matchedEntityId","requestId":"${result.requestId ?: ""}"}""",
                    )
                    return
                }
                val hitId = UUID.randomUUID()
                repository.insertHit(hitId, endUserId, kycProfileId, "POSSIBLE_MATCH", result.matchScore, result.matchedEntityId, result.matchedName, result.requestId)
                auditRepository.write(
                    eventType = "sanctions.opensanctions_screening_blocked",
                    actorType = "BACKOFFICE",
                    actorId = actorId,
                    subjectType = "KYC_PROFILE",
                    subjectId = kycProfileId,
                    outcome = "BLOCKED",
                    metadataJson = """{"vendor":"OPENSANCTIONS","endUserId":"$endUserId","hitId":"$hitId","requestId":"${result.requestId ?: ""}","matchScore":"${result.matchScore ?: ""}"}""",
                )
                throw SanctionsException("sanctions_possible_match", "OpenSanctions returned a possible match; KYC approval remains in review.", HttpStatus.CONFLICT)
            }
        }
    }

    open fun listHits(): List<SanctionsHitResponse> =
        repository.listHits().map { it.toResponse() }

    @Transactional
    open fun getHit(id: UUID, principal: BackofficePrincipal): SanctionsHitResponse {
        val hit = repository.findHit(id)
            ?: throw SanctionsException("not_found", "Sanctions hit was not found.", HttpStatus.NOT_FOUND)
        readAuditRepository.write(
            actorType = "BACKOFFICE",
            actorId = principal.subjectUuid,
            actorReference = principal.subject,
            subjectType = "SANCTIONS_HIT",
            subjectId = hit.id,
            resourceType = "SANCTIONS_HIT",
            resourceId = hit.id,
            purpose = "compliance_sanctions_hit_detail",
            decision = "ALLOW",
            metadataJson = """{"roles":${principal.roles.toJsonArray()},"endUserId":"${hit.endUserId}"}""",
        )
        return hit.toResponse()
    }

    @Transactional
    open fun decideHit(id: UUID, request: SanctionsHitDecisionRequest, principal: BackofficePrincipal): SanctionsHitDecisionResponse {
        if (principal.roles.none { it == "compliance_officer" || it == "senior_compliance" }) {
            throw SanctionsException("forbidden_role", "Compliance role is required.", HttpStatus.FORBIDDEN)
        }
        val hit = repository.findHit(id)
            ?: throw SanctionsException("not_found", "Sanctions hit was not found.", HttpStatus.NOT_FOUND)
        if (hit.status != "OPEN") {
            throw SanctionsException("invalid_state", "Sanctions hit is not open.", HttpStatus.CONFLICT)
        }
        val decision = request.decision.trim().uppercase()
        if (decision != "CLEAR_FALSE_POSITIVE") {
            throw SanctionsException("invalid_decision", "Sanctions hit decision is invalid.", HttpStatus.BAD_REQUEST)
        }
        val rationale = request.rationale?.trim().orEmpty()
        if (rationale.length < 20) {
            throw SanctionsException("invalid_rationale", "Sanctions hit decision rationale must be at least 20 characters.", HttpStatus.BAD_REQUEST)
        }
        val matchedEntityId = hit.matchedEntityId
            ?: throw SanctionsException("invalid_hit", "Sanctions hit has no matched entity to clear.", HttpStatus.CONFLICT)
        if (!repository.clearHitFalsePositive(hit.id)) {
            throw SanctionsException("invalid_state", "Sanctions hit is not open.", HttpStatus.CONFLICT)
        }
        val decisionId = UUID.randomUUID()
        val exceptionId = UUID.randomUUID()
        val role = principal.roles.firstOrNull { it == "senior_compliance" || it == "compliance_officer" }
        repository.insertDecision(decisionId, hit.id, hit.status, rationale, principal.subject, role)
        repository.insertFalsePositiveException(exceptionId, hit.endUserId, hit.vendor, matchedEntityId, rationale, hit.id, principal.subject)
        auditRepository.write(
            eventType = "sanctions.hit_false_positive_cleared",
            actorType = "BACKOFFICE",
            actorId = principal.subjectUuid,
            subjectType = "SANCTIONS_HIT",
            subjectId = hit.id,
            outcome = "SUCCESS",
            metadataJson = """{"decision":"CLEAR_FALSE_POSITIVE","previousStatus":"${hit.status}","resultingStatus":"CLEARED_FALSE_POSITIVE","exceptionId":"$exceptionId","role":"${role ?: ""}"}""",
        )
        return SanctionsHitDecisionResponse(
            hitId = hit.id.toString(),
            previousStatus = hit.status,
            status = "CLEARED_FALSE_POSITIVE",
            decision = decision,
            exceptionId = exceptionId.toString(),
            decidedBySubject = principal.subject,
            decidedByRole = role,
            decidedAt = OffsetDateTime.now(),
        )
    }

    private fun SanctionsHitRecord.toResponse(): SanctionsHitResponse =
        SanctionsHitResponse(
            id = id.toString(),
            endUserId = endUserId.toString(),
            kycProfileId = kycProfileId?.toString(),
            status = status,
            reason = reason,
            vendor = vendor,
            matchScore = matchScore,
            matchedEntityId = matchedEntityId,
            matchedName = matchedName,
            requestId = requestId,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun List<String>.toJsonArray(): String =
        joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
}
