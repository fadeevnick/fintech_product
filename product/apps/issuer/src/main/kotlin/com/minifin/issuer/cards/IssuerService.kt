package com.minifin.issuer.cards

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime
import java.time.YearMonth
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


    fun authorize(request: AuthorizeCardRequest): AuthorizeCardResponse {
        val amount = BigDecimal(request.amount).setScale(4, RoundingMode.UNNECESSARY)
        val authId = UUID.randomUUID()
        val card = repository.findCardRecordByToken(request.cardToken)
        fun decline(code: String, msg: String): AuthorizeCardResponse {
            repository.insertAuthorization(authId, card, request.cardToken, amount, request.currency, "DECLINED", null, code, msg, null, request.requestId, request.correlationId)
            return AuthorizeCardResponse("AUTH_DECLINED", authId.toString(), declineCode = code, declineMessage = msg)
        }
        if (card == null) return decline("card_not_found", "Card token was not found.")
        if (card.state != "ACTIVE") return decline("card_inactive", "Card is not active.")
        val nowYm = YearMonth.now(); if (YearMonth.of(card.expirationYear, card.expirationMonth).isBefore(nowYm)) return decline("card_expired", "Card is expired.")
        val control = getActorControl(card.endUserId)
        if (control == "BLOCKED") return decline("actor_blocked", "Cardholder actor is blocked.")
        if (control == "FROZEN") return decline("actor_frozen", "Cardholder actor is frozen.")
        val balance = getBalance(card.walletAccountId) ?: return decline("ledger_unavailable", "Platform balance service is unavailable.")
        if (BigDecimal(balance.availableBalance).setScale(4) < amount) return decline("insufficient_funds", "Insufficient available wallet balance.")
        val expiresAt = OffsetDateTime.now().plusDays(7)
        val authCode = UUID.randomUUID().toString().replace("-", "").take(6).uppercase()
        val hold = postHold(authId, card.walletAccountId, amount, request.currency, request.requestId, request.correlationId) ?: return decline("ledger_unavailable", "Platform ledger hold failed.")
        repository.insertAuthorization(authId, card, request.cardToken, amount, request.currency, "HELD", authCode, null, null, expiresAt, request.requestId, request.correlationId)
        repository.insertHold(UUID.randomUUID(), authId, request.cardToken, card.walletAccountId, UUID.fromString(hold.journalEntryId), amount, request.currency, expiresAt)
        return AuthorizeCardResponse("AUTH_APPROVED", authId.toString(), authCode, expiresAt.toString(), ledgerHoldJournalEntryId = hold.journalEntryId)
    }

    private fun getActorControl(endUserId: UUID): String? {
        val headers = HttpHeaders(); headers.set("X-Service-Name", "issuer"); headers.set("X-Service-Secret", properties.serviceAuthSecret)
        return runCatching { restTemplate.exchange("${properties.platformBaseUrl}/internal/identity/actor-controls/end-users/$endUserId", HttpMethod.GET, HttpEntity<Void>(headers), PlatformActorControlApiResponse::class.java).body?.data?.state }
            .getOrNull()
    }

    private fun getBalance(walletAccountId: UUID): PlatformBalanceResponse? {
        val headers = HttpHeaders(); headers.set("X-Service-Name", "issuer"); headers.set("X-Service-Secret", properties.serviceAuthSecret)
        return runCatching { restTemplate.exchange("${properties.platformBaseUrl}/internal/wallet/accounts/$walletAccountId/available-balance", HttpMethod.GET, HttpEntity<Void>(headers), PlatformBalanceApiResponse::class.java).body?.data }.getOrNull()
    }

    private fun postHold(authId: UUID, walletAccountId: UUID, amount: BigDecimal, currency: String, requestId: String?, correlationId: String?): PlatformHoldResponse? {
        val headers = HttpHeaders(); headers.set("X-Service-Name", "issuer"); headers.set("X-Service-Secret", properties.serviceAuthSecret)
        val body = PlatformHoldRequest(authId.toString(), walletAccountId.toString(), amount.toPlainString(), currency, requestId, correlationId)
        return runCatching { restTemplate.exchange("${properties.platformBaseUrl}/internal/ledger/holds", HttpMethod.POST, HttpEntity(body, headers), PlatformHoldApiResponse::class.java).body?.data }.getOrNull()
    }

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

data class PlatformBalanceApiResponse(val data: PlatformBalanceResponse? = null, val errors: List<ApiError> = emptyList())
data class PlatformHoldApiResponse(val data: PlatformHoldResponse? = null, val errors: List<ApiError> = emptyList())
data class PlatformActorControlApiResponse(val data: PlatformActorControlResponse? = null, val errors: List<ApiError> = emptyList())
