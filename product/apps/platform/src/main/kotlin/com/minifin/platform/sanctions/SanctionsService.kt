package com.minifin.platform.sanctions

import com.minifin.platform.identity.AuditRepository
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

@Service
class SanctionsService(
    private val client: OpenSanctionsClient,
    private val repository: SanctionsRepository,
    private val auditRepository: AuditRepository,
) {
    fun requireKycApprovalAllowed(endUserId: UUID, kycProfileId: UUID, actorId: UUID?) {
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
}
