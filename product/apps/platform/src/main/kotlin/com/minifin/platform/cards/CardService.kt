package com.minifin.platform.cards

import com.minifin.platform.controls.ActorControlException
import com.minifin.platform.controls.ActorControlService
import com.minifin.platform.identity.AuditRepository
import com.minifin.platform.identity.EndUserRecord
import com.minifin.platform.wallet.WalletService
import java.util.UUID
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate

class CardException(val code: String, override val message: String, val status: HttpStatus) : RuntimeException(message)

@Service
class CardService(
    private val walletService: WalletService,
    private val actorControlService: ActorControlService,
    private val auditRepository: AuditRepository,
    private val cardRepository: CardRepository,
    private val properties: CardProperties,
) {
    private val restTemplate = RestTemplate()

    @Transactional(noRollbackFor = [ActorControlException::class])
    fun issueCard(user: EndUserRecord): CardIssueResponse {
        actorControlService.requireWriteAllowed("END_USER", user.id)
        val wallet = walletService.provisionWallet(user)
        val requestId = UUID.randomUUID().toString()
        val issuerCard = callIssuer(user.id, wallet.id, requestId)
        cardRepository.insertIssuedCard(
            id = UUID.fromString(issuerCard.cardId),
            endUserId = user.id,
            walletAccountId = wallet.id,
            cardToken = issuerCard.cardToken,
            state = issuerCard.state,
            last4 = issuerCard.last4,
            bin = issuerCard.bin,
            expirationMonth = issuerCard.expirationMonth,
            expirationYear = issuerCard.expirationYear,
        )
        auditRepository.write(
            eventType = "card.issued",
            actorType = "END_USER",
            actorId = user.id,
            subjectType = "CARD",
            subjectId = UUID.fromString(issuerCard.cardId),
            outcome = "SUCCESS",
            metadataJson = """{"last4":"${issuerCard.last4}","bin":"${issuerCard.bin}"}""",
        )
        return CardIssueResponse(
            card = CardMetadataResponse(
                id = issuerCard.cardId,
                state = issuerCard.state,
                last4 = issuerCard.last4,
                expirationMonth = issuerCard.expirationMonth,
                expirationYear = issuerCard.expirationYear,
                bin = issuerCard.bin,
            ),
        )
    }

    private fun callIssuer(userId: UUID, walletAccountId: UUID, requestId: String): IssuerCardResponse {
        val headers = HttpHeaders()
        headers.set("X-Service-Name", "platform")
        headers.set("X-Service-Secret", properties.serviceAuthSecret)
        val entity = HttpEntity(IssuerIssueCardRequest(userId.toString(), walletAccountId.toString(), requestId, requestId), headers)
        val response = runCatching {
            restTemplate.exchange("${properties.issuerBaseUrl}/internal/issuer/cards", HttpMethod.POST, entity, IssuerApiResponse::class.java)
        }.getOrElse { throw CardException("issuer_unavailable", "Issuer card issuance failed.", HttpStatus.BAD_GATEWAY) }
        return response.body?.data ?: throw CardException("issuer_unavailable", "Issuer card issuance failed.", HttpStatus.BAD_GATEWAY)
    }
}
