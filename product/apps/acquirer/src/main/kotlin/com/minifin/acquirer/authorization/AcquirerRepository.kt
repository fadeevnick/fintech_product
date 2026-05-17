package com.minifin.acquirer.authorization

import java.math.BigDecimal
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class AcquirerRepository(private val jdbc: JdbcTemplate) {
    fun insertPaymentIntent(id: UUID, merchantId: UUID, amount: BigDecimal, currency: String, cardToken: String, requestId: String?, correlationId: String?) {
        jdbc.update("""
            insert into acquirer.payment_intents (id, merchant_id, amount, currency, state, card_token, platform_payment_intent_id, request_id, correlation_id)
            values (?, ?, ?, ?, 'CREATED', ?, ?, ?, ?)
        """.trimIndent(), id, merchantId, amount, currency, cardToken, id, requestId, correlationId)
    }
    fun mark(id: UUID, state: String, authorizationId: UUID?, authCode: String?, declineCode: String?, declineMessage: String?) {
        jdbc.update("""
            update acquirer.payment_intents set state = ?, authorization_id = ?, auth_code = ?, decline_code = ?, decline_message = ?, updated_at = now() where id = ?
        """.trimIndent(), state, authorizationId, authCode, declineCode, declineMessage, id)
    }
    fun insertAttempt(id: UUID, pi: UUID, merchantId: UUID, amount: BigDecimal, currency: String, cardToken: String, status: String, authId: UUID?, authCode: String?, declineCode: String?, declineMessage: String?, routeId: UUID?, requestId: String?, correlationId: String?) {
        jdbc.update("""
            insert into acquirer.authorization_attempts (id, payment_intent_id, merchant_id, amount, currency, card_token, status, authorization_id, auth_code, decline_code, decline_message, network_route_id, request_id, correlation_id)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(), id, pi, merchantId, amount, currency, cardToken, status, authId, authCode, declineCode, declineMessage, routeId, requestId, correlationId)
    }
}
