package com.minifin.platform.controls

import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ReadAuditRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun write(
        actorType: String,
        actorId: UUID?,
        actorReference: String?,
        subjectType: String,
        subjectId: UUID?,
        resourceType: String,
        resourceId: UUID,
        purpose: String,
        decision: String,
        metadataJson: String = "{}",
    ) {
        jdbcTemplate.update(
            """
            insert into audit.read_audit_log (
                actor_type,
                actor_id,
                actor_reference,
                subject_type,
                subject_id,
                resource_type,
                resource_id,
                purpose,
                decision,
                metadata
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            """.trimIndent(),
            actorType,
            actorId,
            actorReference,
            subjectType,
            subjectId,
            resourceType,
            resourceId,
            purpose,
            decision,
            metadataJson,
        )
    }
}
