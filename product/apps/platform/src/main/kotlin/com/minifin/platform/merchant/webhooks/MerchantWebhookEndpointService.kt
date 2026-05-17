package com.minifin.platform.merchant.webhooks

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.minifin.platform.identity.AuditRepository
import com.minifin.platform.identity.MerchantEmployeeRecord
import com.minifin.platform.merchant.dashboard.MerchantDashboardException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val ALLOWED_EVENTS = setOf(
    "payment_intent.created",
    "payment_intent.authorized",
    "payment_intent.captured",
    "payment_intent.refunded",
    "chargeback.created",
)

@Service
class MerchantWebhookEndpointService(
    private val repository: MerchantWebhookEndpointRepository,
    private val objectMapper: ObjectMapper,
    private val auditRepository: AuditRepository,
) {
    private val secureRandom = SecureRandom()

    @Transactional(readOnly = true)
    fun list(employee: MerchantEmployeeRecord): List<WebhookEndpointDto> {
        requireActive(employee)
        return repository.list(employee.merchantId).map { it.toDto() }
    }

    @Transactional
    fun create(employee: MerchantEmployeeRecord, request: WebhookEndpointRequest): WebhookEndpointDto {
        requireAdmin(employee)
        val id = UUID.randomUUID()
        val validated = request.validated()
        val signingSecret = generateSigningSecret()
        try {
            repository.insert(
                id,
                employee.merchantId,
                validated.url,
                validated.enabledEventsJson,
                validated.status,
                validated.description,
                employee.id,
                sha256(signingSecret),
                signingSecret,
                signingSecret.take(16),
            )
        } catch (e: DuplicateKeyException) {
            throw MerchantDashboardException("webhook_endpoint_conflict", "Webhook endpoint URL already exists for this merchant.", HttpStatus.CONFLICT, "url")
        }
        auditRepository.write("merchant.webhook_endpoint_created", "MERCHANT_EMPLOYEE", employee.id, "MERCHANT_WEBHOOK_ENDPOINT", id, "SUCCESS", "{\"merchantId\":\"${employee.merchantId}\"}")
        return repository.findByIdForMerchant(id, employee.merchantId)!!.toDto(signingSecret)
    }

    @Transactional
    fun update(employee: MerchantEmployeeRecord, id: UUID, request: WebhookEndpointRequest): WebhookEndpointDto {
        requireAdmin(employee)
        repository.findByIdForMerchant(id, employee.merchantId)
            ?: throw MerchantDashboardException("webhook_endpoint_not_found", "Webhook endpoint was not found.", HttpStatus.NOT_FOUND)
        val validated = request.validated()
        try {
            repository.update(id, employee.merchantId, validated.url, validated.enabledEventsJson, validated.status, validated.description)
        } catch (e: DuplicateKeyException) {
            throw MerchantDashboardException("webhook_endpoint_conflict", "Webhook endpoint URL already exists for this merchant.", HttpStatus.CONFLICT, "url")
        }
        auditRepository.write("merchant.webhook_endpoint_updated", "MERCHANT_EMPLOYEE", employee.id, "MERCHANT_WEBHOOK_ENDPOINT", id, "SUCCESS", "{\"merchantId\":\"${employee.merchantId}\"}")
        return repository.findByIdForMerchant(id, employee.merchantId)!!.toDto()
    }

    @Transactional
    fun delete(employee: MerchantEmployeeRecord, id: UUID): WebhookEndpointDto {
        requireAdmin(employee)
        val existing = repository.findByIdForMerchant(id, employee.merchantId)
            ?: throw MerchantDashboardException("webhook_endpoint_not_found", "Webhook endpoint was not found.", HttpStatus.NOT_FOUND)
        repository.delete(id, employee.merchantId)
        auditRepository.write("merchant.webhook_endpoint_deleted", "MERCHANT_EMPLOYEE", employee.id, "MERCHANT_WEBHOOK_ENDPOINT", id, "SUCCESS", "{\"merchantId\":\"${employee.merchantId}\"}")
        return existing.copy(status = "DELETED").toDto()
    }

    @Transactional
    fun rotateSecret(employee: MerchantEmployeeRecord, id: UUID): WebhookEndpointDto {
        requireAdmin(employee)
        repository.findByIdForMerchant(id, employee.merchantId)
            ?: throw MerchantDashboardException("webhook_endpoint_not_found", "Webhook endpoint was not found.", HttpStatus.NOT_FOUND)
        val signingSecret = generateSigningSecret()
        repository.rotateSecret(id, employee.merchantId, sha256(signingSecret), signingSecret, signingSecret.take(16))
        auditRepository.write("merchant.webhook_endpoint_secret_rotated", "MERCHANT_EMPLOYEE", employee.id, "MERCHANT_WEBHOOK_ENDPOINT", id, "SUCCESS", "{\"merchantId\":\"${employee.merchantId}\"}")
        return repository.findByIdForMerchant(id, employee.merchantId)!!.toDto(signingSecret)
    }

    private fun requireActive(employee: MerchantEmployeeRecord) {
        if (employee.status != "ACTIVE") throw MerchantDashboardException("merchant_employee_not_active", "Merchant employee is not active.", HttpStatus.FORBIDDEN)
    }

    private fun requireAdmin(employee: MerchantEmployeeRecord) {
        requireActive(employee)
        if (employee.role != "merchant_admin") throw MerchantDashboardException("forbidden_role", "merchant_admin role is required to manage webhook endpoints.", HttpStatus.FORBIDDEN)
    }

    private data class ValidatedWebhookEndpoint(val url: String, val enabledEventsJson: String, val status: String, val description: String?)

    private fun WebhookEndpointRequest.validated(): ValidatedWebhookEndpoint {
        val url = this.url?.trim()?.takeIf { it.isNotBlank() }
            ?: throw MerchantDashboardException("invalid_webhook_url", "Webhook URL is required.", HttpStatus.BAD_REQUEST, "url")
        if (!(url.startsWith("https://") || url.startsWith("http://127.0.0.1") || url.startsWith("http://localhost") || url.startsWith("http://host.docker.internal"))) {
            throw MerchantDashboardException("invalid_webhook_url", "Webhook URL must be HTTPS, localhost, or 127.0.0.1.", HttpStatus.BAD_REQUEST, "url")
        }
        val events = enabledEvents?.map { it.trim() }?.filter { it.isNotBlank() }?.distinct().orEmpty()
        if (events.isEmpty()) throw MerchantDashboardException("invalid_enabled_events", "At least one event is required.", HttpStatus.BAD_REQUEST, "enabledEvents")
        val invalid = events.firstOrNull { it !in ALLOWED_EVENTS }
        if (invalid != null) throw MerchantDashboardException("invalid_enabled_events", "Enabled event is not supported.", HttpStatus.BAD_REQUEST, "enabledEvents")
        val normalizedStatus = (status ?: "ACTIVE").trim().uppercase()
        if (normalizedStatus !in setOf("ACTIVE", "DISABLED")) throw MerchantDashboardException("invalid_webhook_status", "Webhook status is invalid.", HttpStatus.BAD_REQUEST, "status")
        val description = description?.trim()?.takeIf { it.isNotBlank() }
        if ((description?.length ?: 0) > 500) throw MerchantDashboardException("invalid_description", "Description must be at most 500 characters.", HttpStatus.BAD_REQUEST, "description")
        return ValidatedWebhookEndpoint(url, objectMapper.writeValueAsString(events), normalizedStatus, description)
    }

    private fun generateSigningSecret(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return "mfp_whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun WebhookEndpointRecord.toDto(signingSecret: String? = null): WebhookEndpointDto = WebhookEndpointDto(
        id = id.toString(),
        url = url,
        enabledEvents = objectMapper.readValue(enabledEventsJson, object : TypeReference<List<String>>() {}),
        status = status,
        description = description,
        secretPrefix = secretPrefix,
        signingSecret = signingSecret,
        createdAt = createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        updatedAt = updatedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        deletedAt = deletedAt?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    )
}
