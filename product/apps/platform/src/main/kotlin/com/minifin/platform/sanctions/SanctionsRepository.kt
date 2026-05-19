package com.minifin.platform.sanctions

import java.math.BigDecimal
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class SanctionsRepository(private val jdbcTemplate: JdbcTemplate) {
    fun insertHit(
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
}
