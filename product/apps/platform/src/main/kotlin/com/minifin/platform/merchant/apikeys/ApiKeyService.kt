package com.minifin.platform.merchant.apikeys

import com.minifin.platform.identity.AuditRepository
import com.minifin.platform.identity.IdentityClock
import com.minifin.platform.identity.MerchantEmployeeRecord
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private const val KEY_PREFIX_STATIC = "mfp_live_"
private const val KEY_PREFIX_LENGTH = KEY_PREFIX_STATIC.length + 3
private const val FINGERPRINT_LENGTH = 16

@Service
class ApiKeyService(
    private val repository: ApiKeyRepository,
    private val auditRepository: AuditRepository,
    private val clock: IdentityClock,
) {
    private val secureRandom = SecureRandom()

    @Transactional
    fun create(employee: MerchantEmployeeRecord, request: CreateApiKeyRequest): CreateApiKeyResponse {
        requireMerchantAdmin(employee)
        val label = (request.label ?: "default").trim().ifBlank { "default" }
        if (label.length > 120) {
            throw ApiKeyException(
                code = "invalid_label",
                message = "Label must be at most 120 characters.",
                status = HttpStatus.BAD_REQUEST,
                field = "label",
            )
        }
        val rawKey = generateRawKey()
        val keyHash = sha256Hex(rawKey)
        val fingerprint = keyHash.substring(0, FINGERPRINT_LENGTH)
        val keyPrefix = rawKey.substring(0, KEY_PREFIX_LENGTH)
        val keyId = UUID.randomUUID()

        repository.insert(
            id = keyId,
            merchantId = employee.merchantId,
            label = label,
            keyPrefix = keyPrefix,
            keyHash = keyHash,
            fingerprint = fingerprint,
            createdByEmployeeId = employee.id,
        )

        val record = repository.findById(keyId)
            ?: error("API key disappeared after insert")

        auditRepository.write(
            eventType = "merchant.api_key_created",
            actorType = "MERCHANT_EMPLOYEE",
            actorId = employee.id,
            subjectType = "MERCHANT_API_KEY",
            subjectId = keyId,
            outcome = "SUCCESS",
            metadataJson = """{"merchantId":"${employee.merchantId}","fingerprint":"$fingerprint","keyPrefix":"$keyPrefix"}""",
        )

        return CreateApiKeyResponse(
            apiKeyId = record.id.toString(),
            label = record.label,
            key = rawKey,
            keyPrefix = record.keyPrefix,
            fingerprint = record.fingerprint,
            status = record.status,
            createdAt = record.createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        )
    }

    @Transactional(readOnly = true)
    fun list(employee: MerchantEmployeeRecord): List<ApiKeySummary> =
        repository.listByMerchant(employee.merchantId, limit = 100)
            .map { it.toSummary() }

    @Transactional
    fun revoke(employee: MerchantEmployeeRecord, keyId: UUID): RevokeApiKeyResponse {
        requireMerchantAdmin(employee)
        val existing = repository.findById(keyId)
            ?: throw ApiKeyException(
                code = "api_key_not_found",
                message = "API key was not found.",
                status = HttpStatus.NOT_FOUND,
            )
        if (existing.merchantId != employee.merchantId) {
            throw ApiKeyException(
                code = "api_key_not_found",
                message = "API key was not found.",
                status = HttpStatus.NOT_FOUND,
            )
        }
        val now = clock.now()
        if (existing.status == "ACTIVE") {
            val updated = repository.revoke(keyId, employee.merchantId, employee.id, now)
            if (updated == 0) {
                throw ApiKeyException(
                    code = "api_key_state_conflict",
                    message = "API key state changed concurrently.",
                    status = HttpStatus.CONFLICT,
                )
            }
            auditRepository.write(
                eventType = "merchant.api_key_revoked",
                actorType = "MERCHANT_EMPLOYEE",
                actorId = employee.id,
                subjectType = "MERCHANT_API_KEY",
                subjectId = keyId,
                outcome = "SUCCESS",
                metadataJson = """{"merchantId":"${employee.merchantId}"}""",
            )
        }
        val refreshed = repository.findById(keyId)!!
        return RevokeApiKeyResponse(
            apiKeyId = refreshed.id.toString(),
            status = refreshed.status,
            revokedAt = refreshed.revokedAt?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                ?: now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        )
    }

    private fun requireMerchantAdmin(employee: MerchantEmployeeRecord) {
        if (employee.role != "merchant_admin") {
            throw ApiKeyException(
                code = "forbidden_role",
                message = "merchant_admin role is required to manage API keys.",
                status = HttpStatus.FORBIDDEN,
            )
        }
        if (employee.status != "ACTIVE") {
            throw ApiKeyException(
                code = "merchant_employee_not_active",
                message = "Merchant employee is not active.",
                status = HttpStatus.FORBIDDEN,
            )
        }
    }

    private fun generateRawKey(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return KEY_PREFIX_STATIC + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

internal fun sha256Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

internal fun ApiKeyRecord.toSummary(): ApiKeySummary =
    ApiKeySummary(
        apiKeyId = id.toString(),
        label = label,
        keyPrefix = keyPrefix,
        fingerprint = fingerprint,
        status = status,
        createdAt = createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        revokedAt = revokedAt?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        lastUsedAt = lastUsedAt?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    )
