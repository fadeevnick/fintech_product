package com.minifin.network.authorization

import java.math.BigDecimal
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class NetworkRepository(private val jdbc: JdbcTemplate) {
    fun findIssuer(bin: String): String? = jdbc.query("select issuer_service from network.bin_registry where bin = ? and status = 'ACTIVE'", { rs, _ -> rs.getString(1) }, bin).firstOrNull()
    fun insertRoute(id: UUID, req: NetworkAuthorizeRequest, bin: String?, issuer: String?, status: String) = jdbc.update("""
        insert into network.authorization_routes (id, payment_intent_id, merchant_id, amount, currency, card_token, routed_bin, issuer_service, status, request_id, correlation_id)
        values (?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent(), id, req.paymentIntentId, req.merchantId, BigDecimal(req.amount), req.currency, req.cardToken, bin, issuer, status, req.requestId, req.correlationId)
    fun updateRoute(id: UUID, status: String, authId: String?, declineCode: String?, declineMessage: String?) = jdbc.update("update network.authorization_routes set status = ?, authorization_id = ?::uuid, decline_code = ?, decline_message = ?, updated_at = now() where id = ?", status, authId, declineCode, declineMessage, id)
    fun audit(routeId: UUID, event: String, details: String) = jdbc.update("insert into network.network_audit_log (id, route_id, event_type, details) values (?, ?, ?, ?::jsonb)", UUID.randomUUID(), routeId, event, details)
}
