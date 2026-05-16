package com.minifin.platform.controls

import com.minifin.platform.backoffice.BackofficePrincipal
import com.minifin.platform.identity.AuditRepository
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ActorControlService(
    private val actorControlRepository: ActorControlRepository,
    private val readAuditRepository: ReadAuditRepository,
    private val auditRepository: AuditRepository,
) {
    @Transactional
    fun writeReadAuditProbe(principal: BackofficePrincipal, resourceId: UUID) {
        readAuditRepository.write(
            actorType = "BACKOFFICE",
            actorId = principal.subjectUuid,
            actorReference = principal.subject,
            subjectType = "READ_AUDIT_PROBE",
            subjectId = resourceId,
            resourceType = "READ_AUDIT_PROBE",
            resourceId = resourceId,
            purpose = "runtime_probe",
            decision = "ALLOW",
            metadataJson = """{"roles":${principal.roles.toJsonArray()}}""",
        )
    }

    @Transactional
    fun setControl(request: ActorControlRequest, principal: BackofficePrincipal): ActorControlResponse {
        val actorType = validateActorType(request.actorType)
        val actorId = requireUuid(request.actorId)
        val state = validateState(request.state)
        val reasonCode = validateReasonCode(request.reasonCode)
        val record = actorControlRepository.upsert(
            actorType = actorType,
            actorId = actorId,
            state = state,
            reasonCode = reasonCode,
            updatedByActorType = "BACKOFFICE",
            updatedByActorId = principal.subjectUuid,
            updatedByReference = principal.subject,
        )
        auditRepository.write(
            eventType = "identity.actor_control_changed",
            actorType = "BACKOFFICE",
            actorId = principal.subjectUuid,
            subjectType = actorType,
            subjectId = actorId,
            outcome = "SUCCESS",
            metadataJson = """{"state":"$state","reasonCode":"$reasonCode"}""",
        )
        return record.toResponse()
    }

    @Transactional(noRollbackFor = [ActorControlException::class])
    fun requireWriteAllowed(actorType: String, actorId: UUID) {
        val normalizedActorType = validateActorType(actorType)
        val control = actorControlRepository.find(normalizedActorType, actorId)
        if (control != null && control.state != "ACTIVE") {
            auditRepository.write(
                eventType = "identity.actor_control_write_denied",
                actorType = normalizedActorType,
                actorId = actorId,
                subjectType = normalizedActorType,
                subjectId = actorId,
                outcome = "DENIED",
                metadataJson = """{"state":"${control.state}","reasonCode":"${control.reasonCode}"}""",
            )
            throw ActorControlException(
                code = "actor_control_blocked",
                message = "Actor is blocked or frozen.",
                status = HttpStatus.FORBIDDEN,
            )
        }
    }

    private fun validateActorType(value: String): String {
        val normalized = value.trim().uppercase()
        if (normalized !in setOf("END_USER", "MERCHANT")) {
            throw ActorControlException(
                code = "invalid_actor_type",
                message = "Actor type is invalid.",
                status = HttpStatus.BAD_REQUEST,
            )
        }
        return normalized
    }

    private fun validateState(value: String): String {
        val normalized = value.trim().uppercase()
        if (normalized !in setOf("ACTIVE", "BLOCKED", "FROZEN")) {
            throw ActorControlException(
                code = "invalid_control_state",
                message = "Actor control state is invalid.",
                status = HttpStatus.BAD_REQUEST,
            )
        }
        return normalized
    }

    private fun validateReasonCode(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank() || trimmed.length > 80) {
            throw ActorControlException(
                code = "invalid_reason_code",
                message = "Reason code is invalid.",
                status = HttpStatus.BAD_REQUEST,
            )
        }
        return trimmed
    }

    private fun requireUuid(value: String): UUID =
        runCatching { UUID.fromString(value) }
            .getOrElse {
                throw ActorControlException(
                    code = "invalid_uuid",
                    message = "Invalid UUID.",
                    status = HttpStatus.BAD_REQUEST,
                )
            }

    private fun ActorControlRecord.toResponse(): ActorControlResponse =
        ActorControlResponse(
            actorType = actorType,
            actorId = actorId.toString(),
            state = state,
            reasonCode = reasonCode,
        )

    private fun List<String>.toJsonArray(): String =
        joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
}
