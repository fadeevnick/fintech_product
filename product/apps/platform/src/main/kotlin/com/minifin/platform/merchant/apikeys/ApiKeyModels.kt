package com.minifin.platform.merchant.apikeys

import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.http.HttpStatus

data class CreateApiKeyRequest(
    val label: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateApiKeyResponse(
    val apiKeyId: String,
    val label: String,
    val key: String,
    val keyPrefix: String,
    val fingerprint: String,
    val status: String,
    val createdAt: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiKeySummary(
    val apiKeyId: String,
    val label: String,
    val keyPrefix: String,
    val fingerprint: String,
    val status: String,
    val createdAt: String,
    val revokedAt: String?,
    val lastUsedAt: String?,
)

data class RevokeApiKeyResponse(
    val apiKeyId: String,
    val status: String,
    val revokedAt: String,
)

class ApiKeyException(
    val code: String,
    override val message: String,
    val status: HttpStatus,
    val field: String? = null,
) : RuntimeException(message)
