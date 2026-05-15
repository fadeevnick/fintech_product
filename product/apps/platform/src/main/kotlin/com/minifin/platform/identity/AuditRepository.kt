package com.minifin.platform.identity

import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class AuditRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun write(
        eventType: String,
        actorType: String,
        actorId: UUID?,
        subjectType: String,
        subjectId: UUID?,
        outcome: String,
        metadataJson: String = "{}",
    ) {
        jdbcTemplate.update(
            """
            insert into audit.audit_log (
                event_type,
                actor_type,
                actor_id,
                subject_type,
                subject_id,
                outcome,
                metadata
            )
            values (?, ?, ?, ?, ?, ?, ?::jsonb)
            """.trimIndent(),
            eventType,
            actorType,
            actorId,
            subjectType,
            subjectId,
            outcome,
            metadataJson,
        )
    }
}
