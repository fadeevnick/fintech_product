package com.minifin.platform.sanctions

import java.math.BigDecimal
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
open class SanctionsRepository(private val jdbcTemplate: JdbcTemplate) {
    open fun insertHit(
        id: UUID,
        endUserId: UUID,
        kycProfileId: UUID,
        reason: String,
        matchScore: BigDecimal?,
        matchedEntityId: String?,
        matchedName: String?,
        requestId: String?,
    ) {
        jdbcTemplate.update(
            """
            insert into sanctions.sanctions_hits (
                id, end_user_id, kyc_profile_id, status, reason, vendor, match_score, matched_entity_id, matched_name, request_id
            )
            values (?, ?, ?, 'OPEN', ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            endUserId,
            kycProfileId,
            reason,
            SANCTIONS_VENDOR_OPENSANCTIONS,
            matchScore,
            matchedEntityId,
            matchedName,
            requestId,
        )
    }

    open fun hasActiveFalsePositiveException(endUserId: UUID, vendor: String, matchedEntityId: String): Boolean =
        jdbcTemplate.queryForObject(
            """
            select exists (
                select 1
                from sanctions.sanctions_false_positive_exceptions
                where end_user_id = ?
                  and vendor = ?
                  and matched_entity_id = ?
                  and revoked_at is null
            )
            """.trimIndent(),
            Boolean::class.java,
            endUserId,
            vendor,
            matchedEntityId,
        ) ?: false

    open fun listHits(): List<SanctionsHitRecord> =
        jdbcTemplate.query(
            """
            select id, end_user_id, kyc_profile_id, status, reason, vendor, match_score, matched_entity_id, matched_name, request_id, created_at, updated_at
            from sanctions.sanctions_hits
            order by created_at desc
            limit 100
            """.trimIndent(),
        ) { rs, _ -> rs.toHitRecord() }

    open fun findHit(id: UUID): SanctionsHitRecord? =
        jdbcTemplate.query(
            """
            select id, end_user_id, kyc_profile_id, status, reason, vendor, match_score, matched_entity_id, matched_name, request_id, created_at, updated_at
            from sanctions.sanctions_hits
            where id = ?
            """.trimIndent(),
            { rs, _ -> rs.toHitRecord() },
            id,
        ).firstOrNull()

    open fun clearHitFalsePositive(id: UUID): Boolean =
        jdbcTemplate.update(
            """
            update sanctions.sanctions_hits
            set status = 'CLEARED_FALSE_POSITIVE', updated_at = now()
            where id = ? and status = 'OPEN'
            """.trimIndent(),
            id,
        ) == 1

    open fun insertDecision(
        id: UUID,
        hitId: UUID,
        previousStatus: String,
        rationale: String,
        decidedBySubject: String,
        decidedByRole: String?,
    ) {
        jdbcTemplate.update(
            """
            insert into sanctions.sanctions_hit_decisions (
                id, hit_id, previous_status, decision, resulting_status, rationale, decided_by_subject, decided_by_role
            )
            values (?, ?, ?, 'CLEAR_FALSE_POSITIVE', 'CLEARED_FALSE_POSITIVE', ?, ?, ?)
            """.trimIndent(),
            id,
            hitId,
            previousStatus,
            rationale,
            decidedBySubject,
            decidedByRole,
        )
    }

    open fun insertFalsePositiveException(
        id: UUID,
        endUserId: UUID,
        vendor: String,
        matchedEntityId: String,
        rationale: String,
        createdFromHitId: UUID,
        createdBySubject: String,
    ) {
        jdbcTemplate.update(
            """
            insert into sanctions.sanctions_false_positive_exceptions (
                id, end_user_id, vendor, matched_entity_id, rationale, created_from_hit_id, created_by_subject
            )
            values (?, ?, ?, ?, ?, ?, ?)
            on conflict (end_user_id, vendor, matched_entity_id) do update
            set rationale = excluded.rationale,
                created_from_hit_id = excluded.created_from_hit_id,
                created_by_subject = excluded.created_by_subject,
                created_at = now(),
                revoked_at = null
            """.trimIndent(),
            id,
            endUserId,
            vendor,
            matchedEntityId,
            rationale,
            createdFromHitId,
            createdBySubject,
        )
    }

    private fun ResultSet.toHitRecord(): SanctionsHitRecord =
        SanctionsHitRecord(
            id = getObject("id", UUID::class.java),
            endUserId = getObject("end_user_id", UUID::class.java),
            kycProfileId = getObject("kyc_profile_id", UUID::class.java),
            status = getString("status"),
            reason = getString("reason"),
            vendor = getString("vendor"),
            matchScore = getBigDecimal("match_score"),
            matchedEntityId = getString("matched_entity_id"),
            matchedName = getString("matched_name"),
            requestId = getString("request_id"),
            createdAt = getObject("created_at", OffsetDateTime::class.java),
            updatedAt = getObject("updated_at", OffsetDateTime::class.java),
        )
}
