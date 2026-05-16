package com.minifin.issuer.cards

import java.sql.ResultSet
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class IssuerRepository(private val jdbcTemplate: JdbcTemplate) {
    fun insertCard(id: UUID, endUserId: UUID, walletAccountId: UUID, token: String, last4: String, bin: String, expMonth: Int, expYear: Int, requestId: String?, correlationId: String?): CardResponse {
        jdbcTemplate.update(
            """
            insert into issuer.cards (id, end_user_id, wallet_account_id, card_token, last4, bin, expiration_month, expiration_year, state, request_id, correlation_id)
            values (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
            """.trimIndent(),
            id, endUserId, walletAccountId, token, last4, bin, expMonth, expYear, requestId, correlationId,
        )
        return findCard(id) ?: error("Card disappeared after insert")
    }

    fun findCard(id: UUID): CardResponse? = jdbcTemplate.query(
        """
        select id, state, card_token, last4, expiration_month, expiration_year, bin
        from issuer.cards where id = ?
        """.trimIndent(),
        { rs: ResultSet, _: Int -> rs.toCardResponse() },
        id,
    ).firstOrNull()

    private fun ResultSet.toCardResponse(): CardResponse = CardResponse(
        cardId = getObject("id", UUID::class.java).toString(),
        state = getString("state"),
        cardToken = getString("card_token"),
        last4 = getString("last4"),
        expirationMonth = getInt("expiration_month"),
        expirationYear = getInt("expiration_year"),
        bin = getString("bin"),
    )
}
