package com.minifin.platform.backoffice

import com.minifin.platform.controls.ReadAuditRepository
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BackofficeAuditService(
    private val repository: BackofficeAuditRepository,
    private val readAuditRepository: ReadAuditRepository,
) {
    private val auditViewerResourceId: UUID = UUID.nameUUIDFromBytes("BACKOFFICE_AUDIT_LOG_VIEWER".toByteArray(StandardCharsets.UTF_8))

    @Transactional
    fun listFeed(principal: BackofficePrincipal, stream: String?, limit: Int?): BackofficeAuditFeedResponse {
        val normalizedLimit = (limit ?: 50).coerceIn(1, 200)
        val normalizedStream = stream?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: "ALL"
        readAuditRepository.write(
            actorType = "BACKOFFICE",
            actorId = principal.subjectUuid,
            actorReference = principal.subject,
            subjectType = "AUDIT_LOG_VIEWER",
            subjectId = null,
            resourceType = "AUDIT_LOG_VIEWER",
            resourceId = auditViewerResourceId,
            purpose = "backoffice_audit_log_view",
            decision = "ALLOW",
            metadataJson = """{"roles":${principal.roles.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }},"stream":"$normalizedStream","limit":$normalizedLimit}""",
        )
        return BackofficeAuditFeedResponse(
            items = repository.listFeed(normalizedStream, normalizedLimit).map { record ->
                BackofficeAuditFeedItemResponse(
                    entryId = record.entryId,
                    stream = record.stream,
                    code = record.code,
                    actorType = record.actorType,
                    actorId = record.actorId,
                    actorReference = record.actorReference,
                    subjectType = record.subjectType,
                    subjectId = record.subjectId,
                    resourceType = record.resourceType,
                    resourceId = record.resourceId,
                    result = record.result,
                    metadataJson = record.metadataJson,
                    requestId = record.requestId,
                    correlationId = record.correlationId,
                    createdAt = record.createdAt,
                )
            },
        )
    }
}

