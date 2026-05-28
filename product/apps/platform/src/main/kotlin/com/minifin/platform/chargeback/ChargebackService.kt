package com.minifin.platform.chargeback

import com.minifin.platform.identity.AuditRepository
import com.minifin.platform.identity.EndUserRecord
import com.minifin.platform.identity.MerchantEmployeeRecord
import com.minifin.platform.ledger.LedgerRepository
import com.minifin.platform.backoffice.BackofficePrincipal
import com.minifin.platform.backoffice.BackofficeException
import com.minifin.platform.controls.ReadAuditRepository
import com.minifin.platform.merchant.dashboard.MerchantDashboardException
import com.minifin.platform.merchant.webhooks.OutboundWebhookService
import java.math.RoundingMode
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

class ChargebackException(
    val code: String,
    override val message: String,
    val status: HttpStatus,
    val field: String? = null,
) : RuntimeException(message)

@Service
class ChargebackService(
    private val repository: ChargebackRepository,
    private val auditRepository: AuditRepository,
    private val outboundWebhookService: OutboundWebhookService,
    private val ledgerRepository: LedgerRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val properties: ChargebackProperties,
    private val evidenceObjectStorage: ChargebackEvidenceObjectStorage,
    private val readAuditRepository: ReadAuditRepository,
) {
    private val reasonCodes = setOf(
        "fraud_no_authorization",
        "goods_not_received",
        "goods_not_as_described",
        "duplicate_charge",
    )

    @Transactional
    fun createDispute(user: EndUserRecord, paymentIntentId: UUID, request: CreateDisputeRequest): ChargebackDisputeDto {
        val reasonCode = request.reasonCode?.trim()?.lowercase()
            ?: throw ChargebackException("invalid_reason_code", "Reason code is required.", HttpStatus.BAD_REQUEST, "reasonCode")
        if (reasonCode !in reasonCodes) {
            throw ChargebackException("invalid_reason_code", "Reason code is not supported.", HttpStatus.BAD_REQUEST, "reasonCode")
        }
        val narrative = request.narrative?.trim()?.takeIf { it.isNotEmpty() }?.also {
            if (it.length > 1000) {
                throw ChargebackException("invalid_narrative", "Narrative must be at most 1000 characters.", HttpStatus.BAD_REQUEST, "narrative")
            }
        }
        val payment = repository.findPayment(paymentIntentId)
            ?: throw ChargebackException("payment_not_found", "Payment was not found.", HttpStatus.NOT_FOUND)
        val existing = repository.findDisputeByPaymentIntent(paymentIntentId)
        if (existing != null) {
            if (existing.cardholderUserId != user.id) {
                throw ChargebackException("payment_not_found", "Payment was not found.", HttpStatus.NOT_FOUND)
            }
            return existing.toDto()
        }
        if (payment.cardholderUserId != user.id) {
            throw ChargebackException("payment_not_found", "Payment was not found.", HttpStatus.NOT_FOUND)
        }
        val walletLedgerAccountId = payment.cardholderWalletLedgerAccountId
            ?: throw ChargebackException("wallet_missing", "Cardholder wallet is missing.", HttpStatus.INTERNAL_SERVER_ERROR)
        if (!isKycApproved(user.id)) {
            throw ChargebackException("kyc_not_approved", "KYC approval is required to dispute a payment.", HttpStatus.FORBIDDEN)
        }
        if (payment.state != "SETTLED") {
            throw ChargebackException("invalid_payment_state", "Only settled payments can be disputed.", HttpStatus.CONFLICT)
        }
        if (payment.currency != "EUR") {
            throw ChargebackException("unsupported_currency", "Only EUR payments can be disputed.", HttpStatus.BAD_REQUEST, "currency")
        }
        val paymentDate = payment.settledAt ?: payment.capturedAt
            ?: throw ChargebackException("invalid_payment_state", "Only settled payments can be disputed.", HttpStatus.CONFLICT)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        if (paymentDate.plus(properties.disputeWindow).isBefore(now)) {
            throw ChargebackException("dispute_window_expired", "Payment is outside the dispute window.", HttpStatus.CONFLICT)
        }

        val disputeId = UUID.randomUUID()
        repository.insertDispute(
            id = disputeId,
            paymentIntentId = payment.id,
            merchantId = payment.merchantId,
            cardholderUserId = user.id,
            amount = payment.amount,
            currency = payment.currency,
            reasonCode = reasonCode,
            narrative = narrative,
            merchantResponseDeadline = now.plus(properties.merchantResponseDeadline),
        )
        repository.markPaymentDisputed(payment.id)
        val insertedDispute = repository.findDisputeByPaymentIntent(payment.id) ?: error("Dispute disappeared after insert")
        val provisionalCreditJournalId = postProvisionalCredit(insertedDispute, walletLedgerAccountId)
        repository.markProvisionalCreditJournal(insertedDispute.id, provisionalCreditJournalId)
        val dispute = repository.findDisputeByPaymentIntent(payment.id) ?: error("Dispute disappeared after provisional credit")
        auditRepository.write(
            eventType = "chargeback.initiated",
            actorType = "END_USER",
            actorId = user.id,
            subjectType = "CHARGEBACK",
            subjectId = dispute.id,
            outcome = "SUCCESS",
            metadataJson = """{"paymentIntentId":"${payment.id}","reasonCode":"$reasonCode","provisionalCreditJournalId":"$provisionalCreditJournalId"}""",
        )
        val dto = dispute.toDto()
        outboundWebhookService.publishDisputeCreated(
            disputeId = dispute.id,
            merchantId = dispute.merchantId,
            paymentIntentId = dispute.paymentIntentId,
            amount = dto.amount,
            currency = dto.currency,
            reasonCode = dto.reasonCode,
            state = dto.state,
            merchantResponseDeadline = dto.merchantResponseDeadline,
            createdAt = dto.createdAt,
        )
        return dto
    }

    @Transactional
    fun submitEvidence(employee: MerchantEmployeeRecord, disputeId: UUID, request: SubmitEvidenceRequest): EvidenceSubmissionDto {
        requireActiveMerchant(employee)
        val dispute = repository.findDisputeById(disputeId)
            ?: throw MerchantDashboardException("dispute_not_found", "Dispute was not found.", HttpStatus.NOT_FOUND)
        if (dispute.merchantId != employee.merchantId) {
            throw MerchantDashboardException("dispute_not_found", "Dispute was not found.", HttpStatus.NOT_FOUND)
        }
        if (dispute.state != "MERCHANT_NOTIFIED") {
            throw MerchantDashboardException("invalid_state", "Evidence can only be submitted for merchant-notified disputes.", HttpStatus.CONFLICT)
        }
        if (dispute.merchantResponseDeadline.isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw MerchantDashboardException("merchant_deadline_expired", "Merchant evidence deadline has expired.", HttpStatus.CONFLICT)
        }
        val narrative = request.narrative?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw MerchantDashboardException("invalid_narrative", "Evidence narrative is required.", HttpStatus.BAD_REQUEST, "narrative")
        if (narrative.length > 4000) {
            throw MerchantDashboardException("invalid_narrative", "Evidence narrative must be at most 4000 characters.", HttpStatus.BAD_REQUEST, "narrative")
        }
        if (request.attachments.size > 5) {
            throw MerchantDashboardException("too_many_attachments", "At most 5 evidence attachments are supported.", HttpStatus.BAD_REQUEST, "attachments")
        }
        val attachments = request.attachments.mapIndexed { index, attachment -> validateAttachment(index, attachment) }
        attachments.forEach {
            evidenceObjectStorage.putEvidenceObject(
                storageKey = it.storageKey,
                contentType = it.contentType,
                bytes = it.bytes,
            )
        }
        val submissionId = UUID.randomUUID()
        if (repository.insertEvidenceSubmission(submissionId, dispute.id, employee.merchantId, employee.id, narrative) == 0) {
            throw MerchantDashboardException("evidence_already_submitted", "Evidence has already been submitted for this dispute.", HttpStatus.CONFLICT)
        }
        attachments.forEach {
            repository.insertEvidenceAttachment(
                id = UUID.randomUUID(),
                evidenceSubmissionId = submissionId,
                fileName = it.fileName,
                contentType = it.contentType,
                storageKey = it.storageKey,
                sizeBytes = it.sizeBytes,
            )
        }
        if (repository.markEvidenceSubmitted(dispute.id) == 0) {
            throw MerchantDashboardException("invalid_state", "Evidence can only be submitted for merchant-notified disputes.", HttpStatus.CONFLICT)
        }
        auditRepository.write(
            eventType = "chargeback.evidence_submitted",
            actorType = "MERCHANT_EMPLOYEE",
            actorId = employee.id,
            subjectType = "CHARGEBACK",
            subjectId = dispute.id,
            outcome = "SUCCESS",
            metadataJson = """{"evidenceSubmissionId":"$submissionId","attachmentCount":${attachments.size}}""",
        )
        val submission = repository.findEvidenceSubmission(dispute.id) ?: error("Evidence submission disappeared after insert")
        val dto = submission.toDto(
            state = "EVIDENCE_SUBMITTED",
            attachments = repository.listEvidenceAttachments(submission.id),
        )
        outboundWebhookService.publishDisputeEvidenceReceived(
            disputeId = dispute.id,
            merchantId = dispute.merchantId,
            paymentIntentId = dispute.paymentIntentId,
            evidenceSubmissionId = submission.id,
            state = dto.state,
            createdAt = dto.createdAt,
        )
        return dto
    }

    fun listMerchantDisputes(employee: MerchantEmployeeRecord, limit: Int): ChargebackDisputeListResponse {
        requireActiveMerchant(employee)
        val normalizedLimit = limit.coerceIn(1, 100)
        return ChargebackDisputeListResponse(
            items = repository.listDisputesForMerchant(employee.merchantId, normalizedLimit).map { it.toDto() },
        )
    }

    fun getMerchantDispute(employee: MerchantEmployeeRecord, disputeId: UUID): MerchantDisputeDetailDto {
        requireActiveMerchant(employee)
        val dispute = repository.findDisputeByIdForMerchant(disputeId, employee.merchantId)
            ?: throw MerchantDashboardException("dispute_not_found", "Dispute was not found.", HttpStatus.NOT_FOUND)
        val evidenceSubmission = repository.findEvidenceSubmission(dispute.id)?.let { submission ->
            submission.toDto(
                state = dispute.state,
                attachments = repository.listEvidenceAttachments(submission.id),
            )
        }
        return MerchantDisputeDetailDto(dispute = dispute.toDto(), evidenceSubmission = evidenceSubmission)
    }

    fun listBackofficeDisputes(limit: Int): ChargebackDisputeListResponse {
        val normalizedLimit = limit.coerceIn(1, 100)
        return ChargebackDisputeListResponse(
            items = repository.listDisputes(normalizedLimit).map { it.toDto() },
        )
    }

    @Transactional
    fun getBackofficeDispute(disputeId: UUID, principal: BackofficePrincipal): BackofficeDisputeDetailDto {
        val dispute = repository.findDisputeById(disputeId)
            ?: throw BackofficeException("dispute_not_found", "Dispute was not found.", HttpStatus.NOT_FOUND)
        readAuditRepository.write(
            actorType = "BACKOFFICE",
            actorId = principal.subjectUuid,
            actorReference = principal.subject,
            subjectType = "CHARGEBACK_DISPUTE",
            subjectId = dispute.id,
            resourceType = "CHARGEBACK_DISPUTE",
            resourceId = dispute.id,
            purpose = "backoffice_chargeback_dispute_detail",
            decision = "ALLOW",
            metadataJson = """{"roles":${principal.roles.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }},"paymentIntentId":"${dispute.paymentIntentId}","merchantId":"${dispute.merchantId}"}""",
        )
        val evidenceSubmission = repository.findEvidenceSubmission(dispute.id)?.let { submission ->
            submission.toDto(
                state = dispute.state,
                attachments = repository.listEvidenceAttachments(submission.id),
            )
        }
        return BackofficeDisputeDetailDto(dispute = dispute.toDto(), evidenceSubmission = evidenceSubmission)
    }

    @Transactional
    fun acceptDispute(employee: MerchantEmployeeRecord, disputeId: UUID): MerchantAcceptChargebackDto {
        requireActiveMerchant(employee)
        if (employee.role != "merchant_admin") {
            throw MerchantDashboardException("forbidden_role", "merchant_admin role is required to accept chargebacks.", HttpStatus.FORBIDDEN)
        }
        val dispute = repository.findDisputeById(disputeId)
            ?: throw MerchantDashboardException("dispute_not_found", "Dispute was not found.", HttpStatus.NOT_FOUND)
        if (dispute.merchantId != employee.merchantId) {
            throw MerchantDashboardException("dispute_not_found", "Dispute was not found.", HttpStatus.NOT_FOUND)
        }
        if (dispute.state != "MERCHANT_NOTIFIED") {
            throw MerchantDashboardException("invalid_state", "Only merchant-notified disputes can be accepted.", HttpStatus.CONFLICT)
        }
        if (dispute.provisionalCreditJournalId == null) {
            throw MerchantDashboardException("provisional_credit_missing", "Dispute has no provisional credit to finalize.", HttpStatus.CONFLICT)
        }
        val merchantDebitJournalId = postMerchantChargebackDebit(
            dispute = dispute,
            missingAccount = { code, message -> MerchantDashboardException(code, message, HttpStatus.INTERNAL_SERVER_ERROR) },
        )
        if (repository.markMerchantAccepted(dispute.id, employee.id, merchantDebitJournalId) == 0) {
            throw MerchantDashboardException("invalid_state", "Only merchant-notified disputes can be accepted.", HttpStatus.CONFLICT)
        }
        auditRepository.write(
            eventType = "chargeback.merchant_accepted",
            actorType = "MERCHANT_EMPLOYEE",
            actorId = employee.id,
            subjectType = "CHARGEBACK",
            subjectId = dispute.id,
            outcome = "SUCCESS",
            metadataJson = """{"merchantDebitJournalId":"$merchantDebitJournalId","role":"${employee.role}"}""",
        )
        outboundWebhookService.publishDisputeLost(
            disputeId = dispute.id,
            merchantId = dispute.merchantId,
            paymentIntentId = dispute.paymentIntentId,
            arbitrationJournalId = merchantDebitJournalId,
        )
        return MerchantAcceptChargebackDto(
            disputeId = dispute.id.toString(),
            state = "MERCHANT_ACCEPTED",
            externalState = "LOST",
            merchantDebitJournalId = merchantDebitJournalId.toString(),
        )
    }

    @Transactional
    fun processMerchantDeadlines(limit: Int): DeadlineExpiryProcessDto {
        if (limit !in 1..500) {
            throw ChargebackException("invalid_limit", "Limit must be between 1 and 500.", HttpStatus.BAD_REQUEST, "limit")
        }
        val processed = repository.listDeadlineExpiredCandidates(limit).mapNotNull { dispute ->
            val merchantDebitJournalId = postMerchantChargebackDebit(
                dispute = dispute,
                missingAccount = { code, message -> ChargebackException(code, message, HttpStatus.INTERNAL_SERVER_ERROR) },
            )
            if (repository.markMerchantDeadlineExpired(dispute.id, merchantDebitJournalId) == 0) {
                null
            } else {
                auditRepository.write(
                    eventType = "chargeback.deadline_expired",
                    actorType = "SYSTEM",
                    actorId = null,
                    subjectType = "CHARGEBACK",
                    subjectId = dispute.id,
                    outcome = "SUCCESS",
                    metadataJson = """{"merchantDebitJournalId":"$merchantDebitJournalId"}""",
                )
                outboundWebhookService.publishDisputeLost(
                    disputeId = dispute.id,
                    merchantId = dispute.merchantId,
                    paymentIntentId = dispute.paymentIntentId,
                    arbitrationJournalId = merchantDebitJournalId,
                )
                DeadlineExpiryItemDto(
                    disputeId = dispute.id.toString(),
                    state = "MERCHANT_DEADLINE_EXPIRED",
                    externalState = "LOST",
                    merchantDebitJournalId = merchantDebitJournalId.toString(),
                )
            }
        }
        return DeadlineExpiryProcessDto(processedCount = processed.size, processed = processed)
    }

    @Transactional
    fun decideArbitration(disputeId: UUID, request: ArbitrationDecisionRequest, principal: BackofficePrincipal): ArbitrationDecisionDto {
        val outcome = request.outcome?.trim()?.uppercase()
            ?: throw BackofficeException("invalid_outcome", "Arbitration outcome is required.", HttpStatus.BAD_REQUEST)
        if (outcome !in setOf("WON", "LOST")) {
            throw BackofficeException("unsupported_outcome", "Only WON or LOST arbitration is supported.", HttpStatus.BAD_REQUEST)
        }
        val rationale = request.rationale?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw BackofficeException("invalid_rationale", "Arbitration rationale is required.", HttpStatus.BAD_REQUEST)
        if (rationale.length < 20 || rationale.length > 4000) {
            throw BackofficeException("invalid_rationale", "Arbitration rationale must be between 20 and 4000 characters.", HttpStatus.BAD_REQUEST)
        }
        val dispute = repository.findDisputeById(disputeId)
            ?: throw BackofficeException("dispute_not_found", "Dispute was not found.", HttpStatus.NOT_FOUND)
        if (dispute.state != "EVIDENCE_SUBMITTED") {
            throw BackofficeException("invalid_state", "Only evidence-submitted disputes can be decided as WON.", HttpStatus.CONFLICT)
        }
        if (dispute.provisionalCreditJournalId == null) {
            throw BackofficeException("provisional_credit_missing", "Dispute has no provisional credit to reverse.", HttpStatus.CONFLICT)
        }
        val walletLedgerAccountId = repository.cardholderWalletLedgerAccountId(dispute.id)
            ?: throw BackofficeException("wallet_missing", "Cardholder wallet is missing.", HttpStatus.INTERNAL_SERVER_ERROR)
        val role = principal.roles.firstOrNull()
        if (outcome == "WON") {
            val reversalJournalId = postProvisionalCreditReversal(dispute, walletLedgerAccountId)
            if (repository.markArbitrationWon(dispute.id, rationale, principal.subject, role, reversalJournalId) == 0) {
                throw BackofficeException("invalid_state", "Only evidence-submitted disputes can be decided as WON.", HttpStatus.CONFLICT)
            }
            auditRepository.write(
                eventType = "chargeback.arbitration_won",
                actorType = "BACKOFFICE",
                actorId = principal.subjectUuid,
                subjectType = "CHARGEBACK",
                subjectId = dispute.id,
                outcome = "SUCCESS",
                metadataJson = """{"arbitrationJournalId":"$reversalJournalId","role":"${role ?: ""}"}""",
            )
            outboundWebhookService.publishDisputeWon(
                disputeId = dispute.id,
                merchantId = dispute.merchantId,
                paymentIntentId = dispute.paymentIntentId,
                arbitrationJournalId = reversalJournalId,
            )
            return ArbitrationDecisionDto(dispute.id.toString(), "WON", "WON", reversalJournalId.toString())
        }
        val merchantDebitJournalId = postMerchantChargebackDebit(
            dispute = dispute,
            missingAccount = { code, message -> BackofficeException(code, message, HttpStatus.INTERNAL_SERVER_ERROR) },
        )
        if (repository.markArbitrationLost(dispute.id, rationale, principal.subject, role, merchantDebitJournalId) == 0) {
            throw BackofficeException("invalid_state", "Only evidence-submitted disputes can be decided as LOST.", HttpStatus.CONFLICT)
        }
        auditRepository.write(
            eventType = "chargeback.arbitration_lost",
            actorType = "BACKOFFICE",
            actorId = principal.subjectUuid,
            subjectType = "CHARGEBACK",
            subjectId = dispute.id,
            outcome = "SUCCESS",
            metadataJson = """{"arbitrationJournalId":"$merchantDebitJournalId","role":"${role ?: ""}"}""",
        )
        outboundWebhookService.publishDisputeLost(
            disputeId = dispute.id,
            merchantId = dispute.merchantId,
            paymentIntentId = dispute.paymentIntentId,
            arbitrationJournalId = merchantDebitJournalId,
        )
        return ArbitrationDecisionDto(dispute.id.toString(), "LOST", "LOST", merchantDebitJournalId.toString())
    }

    private fun isKycApproved(userId: UUID): Boolean =
        jdbcTemplate.query(
            "select status from kyc.kyc_profiles where end_user_id = ?",
            { rs, _ -> rs.getString("status") },
            userId,
        ).firstOrNull() == "APPROVED"

    private fun requireActiveMerchant(employee: MerchantEmployeeRecord) {
        if (employee.status != "ACTIVE") {
            throw MerchantDashboardException("merchant_employee_not_active", "Merchant employee is not active.", HttpStatus.FORBIDDEN)
        }
    }

    private fun validateAttachment(index: Int, attachment: EvidenceAttachmentRequest): ValidatedEvidenceAttachment {
        val fileName = attachment.fileName?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw MerchantDashboardException("invalid_attachment", "Attachment file name is required.", HttpStatus.BAD_REQUEST, "attachments[$index].fileName")
        val contentType = attachment.contentType?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            ?: throw MerchantDashboardException("invalid_attachment", "Attachment content type is required.", HttpStatus.BAD_REQUEST, "attachments[$index].contentType")
        val storageKey = attachment.storageKey?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw MerchantDashboardException("invalid_attachment", "Attachment storage key is required.", HttpStatus.BAD_REQUEST, "attachments[$index].storageKey")
        val sizeBytes = attachment.sizeBytes
            ?: throw MerchantDashboardException("invalid_attachment", "Attachment size is required.", HttpStatus.BAD_REQUEST, "attachments[$index].sizeBytes")
        if (fileName.length > 255 || contentType.length > 120 || storageKey.length > 500 || sizeBytes <= 0L) {
            throw MerchantDashboardException("invalid_attachment", "Attachment metadata is invalid.", HttpStatus.BAD_REQUEST, "attachments[$index]")
        }
        val contentBase64 = attachment.contentBase64?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw MerchantDashboardException("invalid_attachment", "Attachment content is required.", HttpStatus.BAD_REQUEST, "attachments[$index].contentBase64")
        val bytes = try {
            Base64.getDecoder().decode(contentBase64)
        } catch (_: IllegalArgumentException) {
            throw MerchantDashboardException("invalid_attachment", "Attachment content is not valid base64.", HttpStatus.BAD_REQUEST, "attachments[$index].contentBase64")
        }
        if (bytes.size.toLong() != sizeBytes) {
            throw MerchantDashboardException("invalid_attachment", "Attachment size does not match content.", HttpStatus.BAD_REQUEST, "attachments[$index].sizeBytes")
        }
        return ValidatedEvidenceAttachment(fileName, contentType, storageKey, sizeBytes, bytes)
    }

    private fun postProvisionalCredit(dispute: ChargebackDisputeRecord, walletLedgerAccountId: UUID): UUID {
        val reserveAccountId = repository.accountByCode("ACQUIRER_DISPUTE_RESERVE")
            ?: throw ChargebackException(
                code = "dispute_reserve_account_missing",
                message = "Acquirer dispute reserve ledger account is missing.",
                status = HttpStatus.INTERNAL_SERVER_ERROR,
            )
        val amountText = dispute.amount.setScale(4, RoundingMode.UNNECESSARY).toPlainString()
        return ledgerRepository.postJournal(
            journalId = UUID.randomUUID(),
            journalType = "CARDHOLDER_PROVISIONAL_CREDIT",
            referenceType = "CHARGEBACK",
            referenceId = dispute.id,
            currency = dispute.currency,
            description = "Cardholder provisional credit",
            postingsJson = """
                [
                  {"accountId":"$reserveAccountId","side":"DEBIT","amount":"$amountText"},
                  {"accountId":"$walletLedgerAccountId","side":"CREDIT","amount":"$amountText"}
                ]
            """.trimIndent(),
        )
    }

    private fun postProvisionalCreditReversal(dispute: ChargebackDisputeRecord, walletLedgerAccountId: UUID): UUID {
        val reserveAccountId = repository.accountByCode("ACQUIRER_DISPUTE_RESERVE")
            ?: throw BackofficeException(
                code = "dispute_reserve_account_missing",
                message = "Acquirer dispute reserve ledger account is missing.",
                status = HttpStatus.INTERNAL_SERVER_ERROR,
            )
        val amountText = dispute.amount.setScale(4, RoundingMode.UNNECESSARY).toPlainString()
        return ledgerRepository.postJournal(
            journalId = UUID.randomUUID(),
            journalType = "CARDHOLDER_PROVISIONAL_CREDIT_REVERSAL",
            referenceType = "CHARGEBACK",
            referenceId = dispute.id,
            currency = dispute.currency,
            description = "Cardholder provisional credit reversal",
            postingsJson = """
                [
                  {"accountId":"$walletLedgerAccountId","side":"DEBIT","amount":"$amountText"},
                  {"accountId":"$reserveAccountId","side":"CREDIT","amount":"$amountText"}
                ]
            """.trimIndent(),
        )
    }

    private fun postMerchantChargebackDebit(
        dispute: ChargebackDisputeRecord,
        missingAccount: (code: String, message: String) -> RuntimeException,
    ): UUID {
        val merchantSettlementAccountId = repository.accountByCode("MERCHANT_SETTLEMENT:${dispute.merchantId}")
            ?: throw missingAccount("merchant_settlement_account_missing", "Merchant settlement ledger account is missing.")
        val reserveAccountId = repository.accountByCode("ACQUIRER_DISPUTE_RESERVE")
            ?: throw missingAccount("dispute_reserve_account_missing", "Acquirer dispute reserve ledger account is missing.")
        val amountText = dispute.amount.setScale(4, RoundingMode.UNNECESSARY).toPlainString()
        return ledgerRepository.postJournal(
            journalId = UUID.randomUUID(),
            journalType = "CHARGEBACK_MERCHANT_DEBIT",
            referenceType = "CHARGEBACK",
            referenceId = dispute.id,
            currency = dispute.currency,
            description = "Chargeback merchant debit",
            postingsJson = """
                [
                  {"accountId":"$merchantSettlementAccountId","side":"DEBIT","amount":"$amountText"},
                  {"accountId":"$reserveAccountId","side":"CREDIT","amount":"$amountText"}
                ]
            """.trimIndent(),
        )
    }
}

private data class ValidatedEvidenceAttachment(
    val fileName: String,
    val contentType: String,
    val storageKey: String,
    val sizeBytes: Long,
    val bytes: ByteArray,
)

fun ChargebackDisputeRecord.toDto(): ChargebackDisputeDto =
    ChargebackDisputeDto(
        id = id.toString(),
        paymentIntentId = paymentIntentId.toString(),
        merchantId = merchantId.toString(),
        cardholderUserId = cardholderUserId.toString(),
        amount = amount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
        currency = currency,
        reasonCode = reasonCode,
        narrative = narrative,
        state = state,
        merchantResponseDeadline = merchantResponseDeadline.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        provisionalCreditJournalId = provisionalCreditJournalId?.toString(),
        createdAt = createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    )

fun EvidenceSubmissionRecord.toDto(state: String, attachments: List<EvidenceAttachmentRecord>): EvidenceSubmissionDto =
    EvidenceSubmissionDto(
        id = id.toString(),
        disputeId = disputeId.toString(),
        state = state,
        narrative = narrative,
        attachments = attachments.map { it.toDto() },
        createdAt = createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    )

private fun EvidenceAttachmentRecord.toDto(): EvidenceAttachmentDto =
    EvidenceAttachmentDto(
        id = id.toString(),
        fileName = fileName,
        contentType = contentType,
        storageKey = storageKey,
        sizeBytes = sizeBytes,
    )
