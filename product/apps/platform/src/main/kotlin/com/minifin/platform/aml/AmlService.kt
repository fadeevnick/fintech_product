package com.minifin.platform.aml

import com.minifin.platform.identity.AuditRepository
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
open class AmlService(
    private val amlRepository: AmlRepository,
    private val auditRepository: AuditRepository,
) {
    private val clock: Clock = Clock.systemUTC()
    private val ruleCode = "VELOCITY"
    private val severity = "MEDIUM"

    @Transactional
    open fun evaluateVelocity(request: AmlVelocityEvaluationRequest): AmlVelocityEvaluationResponse {
        if (!amlRepository.endUserExists(request.endUserId)) {
            throw AmlException("end_user_not_found", "End user was not found.", HttpStatus.NOT_FOUND)
        }
        val lookbackHours = request.lookbackHours ?: 24
        val thresholdCount = request.thresholdCount ?: 3
        if (lookbackHours <= 0 || lookbackHours > 24 * 30) {
            throw AmlException("invalid_lookback_hours", "lookbackHours must be between 1 and 720.", HttpStatus.BAD_REQUEST)
        }
        if (thresholdCount < 1 || thresholdCount > 1000) {
            throw AmlException("invalid_threshold_count", "thresholdCount must be between 1 and 1000.", HttpStatus.BAD_REQUEST)
        }

        val windowEndedAt = Instant.now(clock).truncatedTo(ChronoUnit.HOURS).plus(1, ChronoUnit.HOURS)
        val windowStartedAt = windowEndedAt.minus(lookbackHours, ChronoUnit.HOURS)
        val observedCount = amlRepository.countCompletedMoneyMovements(request.endUserId, windowStartedAt, windowEndedAt)
        val tripped = observedCount > thresholdCount
        val metadataJson = """{"lookbackHours":$lookbackHours,"source":"local_velocity_rule"}"""
        var duplicateSuppressed = false
        val alert = if (tripped) {
            amlRepository.findOpenAlert(request.endUserId, ruleCode, windowStartedAt, windowEndedAt)
                ?.also { duplicateSuppressed = true }
                ?: run {
                    val alertId = UUID.randomUUID()
                    val created = amlRepository.insertOpenAlert(
                        alertId,
                        request.endUserId,
                        ruleCode,
                        severity,
                        windowStartedAt,
                        windowEndedAt,
                        observedCount,
                        thresholdCount,
                        metadataJson,
                    )
                    if (created != null) {
                        auditRepository.write(
                            eventType = "aml.alert_created",
                            actorType = "SYSTEM",
                            actorId = null,
                            subjectType = "AML_ALERT",
                            subjectId = alertId,
                            outcome = "SUCCESS",
                            metadataJson = """{"endUserId":"${request.endUserId}","ruleCode":"$ruleCode","severity":"$severity","observedCount":$observedCount,"thresholdCount":$thresholdCount}""",
                        )
                        created
                    } else {
                        duplicateSuppressed = true
                        amlRepository.findOpenAlert(request.endUserId, ruleCode, windowStartedAt, windowEndedAt)
                    }
                }
        } else {
            null
        }

        amlRepository.insertEvaluation(
            UUID.randomUUID(),
            request.endUserId,
            ruleCode,
            windowStartedAt,
            windowEndedAt,
            observedCount,
            thresholdCount,
            tripped,
            alert?.id,
            metadataJson,
        )

        return AmlVelocityEvaluationResponse(
            endUserId = request.endUserId.toString(),
            ruleCode = ruleCode,
            tripped = tripped,
            alertId = alert?.id?.toString(),
            status = alert?.status,
            severity = severity,
            observedCount = observedCount,
            thresholdCount = thresholdCount,
            windowStartedAt = windowStartedAt.toString(),
            windowEndedAt = windowEndedAt.toString(),
            duplicateSuppressed = duplicateSuppressed,
        )
    }
}
