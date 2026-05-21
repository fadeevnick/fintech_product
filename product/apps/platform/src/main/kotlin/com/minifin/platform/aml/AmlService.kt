package com.minifin.platform.aml

import com.minifin.platform.backoffice.BackofficePrincipal
import com.minifin.platform.controls.ActorControlRepository
import com.minifin.platform.identity.AuditRepository
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
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

    open fun listAlerts(): List<AmlAlertResponse> =
        amlRepository.listReviewableAlerts().map { it.toResponse() }

    open fun getAlert(id: UUID): AmlAlertResponse {
        val record = amlRepository.findAlertById(id)
            ?: throw AmlException("not_found", "AML alert was not found.", HttpStatus.NOT_FOUND)
        return record.toResponse()
    }

    @Transactional
    open fun decideAlert(id: UUID, request: AmlAlertDecisionRequest, principal: BackofficePrincipal): AmlAlertDecisionResponse {
        val alert = amlRepository.findAlertById(id)
            ?: throw AmlException("not_found", "AML alert was not found.", HttpStatus.NOT_FOUND)

        val decision = request.decision.trim().uppercase()
        if (decision !in setOf("CLOSED_FALSE_POSITIVE", "ESCALATED", "MARKED_FOR_SAR")) {
            throw AmlException("invalid_decision", "Decision must be CLOSED_FALSE_POSITIVE, ESCALATED or MARKED_FOR_SAR.", HttpStatus.BAD_REQUEST)
        }

        val rationale = request.rationale?.trim().orEmpty()
        if (rationale.length < 20) {
            throw AmlException("invalid_rationale", "Decision rationale must be at least 20 characters.", HttpStatus.BAD_REQUEST)
        }

        if (decision == "MARKED_FOR_SAR" && principal.roles.none { it == "compliance_officer" || it == "senior_compliance" }) {
            throw AmlException("forbidden_role", "MARKED_FOR_SAR requires compliance_officer or senior_compliance role.", HttpStatus.FORBIDDEN)
        }

        val (resultingStatus, allowedFromStatuses) = when (decision) {
            "CLOSED_FALSE_POSITIVE" -> "CLOSED_FALSE_POSITIVE" to setOf("OPEN", "ACCOUNT_FROZEN_PERMANENT")
            "ESCALATED" -> "ESCALATED" to setOf("OPEN")
            "MARKED_FOR_SAR" -> "MARKED_FOR_SAR" to setOf("OPEN")
            else -> throw AmlException("invalid_decision", "Unrecognized decision.", HttpStatus.BAD_REQUEST)
        }

        if (alert.status !in allowedFromStatuses) {
            throw AmlException("invalid_state", "AML alert status '${alert.status}' does not allow decision '$decision'.", HttpStatus.CONFLICT)
        }

        val transitioned = amlRepository.transitionAlertStatus(id, allowedFromStatuses, resultingStatus)
        if (!transitioned) {
            throw AmlException("invalid_state", "AML alert could not be transitioned; it may have been updated concurrently.", HttpStatus.CONFLICT)
        }

        val role = principal.roles.firstOrNull { it == "senior_compliance" || it == "compliance_officer" || it == "backoffice_operator" }
        amlRepository.insertAlertDecision(
            UUID.randomUUID(),
            id,
            alert.status,
            resultingStatus,
            decision,
            rationale,
            principal.subject,
            role,
        )

        var unfrozeActor = false
        if (decision == "CLOSED_FALSE_POSITIVE") {
            val control = actorControlRepository.find("END_USER", alert.endUserId)
            if (control != null && control.state == "FROZEN" && control.reasonCode == "aml_critical_alert") {
                actorControlRepository.upsert(
                    actorType = "END_USER",
                    actorId = alert.endUserId,
                    state = "ACTIVE",
                    reasonCode = "aml_false_positive_review",
                    updatedByActorType = "BACKOFFICE",
                    updatedByActorId = principal.subjectUuid,
                    updatedByReference = principal.subject,
                )
                auditRepository.write(
                    eventType = "identity.actor_control_changed",
                    actorType = "BACKOFFICE",
                    actorId = principal.subjectUuid,
                    subjectType = "END_USER",
                    subjectId = alert.endUserId,
                    outcome = "SUCCESS",
                    metadataJson = """{"state":"ACTIVE","reasonCode":"aml_false_positive_review","alertId":"$id","previousState":"FROZEN"}""",
                )
                unfrozeActor = true
            }
        }

        auditRepository.write(
            eventType = "aml.alert_reviewed",
            actorType = "BACKOFFICE",
            actorId = principal.subjectUuid,
            subjectType = "AML_ALERT",
            subjectId = id,
            outcome = "SUCCESS",
            metadataJson = """{"decision":"$decision","previousStatus":"${alert.status}","resultingStatus":"$resultingStatus","endUserId":"${alert.endUserId}","ruleCode":"${alert.ruleCode}","role":"${role ?: ""}","unfrozeActor":$unfrozeActor}""",
        )

        return AmlAlertDecisionResponse(
            alertId = id.toString(),
            endUserId = alert.endUserId.toString(),
            previousStatus = alert.status,
            status = resultingStatus,
            decision = decision,
            decidedBySubject = principal.subject,
            decidedByRole = role,
            unfrozeActor = unfrozeActor,
            decidedAt = OffsetDateTime.now(),
        )
    }

    private fun AmlAlertRecord.toResponse(): AmlAlertResponse =
        AmlAlertResponse(
            id = id.toString(),
            endUserId = endUserId.toString(),
            ruleCode = ruleCode,
            severity = severity,
            status = status,
            windowStartedAt = windowStartedAt,
            windowEndedAt = windowEndedAt,
            observedCount = observedCount,
            thresholdCount = thresholdCount,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

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
