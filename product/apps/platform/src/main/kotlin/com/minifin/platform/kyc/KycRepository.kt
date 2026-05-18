package com.minifin.platform.kyc

import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class KycRepository(private val jdbcTemplate: JdbcTemplate) {
    fun findProfileByEndUserId(endUserId: UUID): KycProfileRecord? =
        jdbcTemplate.query(
            """
            select id, end_user_id, status, vendor_applicant_id, level_name, external_user_id, created_at, updated_at
            from kyc.kyc_profiles
            where end_user_id = ?
            """.trimIndent(),
            { rs, _ -> rs.toProfile() },
            endUserId,
        ).firstOrNull()

    fun findProfileByVendorApplicantId(vendorApplicantId: String): KycProfileRecord? =
        jdbcTemplate.query(
            """
            select id, end_user_id, status, vendor_applicant_id, level_name, external_user_id, created_at, updated_at
            from kyc.kyc_profiles
            where vendor = 'SUMSUB' and vendor_applicant_id = ?
            """.trimIndent(),
            { rs, _ -> rs.toProfile() },
            vendorApplicantId,
        ).firstOrNull()

    fun insertProfile(id: UUID, endUserId: UUID, externalUserId: String, levelName: String): KycProfileRecord {
        jdbcTemplate.update(
            """
            insert into kyc.kyc_profiles (id, end_user_id, status, vendor, external_user_id, level_name)
            values (?, ?, 'NOT_STARTED', 'SUMSUB', ?, ?)
            """.trimIndent(),
            id,
            endUserId,
            externalUserId,
            levelName,
        )
        return findProfileByEndUserId(endUserId) ?: error("Inserted KYC profile disappeared")
    }

    fun findActiveSession(profileId: UUID): KycSessionRecord? =
        jdbcTemplate.query(
            """
            select id, profile_id, vendor_applicant_id, external_user_id, status, expires_at
            from kyc.kyc_sessions
            where profile_id = ? and status in ('CREATED','ACTIVE')
            order by created_at desc
            limit 1
            """.trimIndent(),
            { rs, _ -> rs.toSession() },
            profileId,
        ).firstOrNull()

    fun markStartFailed(profileId: UUID, code: String, message: String) {
        jdbcTemplate.update(
            """
            insert into kyc.kyc_sessions (id, profile_id, vendor, external_user_id, status, failure_code, failure_message)
            select ?, id, 'SUMSUB', external_user_id, 'FAILED', ?, ?
            from kyc.kyc_profiles where id = ?
            """.trimIndent(),
            UUID.randomUUID(),
            code.take(80),
            message.take(500),
            profileId,
        )
    }

    fun createSession(profileId: UUID, vendorApplicantId: String, accessTokenHash: String, expiresAt: OffsetDateTime?): KycSessionRecord {
        val sessionId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            insert into kyc.kyc_sessions (id, profile_id, vendor, vendor_applicant_id, access_token_hash, external_user_id, status, expires_at)
            select ?, id, 'SUMSUB', ?, ?, external_user_id, 'ACTIVE', ?
            from kyc.kyc_profiles where id = ?
            """.trimIndent(),
            sessionId,
            vendorApplicantId,
            accessTokenHash,
            expiresAt,
            profileId,
        )
        jdbcTemplate.update(
            """
            update kyc.kyc_profiles
            set status = 'SUBMITTED', vendor_applicant_id = ?, submitted_at = coalesce(submitted_at, now()), updated_at = now(), version = version + 1
            where id = ?
            """.trimIndent(),
            vendorApplicantId,
            profileId,
        )
        return findActiveSession(profileId) ?: error("Inserted KYC session disappeared")
    }

    fun insertWebhookEvent(id: UUID, vendorEventId: String, vendorApplicantId: String?, eventType: String, reviewStatus: String?, reviewAnswer: String?, payloadSha256: String): Boolean =
        jdbcTemplate.update(
            """
            insert into kyc.sumsub_webhook_events (id, vendor_event_id, vendor_applicant_id, event_type, review_status, review_answer, payload_sha256)
            values (?, ?, ?, ?, ?, ?, ?)
            on conflict (vendor_event_id) do nothing
            """.trimIndent(),
            id,
            vendorEventId,
            vendorApplicantId,
            eventType,
            reviewStatus,
            reviewAnswer,
            payloadSha256,
        ) == 1

    fun applyWebhookEvent(vendorEventId: String, vendorApplicantId: String, status: String, reviewAnswer: String?, rejectType: String?, moderationComment: String?) {
        val timestampColumn = when (status) {
            "IN_REVIEW" -> "in_review_at"
            "APPROVED" -> "approved_at"
            "REJECTED" -> "rejected_at"
            "NEEDS_RESUBMIT" -> "needs_resubmit_at"
            else -> "updated_at"
        }
        jdbcTemplate.update(
            """
            update kyc.kyc_profiles
            set status = ?, review_answer = ?, review_reject_type = ?, review_moderation_comment = ?, $timestampColumn = coalesce($timestampColumn, now()), updated_at = now(), version = version + 1
            where vendor = 'SUMSUB' and vendor_applicant_id = ?
            """.trimIndent(),
            status,
            reviewAnswer,
            rejectType,
            moderationComment,
            vendorApplicantId,
        )
        jdbcTemplate.update("update kyc.sumsub_webhook_events set applied_at = now() where vendor_event_id = ?", vendorEventId)
    }

    private fun ResultSet.toProfile(): KycProfileRecord =
        KycProfileRecord(
            id = getObject("id", UUID::class.java),
            endUserId = getObject("end_user_id", UUID::class.java),
            status = getString("status"),
            vendorApplicantId = getString("vendor_applicant_id"),
            levelName = getString("level_name"),
            externalUserId = getString("external_user_id"),
            createdAt = getObject("created_at", OffsetDateTime::class.java),
            updatedAt = getObject("updated_at", OffsetDateTime::class.java),
        )

    private fun ResultSet.toSession(): KycSessionRecord =
        KycSessionRecord(
            id = getObject("id", UUID::class.java),
            profileId = getObject("profile_id", UUID::class.java),
            vendorApplicantId = getString("vendor_applicant_id"),
            externalUserId = getString("external_user_id"),
            status = getString("status"),
            expiresAt = getObject("expires_at", OffsetDateTime::class.java),
        )
}
