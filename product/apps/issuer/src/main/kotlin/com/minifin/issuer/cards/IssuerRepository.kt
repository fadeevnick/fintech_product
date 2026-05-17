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

    fun findCardRecordByToken(token: String): IssuerCardRecord? = jdbcTemplate.query(
        """
        select id, end_user_id, wallet_account_id, card_token, last4, expiration_month, expiration_year, bin, state
        from issuer.cards where card_token = ?
        """.trimIndent(),
        { rs, _ -> IssuerCardRecord(
            id = rs.getObject("id", UUID::class.java),
            endUserId = rs.getObject("end_user_id", UUID::class.java),
            walletAccountId = rs.getObject("wallet_account_id", UUID::class.java),
            cardToken = rs.getString("card_token"),
            expirationMonth = rs.getInt("expiration_month"),
            expirationYear = rs.getInt("expiration_year"),
            state = rs.getString("state"),
        ) },
        token,
    ).firstOrNull()

    fun insertAuthorization(id: UUID, card: IssuerCardRecord?, token: String, amount: java.math.BigDecimal, currency: String, state: String, authCode: String?, declineCode: String?, declineMessage: String?, expiresAt: java.time.OffsetDateTime?, requestId: String?, correlationId: String?) {
        jdbcTemplate.update("""
            insert into issuer.card_authorizations (id, card_id, card_token, end_user_id, wallet_account_id, amount, currency, state, auth_code, decline_code, decline_message, expires_at, request_id, correlation_id)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(), id, card?.id, token, card?.endUserId, card?.walletAccountId, amount, currency, state, authCode, declineCode, declineMessage, expiresAt, requestId, correlationId)
    }

    fun insertHold(id: UUID, authorizationId: UUID, token: String, walletAccountId: UUID, journalEntryId: UUID, amount: java.math.BigDecimal, currency: String, expiresAt: java.time.OffsetDateTime) {
        jdbcTemplate.update("""
            insert into issuer.holds (id, authorization_id, card_token, wallet_account_id, ledger_journal_entry_id, amount, currency, state, expires_at)
            values (?, ?, ?, ?, ?, ?, ?, 'HELD', ?)
        """.trimIndent(), id, authorizationId, token, walletAccountId, journalEntryId, amount, currency, expiresAt)
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


data class IssuerCardRecord(
    val id: UUID,
    val endUserId: UUID,
    val walletAccountId: UUID,
    val cardToken: String,
    val expirationMonth: Int,
    val expirationYear: Int,
    val state: String,
)
