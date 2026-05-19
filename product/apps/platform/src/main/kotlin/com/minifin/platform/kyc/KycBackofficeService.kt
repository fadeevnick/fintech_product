package com.minifin.platform.kyc

import com.minifin.platform.backoffice.BackofficePrincipal
import com.minifin.platform.identity.AuditRepository
import com.minifin.platform.sanctions.SanctionsException
import com.minifin.platform.sanctions.SanctionsService
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class KycBackofficeService(
    private val repository: KycRepository,
    private val auditRepository: AuditRepository,
    private val sanctionsService: SanctionsService,
) {
    fun listCases(): List<KycCaseResponse> =
        repository.listReviewCases().map { it.toResponse() }

    fun getCase(id: UUID): KycCaseResponse =
        (repository.findCase(id) ?: throw KycException("not_found", "KYC case was not found.", HttpStatus.NOT_FOUND)).toResponse()

    @Transactional(noRollbackFor = [SanctionsException::class])
    fun decide(id: UUID, request: KycManualDecisionRequest, principal: BackofficePrincipal): KycManualDecisionResponse {
        val case = repository.findCase(id)
            ?: throw KycException("not_found", "KYC case was not found.", HttpStatus.NOT_FOUND)
        if (case.status != "IN_REVIEW") {
            throw KycException("invalid_state", "KYC case is not in manual review.", HttpStatus.CONFLICT)
        }
        val decision = request.decision.trim().uppercase()
        val resultingStatus = when (decision) {
            "APPROVE" -> "APPROVED"
            "REJECT" -> "REJECTED"
            "REQUEST_RESUBMIT" -> "NEEDS_RESUBMIT"
            else -> throw KycException("invalid_decision", "KYC decision is invalid.", HttpStatus.BAD_REQUEST)
        }
        val rationale = request.rationale?.trim().orEmpty()
        if (rationale.length < 20) {
            throw KycException("invalid_rationale", "KYC decision rationale must be at least 20 characters.", HttpStatus.BAD_REQUEST)
        }
        if (decision == "APPROVE") {
            sanctionsService.requireKycApprovalAllowed(case.endUserId, case.id, principal.subjectUuid)
        }
        if (!repository.applyManualDecision(case.id, "IN_REVIEW", resultingStatus)) {
            throw KycException("invalid_state", "KYC case is not in manual review.", HttpStatus.CONFLICT)
        }
        val decisionId = UUID.randomUUID()
        val role = principal.roles.firstOrNull()
        repository.insertManualDecision(decisionId, case.id, case.status, decision, resultingStatus, rationale, principal.subject, role)
        auditRepository.write(
            eventType = "kyc.manual_decision_recorded",
            actorType = "BACKOFFICE",
            actorId = principal.subjectUuid,
            subjectType = "KYC_PROFILE",
            subjectId = case.id,
            outcome = "SUCCESS",
            metadataJson = """{"decision":"$decision","previousStatus":"${case.status}","resultingStatus":"$resultingStatus","role":"${role ?: ""}"}""",
        )
        return KycManualDecisionResponse(
            caseId = case.id.toString(),
            previousStatus = case.status,
            status = resultingStatus,
            decision = decision,
            decidedBySubject = principal.subject,
            decidedByRole = role,
            decidedAt = OffsetDateTime.now(),
        )
    }

    private fun KycCaseRecord.toResponse(): KycCaseResponse =
        KycCaseResponse(
            id = id.toString(),
            endUserId = endUserId.toString(),
            status = status,
            vendor = vendor,
            vendorApplicantId = vendorApplicantId,
            levelName = levelName,
            externalUserId = externalUserId,
            reviewAnswer = reviewAnswer,
            reviewRejectType = reviewRejectType,
            reviewModerationComment = reviewModerationComment,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
