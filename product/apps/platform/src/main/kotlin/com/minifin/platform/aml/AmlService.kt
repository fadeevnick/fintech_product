package com.minifin.platform.aml

import com.minifin.platform.controls.ActorControlRepository
import com.minifin.platform.identity.AuditRepository
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
open class AmlService(
    private val amlRepository: AmlRepository,
    private val auditRepository: AuditRepository,
    private val actorControlRepository: ActorControlRepository,
) {
    private val clock: Clock = Clock.systemUTC()
    private val ruleCode = "VELOCITY"
    private val severity = "MEDIUM"
    private val structuringRuleCode = "STRUCTURING"
    private val structuringSeverity = "HIGH"
    private val dormancyBreakRuleCode = "DORMANCY_BREAK"
    private val dormancyBreakSeverity = "HIGH"

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

    @Transactional
    open fun evaluateStructuring(request: AmlStructuringEvaluationRequest): AmlStructuringEvaluationResponse {
        if (!amlRepository.endUserExists(request.endUserId)) {
            throw AmlException("end_user_not_found", "End user was not found.", HttpStatus.NOT_FOUND)
        }
        val lookbackHours = request.lookbackHours ?: 24
        val thresholdCount = request.thresholdCount ?: 2
        if (lookbackHours <= 0 || lookbackHours > 24 * 30) {
            throw AmlException("invalid_lookback_hours", "lookbackHours must be between 1 and 720.", HttpStatus.BAD_REQUEST)
        }
        if (thresholdCount < 1 || thresholdCount > 1000) {
            throw AmlException("invalid_threshold_count", "thresholdCount must be between 1 and 1000.", HttpStatus.BAD_REQUEST)
        }
        val reportingThreshold = request.reportingThreshold
            ?.let { runCatching { BigDecimal(it) }.getOrNull() }
            ?: BigDecimal("10000.00")
        if (reportingThreshold <= BigDecimal.ZERO) {
            throw AmlException("invalid_reporting_threshold", "reportingThreshold must be positive.", HttpStatus.BAD_REQUEST)
        }

        val minimumAmount = reportingThreshold.multiply(BigDecimal("0.90")).setScale(2, RoundingMode.HALF_UP)
        val maximumAmount = reportingThreshold.multiply(BigDecimal("0.99")).setScale(2, RoundingMode.HALF_UP)
        val windowEndedAt = Instant.now(clock).truncatedTo(ChronoUnit.HOURS).plus(1, ChronoUnit.HOURS)
        val windowStartedAt = windowEndedAt.minus(lookbackHours, ChronoUnit.HOURS)
        val observedCount = amlRepository.countStructuringMoneyMovements(
            request.endUserId,
            windowStartedAt,
            windowEndedAt,
            minimumAmount,
            maximumAmount,
        )
        val tripped = observedCount > thresholdCount
        val metadataJson = """{"lookbackHours":$lookbackHours,"source":"local_structuring_rule","reportingThreshold":"${reportingThreshold.setScale(2, RoundingMode.HALF_UP)}","minimumAmount":"$minimumAmount","maximumAmount":"$maximumAmount"}"""
        var duplicateSuppressed = false
        val alert = if (tripped) {
            amlRepository.findOpenAlert(request.endUserId, structuringRuleCode, windowStartedAt, windowEndedAt)
                ?.also { duplicateSuppressed = true }
                ?: run {
                    val alertId = UUID.randomUUID()
                    val created = amlRepository.insertOpenAlert(
                        alertId,
                        request.endUserId,
                        structuringRuleCode,
                        structuringSeverity,
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
                            metadataJson = """{"endUserId":"${request.endUserId}","ruleCode":"$structuringRuleCode","severity":"$structuringSeverity","observedCount":$observedCount,"thresholdCount":$thresholdCount}""",
                        )
                        created
                    } else {
                        duplicateSuppressed = true
                        amlRepository.findOpenAlert(request.endUserId, structuringRuleCode, windowStartedAt, windowEndedAt)
                    }
                }
        } else {
            null
        }

        amlRepository.insertEvaluation(
            UUID.randomUUID(),
            request.endUserId,
            structuringRuleCode,
            windowStartedAt,
            windowEndedAt,
            observedCount,
            thresholdCount,
            tripped,
            alert?.id,
            metadataJson,
        )

        return AmlStructuringEvaluationResponse(
            endUserId = request.endUserId.toString(),
            ruleCode = structuringRuleCode,
            tripped = tripped,
            alertId = alert?.id?.toString(),
            status = alert?.status,
            severity = structuringSeverity,
            observedCount = observedCount,
            thresholdCount = thresholdCount,
            windowStartedAt = windowStartedAt.toString(),
            windowEndedAt = windowEndedAt.toString(),
            duplicateSuppressed = duplicateSuppressed,
        )
    }

    @Transactional
    open fun evaluateDormancyBreak(request: AmlDormancyBreakEvaluationRequest): AmlDormancyBreakEvaluationResponse {
        if (!amlRepository.endUserExists(request.endUserId)) {
            throw AmlException("end_user_not_found", "End user was not found.", HttpStatus.NOT_FOUND)
        }
        val dormancyDays = request.dormancyDays ?: 30
        val lookbackHours = request.lookbackHours ?: 24
        if (dormancyDays <= 0 || dormancyDays > 3650) {
            throw AmlException("invalid_dormancy_days", "dormancyDays must be between 1 and 3650.", HttpStatus.BAD_REQUEST)
        }
        if (lookbackHours <= 0 || lookbackHours > 24 * 30) {
            throw AmlException("invalid_lookback_hours", "lookbackHours must be between 1 and 720.", HttpStatus.BAD_REQUEST)
        }
        val thresholdAmount = request.thresholdAmount
            ?.let { runCatching { BigDecimal(it) }.getOrNull() }
            ?: BigDecimal("1000.00")
        if (thresholdAmount <= BigDecimal.ZERO) {
            throw AmlException("invalid_threshold_amount", "thresholdAmount must be positive.", HttpStatus.BAD_REQUEST)
        }

        val normalizedThresholdAmount = thresholdAmount.setScale(2, RoundingMode.HALF_UP)
        val windowEndedAt = Instant.now(clock).truncatedTo(ChronoUnit.HOURS).plus(1, ChronoUnit.HOURS)
        val windowStartedAt = windowEndedAt.minus(lookbackHours, ChronoUnit.HOURS)
        val dormantStartedAt = windowStartedAt.minus(dormancyDays, ChronoUnit.DAYS)
        val summary = amlRepository.summarizeDormancyBreakActivity(
            request.endUserId,
            dormantStartedAt,
            windowStartedAt,
            windowEndedAt,
        )
        val observedAmount = summary.recentActivityAmount.setScale(2, RoundingMode.HALF_UP)
        val previousActivityFound = summary.previousActivityCount > 0
        val tripped = previousActivityFound &&
            summary.dormantGapActivityCount == 0 &&
            observedAmount > normalizedThresholdAmount
        val metadataJson = """{"dormancyDays":$dormancyDays,"lookbackHours":$lookbackHours,"source":"local_dormancy_break_rule","previousActivityCount":${summary.previousActivityCount},"dormantGapActivityCount":${summary.dormantGapActivityCount},"observedAmount":"$observedAmount","thresholdAmount":"$normalizedThresholdAmount"}"""
        var duplicateSuppressed = false
        val alert = if (tripped) {
            amlRepository.findOpenAlert(request.endUserId, dormancyBreakRuleCode, windowStartedAt, windowEndedAt)
                ?.also { duplicateSuppressed = true }
                ?: run {
                    val alertId = UUID.randomUUID()
                    val created = amlRepository.insertOpenAlert(
                        alertId,
                        request.endUserId,
                        dormancyBreakRuleCode,
                        dormancyBreakSeverity,
                        windowStartedAt,
                        windowEndedAt,
                        summary.recentActivityCount,
                        1,
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
                            metadataJson = """{"endUserId":"${request.endUserId}","ruleCode":"$dormancyBreakRuleCode","severity":"$dormancyBreakSeverity","observedAmount":"$observedAmount","thresholdAmount":"$normalizedThresholdAmount"}""",
                        )
                        created
                    } else {
                        duplicateSuppressed = true
                        amlRepository.findOpenAlert(request.endUserId, dormancyBreakRuleCode, windowStartedAt, windowEndedAt)
                    }
                }
        } else {
            null
        }

        amlRepository.insertEvaluation(
            UUID.randomUUID(),
            request.endUserId,
            dormancyBreakRuleCode,
            windowStartedAt,
            windowEndedAt,
            summary.recentActivityCount,
            1,
            tripped,
            alert?.id,
            metadataJson,
        )

        return AmlDormancyBreakEvaluationResponse(
            endUserId = request.endUserId.toString(),
            ruleCode = dormancyBreakRuleCode,
            tripped = tripped,
            alertId = alert?.id?.toString(),
            status = alert?.status,
            severity = dormancyBreakSeverity,
            observedCount = summary.recentActivityCount,
            thresholdCount = 1,
            observedAmount = observedAmount.toPlainString(),
            thresholdAmount = normalizedThresholdAmount.toPlainString(),
            previousActivityFound = previousActivityFound,
            dormantGapActivityCount = summary.dormantGapActivityCount,
            windowStartedAt = windowStartedAt.toString(),
            windowEndedAt = windowEndedAt.toString(),
            duplicateSuppressed = duplicateSuppressed,
        )
    }

    @Transactional
    open fun processCriticalAutoFreezes(): AmlCriticalAutoFreezeResponse {
        val alerts = amlRepository.listOpenCriticalAlerts(limit = 100)
        var frozenActorCount = 0
        for (alert in alerts) {
            actorControlRepository.upsert(
                actorType = "END_USER",
                actorId = alert.endUserId,
                state = "FROZEN",
                reasonCode = "aml_critical_alert",
                updatedByActorType = "SYSTEM",
                updatedByActorId = null,
                updatedByReference = "aml:${alert.id}",
            )
            val marked = amlRepository.markAlertFrozen(alert.id)
            if (marked) {
                frozenActorCount += 1
                auditRepository.write(
                    eventType = "identity.actor_control_changed",
                    actorType = "SYSTEM",
                    actorId = null,
                    subjectType = "END_USER",
                    subjectId = alert.endUserId,
                    outcome = "SUCCESS",
                    metadataJson = """{"state":"FROZEN","reasonCode":"aml_critical_alert","alertId":"${alert.id}","ruleCode":"${alert.ruleCode}"}""",
                )
                auditRepository.write(
                    eventType = "aml.critical_alert_auto_frozen",
                    actorType = "SYSTEM",
                    actorId = null,
                    subjectType = "AML_ALERT",
                    subjectId = alert.id,
                    outcome = "SUCCESS",
                    metadataJson = """{"endUserId":"${alert.endUserId}","ruleCode":"${alert.ruleCode}","actorControlState":"FROZEN"}""",
                )
            }
        }
        return AmlCriticalAutoFreezeResponse(
            processedAlertCount = alerts.size,
            frozenActorCount = frozenActorCount,
        )
    }
}
