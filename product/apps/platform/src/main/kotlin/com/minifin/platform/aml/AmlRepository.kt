package com.minifin.platform.aml

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
open class AmlRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    open fun endUserExists(endUserId: UUID): Boolean =
        jdbcTemplate.queryForObject(
            "select exists(select 1 from identity.end_users where id = ?)",
            Boolean::class.java,
            endUserId,
        ) == true

    open fun countCompletedMoneyMovements(endUserId: UUID, windowStartedAt: Instant, windowEndedAt: Instant): Int =
        jdbcTemplate.queryForObject(
            """
            select count(*) from (
                select id
                  from wallet.deposit_requests
                 where user_id = ?
                   and state = 'COMPLETED'
                   and updated_at >= ?
                   and updated_at < ?
                union all
                select id
                  from wallet.withdraw_requests
                 where user_id = ?
                   and state = 'COMPLETED'
                   and updated_at >= ?
                   and updated_at < ?
                union all
                select id
                  from wallet.internal_transfers
                 where sender_user_id = ?
                   and state = 'COMPLETED'
                   and coalesce(completed_at, created_at) >= ?
                   and coalesce(completed_at, created_at) < ?
                union all
                select id
                  from wallet.internal_transfers
                 where receiver_user_id = ?
                   and state = 'COMPLETED'
                   and coalesce(completed_at, created_at) >= ?
                   and coalesce(completed_at, created_at) < ?
            ) movements
            """.trimIndent(),
            Int::class.java,
            endUserId,
            Timestamp.from(windowStartedAt),
            Timestamp.from(windowEndedAt),
            endUserId,
            Timestamp.from(windowStartedAt),
            Timestamp.from(windowEndedAt),
            endUserId,
            Timestamp.from(windowStartedAt),
            Timestamp.from(windowEndedAt),
            endUserId,
            Timestamp.from(windowStartedAt),
            Timestamp.from(windowEndedAt),
        ) ?: 0

    open fun findOpenAlert(endUserId: UUID, ruleCode: String, windowStartedAt: Instant, windowEndedAt: Instant): AmlAlert? =
        jdbcTemplate.query(
            """
            select id, status
              from aml.aml_alerts
             where end_user_id = ?
               and rule_code = ?
               and window_started_at = ?
               and window_ended_at = ?
               and status = 'OPEN'
             limit 1
            """.trimIndent(),
            { rs, _ -> AmlAlert(rs.getObject("id", UUID::class.java), rs.getString("status")) },
            endUserId,
            ruleCode,
            Timestamp.from(windowStartedAt),
            Timestamp.from(windowEndedAt),
        ).firstOrNull()

    open fun insertOpenAlert(
        alertId: UUID,
        endUserId: UUID,
        ruleCode: String,
        severity: String,
        windowStartedAt: Instant,
        windowEndedAt: Instant,
        observedCount: Int,
        thresholdCount: Int,
        metadataJson: String,
    ): AmlAlert? =
        try {
            jdbcTemplate.update(
                """
                insert into aml.aml_alerts (
                    id,
                    end_user_id,
                    rule_code,
                    severity,
                    status,
                    window_started_at,
                    window_ended_at,
                    observed_count,
                    threshold_count,
                    metadata
                )
                values (?, ?, ?, ?, 'OPEN', ?, ?, ?, ?, ?::jsonb)
                """.trimIndent(),
                alertId,
                endUserId,
                ruleCode,
                severity,
                Timestamp.from(windowStartedAt),
                Timestamp.from(windowEndedAt),
                observedCount,
                thresholdCount,
                metadataJson,
            )
            AmlAlert(alertId, "OPEN")
        } catch (_: DuplicateKeyException) {
            null
        }

    open fun insertEvaluation(
        evaluationId: UUID,
        endUserId: UUID,
        ruleCode: String,
        windowStartedAt: Instant,
        windowEndedAt: Instant,
        observedCount: Int,
        thresholdCount: Int,
        tripped: Boolean,
        alertId: UUID?,
        metadataJson: String,
    ) {
        jdbcTemplate.update(
            """
            insert into aml.aml_rule_evaluations (
                id,
                end_user_id,
                rule_code,
                window_started_at,
                window_ended_at,
                observed_count,
                threshold_count,
                tripped,
                alert_id,
                metadata
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            """.trimIndent(),
            evaluationId,
            endUserId,
            ruleCode,
            Timestamp.from(windowStartedAt),
            Timestamp.from(windowEndedAt),
            observedCount,
            thresholdCount,
            tripped,
            alertId,
            metadataJson,
        )
    }
}
