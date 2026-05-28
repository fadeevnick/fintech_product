package com.minifin.platform.backoffice

import java.sql.ResultSet
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

data class BackofficeAuditFeedItemRecord(
    val entryId: String,
    val stream: String,
    val code: String,
    val actorType: String,
    val actorId: String?,
    val actorReference: String?,
    val subjectType: String,
    val subjectId: String?,
    val resourceType: String?,
    val resourceId: String?,
    val result: String,
    val metadataJson: String,
    val requestId: String?,
    val correlationId: String?,
    val createdAt: String,
)

@Repository
class BackofficeAuditRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun listFeed(stream: String, limit: Int): List<BackofficeAuditFeedItemRecord> {
        val normalizedStream = stream.trim().uppercase()
        return when (normalizedStream) {
            "ALL" -> jdbcTemplate.query(
                """
                select *
                  from (
                    select concat('AUDIT_LOG:', id::text) as entry_id,
                           'AUDIT_LOG' as stream,
                           event_type as code,
                           actor_type,
                           actor_id::text as actor_id,
                           null::text as actor_reference,
                           subject_type,
                           subject_id::text as subject_id,
                           null::text as resource_type,
                           null::text as resource_id,
                           outcome as result,
                           metadata::text as metadata_json,
                           request_id,
                           correlation_id,
                           created_at
                      from audit.audit_log
                    union all
                    select concat('READ_AUDIT_LOG:', id::text) as entry_id,
                           'READ_AUDIT_LOG' as stream,
                           purpose as code,
                           actor_type,
                           actor_id::text as actor_id,
                           actor_reference,
                           subject_type,
                           subject_id::text as subject_id,
                           resource_type,
                           resource_id::text as resource_id,
                           decision as result,
                           metadata::text as metadata_json,
                           null::text as request_id,
                           null::text as correlation_id,
                           created_at
                      from audit.read_audit_log
                  ) audit_feed
                 order by created_at desc, entry_id desc
                 limit ?
                """.trimIndent(),
                { rs, _ -> rs.toFeedItemRecord() },
                limit,
            )
            "AUDIT_LOG" -> jdbcTemplate.query(
                """
                select concat('AUDIT_LOG:', id::text) as entry_id,
                       'AUDIT_LOG' as stream,
                       event_type as code,
                       actor_type,
                       actor_id::text as actor_id,
                       null::text as actor_reference,
                       subject_type,
                       subject_id::text as subject_id,
                       null::text as resource_type,
                       null::text as resource_id,
                       outcome as result,
                       metadata::text as metadata_json,
                       request_id,
                       correlation_id,
                       created_at
                  from audit.audit_log
                 order by created_at desc, id desc
                 limit ?
                """.trimIndent(),
                { rs, _ -> rs.toFeedItemRecord() },
                limit,
            )
            "READ_AUDIT_LOG" -> jdbcTemplate.query(
                """
                select concat('READ_AUDIT_LOG:', id::text) as entry_id,
                       'READ_AUDIT_LOG' as stream,
                       purpose as code,
                       actor_type,
                       actor_id::text as actor_id,
                       actor_reference,
                       subject_type,
                       subject_id::text as subject_id,
                       resource_type,
                       resource_id::text as resource_id,
                       decision as result,
                       metadata::text as metadata_json,
                       null::text as request_id,
                       null::text as correlation_id,
                       created_at
                  from audit.read_audit_log
                 order by created_at desc, id desc
                 limit ?
                """.trimIndent(),
                { rs, _ -> rs.toFeedItemRecord() },
                limit,
            )
            else -> throw BackofficeException("invalid_stream", "Audit stream is invalid.", org.springframework.http.HttpStatus.BAD_REQUEST)
        }
    }

    private fun ResultSet.toFeedItemRecord(): BackofficeAuditFeedItemRecord =
        BackofficeAuditFeedItemRecord(
            entryId = getString("entry_id"),
            stream = getString("stream"),
            code = getString("code"),
            actorType = getString("actor_type"),
            actorId = getString("actor_id"),
            actorReference = getString("actor_reference"),
            subjectType = getString("subject_type"),
            subjectId = getString("subject_id"),
            resourceType = getString("resource_type"),
            resourceId = getString("resource_id"),
            result = getString("result"),
            metadataJson = getString("metadata_json"),
            requestId = getString("request_id"),
            correlationId = getString("correlation_id"),
            createdAt = getObject("created_at", java.time.OffsetDateTime::class.java).toString(),
        )
}

