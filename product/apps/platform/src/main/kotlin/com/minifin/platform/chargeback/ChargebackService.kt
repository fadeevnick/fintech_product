package com.minifin.platform.chargeback

import com.minifin.platform.identity.AuditRepository
import com.minifin.platform.identity.EndUserRecord
import com.minifin.platform.ledger.LedgerRepository
import com.minifin.platform.merchant.webhooks.OutboundWebhookService
import java.math.RoundingMode
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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

    private fun isKycApproved(userId: UUID): Boolean =
        jdbcTemplate.query(
            "select status from kyc.kyc_profiles where end_user_id = ?",
            { rs, _ -> rs.getString("status") },
            userId,
        ).firstOrNull() == "APPROVED"

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
}

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
