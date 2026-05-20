package com.minifin.platform.aml

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

data class AmlAlert(
    val id: UUID,
    val status: String,
)

class AmlException(
    val code: String,
    override val message: String,
    val status: HttpStatus,
) : RuntimeException(message)
