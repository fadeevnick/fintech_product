package com.minifin.acquirer.authorization

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

class AcquirerException(val code: String, override val message: String, val status: HttpStatus) : RuntimeException(message)

@Service
class AcquirerService(private val repo: AcquirerRepository, private val props: AcquirerProperties) {
    private val rest = RestTemplate()
    fun authorize(req: AcquirerAuthorizeRequest): AcquirerAuthorizeResponse {
        val pi = uuid(req.paymentIntentId); val merchant = uuid(req.merchantId); val amount = BigDecimal(req.amount).setScale(4, RoundingMode.UNNECESSARY)
        repo.insertPaymentIntent(pi, merchant, amount, req.currency, req.cardToken, req.requestId, req.correlationId)
        val network = callNetwork(req)
        val approved = network.status == "AUTH_APPROVED"
        val authId = network.authorizationId?.let { uuid(it) }
        val routeId = network.routeId?.let { uuid(it) }
        repo.mark(pi, if (approved) "AUTHORIZED" else "FAILED", authId, network.authCode, network.declineCode, network.declineMessage)
        repo.insertAttempt(UUID.randomUUID(), pi, merchant, amount, req.currency, req.cardToken, if (approved) "APPROVED" else "DECLINED", authId, network.authCode, network.declineCode, network.declineMessage, routeId, req.requestId, req.correlationId)
        return AcquirerAuthorizeResponse(pi.toString(), if (approved) "AUTHORIZED" else "FAILED", network.status ?: "AUTH_DECLINED", network.authorizationId, network.authCode, network.expiresAt, network.declineCode, network.declineMessage, network.routeId)
    }
    private fun callNetwork(req: AcquirerAuthorizeRequest): NetworkAuthorizeResponse {
        val headers = HttpHeaders(); headers.set("X-Service-Name", "acquirer"); headers.set("X-Service-Secret", props.serviceAuthSecret)
        val response = runCatching { rest.exchange("${props.networkBaseUrl}/internal/network/authorize", HttpMethod.POST, HttpEntity(req, headers), NetworkApiResponse::class.java) }
            .getOrElse { return NetworkAuthorizeResponse(status="AUTH_DECLINED", declineCode="network_unavailable", declineMessage="Network authorization service is unavailable.") }
        return response.body?.data ?: NetworkAuthorizeResponse(status="AUTH_DECLINED", declineCode="network_unavailable", declineMessage="Network authorization service returned no decision.")
    }
    private fun uuid(v: String) = UUID.fromString(v)
}
