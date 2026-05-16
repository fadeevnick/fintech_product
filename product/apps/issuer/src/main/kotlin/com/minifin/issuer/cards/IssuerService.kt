package com.minifin.issuer.cards

import java.util.UUID
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

class IssuerException(val code: String, override val message: String, val status: HttpStatus) : RuntimeException(message)

@Service
class IssuerService(private val repository: IssuerRepository, private val properties: IssuerProperties) {
    private val restTemplate = RestTemplate()

    fun issueCard(request: IssueCardRequest): CardResponse {
        val endUserId = requireUuid(request.endUserId, "endUserId")
        val walletAccountId = requireUuid(request.walletAccountId, "walletAccountId")
        val vaultResponse = tokenize(request.requestId, request.correlationId)
        return repository.insertCard(
            id = UUID.randomUUID(),
            endUserId = endUserId,
            walletAccountId = walletAccountId,
            token = vaultResponse.cardToken,
            last4 = vaultResponse.last4,
            bin = vaultResponse.bin,
            expMonth = vaultResponse.expirationMonth,
            expYear = vaultResponse.expirationYear,
            requestId = request.requestId,
            correlationId = request.correlationId,
        )
    }

    private fun tokenize(requestId: String?, correlationId: String?): VaultTokenizeResponse {
        val headers = HttpHeaders()
        headers.set("X-Service-Name", "issuer")
        headers.set("X-Service-Secret", properties.serviceAuthSecret)
        val entity = HttpEntity(VaultTokenizeRequest(requestId, correlationId), headers)
        val response = runCatching {
            restTemplate.exchange("${properties.vaultBaseUrl}/internal/vault/tokenize", HttpMethod.POST, entity, VaultApiResponse::class.java)
        }.getOrElse { throw IssuerException("vault_unavailable", "Vault tokenization failed.", HttpStatus.BAD_GATEWAY) }
        return response.body?.data ?: throw IssuerException("vault_unavailable", "Vault tokenization failed.", HttpStatus.BAD_GATEWAY)
    }

    private fun requireUuid(value: String, field: String): UUID = runCatching { UUID.fromString(value) }.getOrElse {
        throw IssuerException("invalid_uuid", "Invalid UUID.", HttpStatus.BAD_REQUEST)
    }
}

data class VaultApiResponse(val data: VaultTokenizeResponse? = null, val errors: List<ApiError> = emptyList())
