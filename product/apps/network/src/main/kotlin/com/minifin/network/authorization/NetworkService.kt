package com.minifin.network.authorization

import java.util.UUID
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

class NetworkException(val code: String, override val message: String, val status: HttpStatus) : RuntimeException(message)

@Service
class NetworkService(private val repo: NetworkRepository, private val props: NetworkProperties) {
    private val rest = RestTemplate()
    fun authorize(req: NetworkAuthorizeRequest): NetworkAuthorizeResponse {
        val routeId = UUID.randomUUID(); val bin = "400000"
        val issuer = repo.findIssuer(bin)
        if (issuer == null) { repo.insertRoute(routeId, req, bin, null, "ERROR"); return NetworkAuthorizeResponse(routeId.toString(), "AUTH_DECLINED", declineCode="issuer_unavailable", declineMessage="No active issuer route for BIN.") }
        repo.insertRoute(routeId, req, bin, issuer, "ROUTED"); repo.audit(routeId, "authorization.routed", """{"issuer":"$issuer"}""")
        val issuerResp = callIssuer(req, routeId)
        val approved = issuerResp.status == "AUTH_APPROVED"
        repo.updateRoute(routeId, if (approved) "APPROVED" else "DECLINED", issuerResp.authorizationId, issuerResp.declineCode, issuerResp.declineMessage)
        return NetworkAuthorizeResponse(routeId.toString(), issuerResp.status ?: "AUTH_DECLINED", issuerResp.authorizationId, issuerResp.authCode, issuerResp.expiresAt, issuerResp.declineCode, issuerResp.declineMessage)
    }
    private fun callIssuer(req: NetworkAuthorizeRequest, routeId: UUID): IssuerAuthorizeResponse {
        val headers = HttpHeaders(); headers.set("X-Service-Name", "network"); headers.set("X-Service-Secret", props.serviceAuthSecret)
        val body = mapOf("paymentIntentId" to req.paymentIntentId, "merchantId" to req.merchantId, "amount" to req.amount, "currency" to req.currency, "cardToken" to req.cardToken, "networkRouteId" to routeId.toString(), "requestId" to req.requestId, "correlationId" to req.correlationId)
        val response = runCatching { rest.exchange("${props.issuerBaseUrl}/internal/issuer/authorize", HttpMethod.POST, HttpEntity(body, headers), IssuerApiResponse::class.java) }
            .getOrElse { return IssuerAuthorizeResponse(status="AUTH_DECLINED", declineCode="issuer_unavailable", declineMessage="Issuer authorization service is unavailable.") }
        return response.body?.data ?: IssuerAuthorizeResponse(status="AUTH_DECLINED", declineCode="issuer_unavailable", declineMessage="Issuer authorization service returned no decision.")
    }
}
