package com.minifin.platform.aml

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.http.HttpStatus

data class AmlVelocityEvaluationRequest(
    val endUserId: UUID,
    val lookbackHours: Long? = null,
    val thresholdCount: Int? = null,
)

data class AmlStructuringEvaluationRequest(
    val endUserId: UUID,
    val lookbackHours: Long? = null,
    val thresholdCount: Int? = null,
    val reportingThreshold: String? = null,
)

data class AmlDormancyBreakEvaluationRequest(
    val endUserId: UUID,
    val dormancyDays: Long? = null,
    val lookbackHours: Long? = null,
    val thresholdAmount: String? = null,
)

data class AmlVelocityEvaluationResponse(
    val endUserId: String,
    val ruleCode: String,
    val tripped: Boolean,
    val alertId: String?,
    val status: String?,
    val severity: String,
    val observedCount: Int,
    val thresholdCount: Int,
    val windowStartedAt: String,
    val windowEndedAt: String,
    val duplicateSuppressed: Boolean,
)

data class AmlStructuringEvaluationResponse(
    val endUserId: String,
    val ruleCode: String,
    val tripped: Boolean,
    val alertId: String?,
    val status: String?,
    val severity: String,
    val observedCount: Int,
    val thresholdCount: Int,
    val windowStartedAt: String,
    val windowEndedAt: String,
    val duplicateSuppressed: Boolean,
)

data class AmlDormancyBreakEvaluationResponse(
    val endUserId: String,
    val ruleCode: String,
    val tripped: Boolean,
    val alertId: String?,
    val status: String?,
    val severity: String,
    val observedCount: Int,
    val thresholdCount: Int,
    val observedAmount: String,
    val thresholdAmount: String,
    val previousActivityFound: Boolean,
    val dormantGapActivityCount: Int,
    val windowStartedAt: String,
    val windowEndedAt: String,
    val duplicateSuppressed: Boolean,
)

data class AmlCriticalAutoFreezeResponse(
    val processedAlertCount: Int,
    val frozenActorCount: Int,
)

data class AmlAlert(
    val id: UUID,
    val status: String,
)

data class AmlCriticalAlert(
    val id: UUID,
    val endUserId: UUID,
    val ruleCode: String,
)

data class AmlDormancyBreakActivitySummary(
    val previousActivityCount: Int,
    val dormantGapActivityCount: Int,
    val recentActivityCount: Int,
    val recentActivityAmount: BigDecimal,
)

data class AmlAlertRecord(
    val id: UUID,
    val endUserId: UUID,
    val ruleCode: String,
    val severity: String,
    val status: String,
    val windowStartedAt: OffsetDateTime,
    val windowEndedAt: OffsetDateTime,
    val observedCount: Int,
    val thresholdCount: Int,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class AmlAlertResponse(
    val id: String,
    val endUserId: String,
    val ruleCode: String,
    val severity: String,
    val status: String,
    val windowStartedAt: OffsetDateTime,
    val windowEndedAt: OffsetDateTime,
    val observedCount: Int,
    val thresholdCount: Int,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class AmlAlertDecisionRequest(
    val decision: String,
    val rationale: String?,
)

data class AmlAlertDecisionResponse(
    val alertId: String,
    val endUserId: String,
    val previousStatus: String,
    val status: String,
    val decision: String,
    val decidedBySubject: String,
    val decidedByRole: String?,
    val unfrozeActor: Boolean,
    val decidedAt: OffsetDateTime,
)

class AmlException(
    val code: String,
    override val message: String,
    val status: HttpStatus,
) : RuntimeException(message)
