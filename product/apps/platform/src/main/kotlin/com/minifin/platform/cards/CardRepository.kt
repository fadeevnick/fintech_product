package com.minifin.platform.cards

import java.sql.ResultSet
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

data class IssuedCardRecord(
    val id: UUID,
    val state: String,
    val last4: String,
    val expirationMonth: Int,
    val expirationYear: Int,
    val bin: String,
)

@Repository
class CardRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun insertIssuedCard(
        id: UUID,
        endUserId: UUID,
        walletAccountId: UUID,
        cardToken: String?,
        state: String,
        last4: String,
        bin: String,
        expirationMonth: Int,
        expirationYear: Int,
    ) {
        jdbcTemplate.update(
            """
            insert into cards.issued_cards (
                id, end_user_id, wallet_account_id, card_token, state, last4, bin, expiration_month, expiration_year
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (id) do nothing
            """.trimIndent(),
            id,
            endUserId,
            walletAccountId,
            cardToken,
            state,
            last4,
            bin,
            expirationMonth,
            expirationYear,
        )
    }

    fun listIssuedCards(endUserId: UUID): List<IssuedCardRecord> =
        jdbcTemplate.query(
            """
            select id, state, last4, expiration_month, expiration_year, bin
              from cards.issued_cards
             where end_user_id = ?
             order by created_at desc, id desc
            """.trimIndent(),
            { rs, _ -> rs.toIssuedCardRecord() },
            endUserId,
        )

    fun findIssuedCard(id: UUID, endUserId: UUID): IssuedCardRecord? =
        jdbcTemplate.query(
            """
            select id, state, last4, expiration_month, expiration_year, bin
              from cards.issued_cards
             where id = ?
               and end_user_id = ?
            """.trimIndent(),
            { rs, _ -> rs.toIssuedCardRecord() },
            id,
            endUserId,
        ).firstOrNull()

    private fun ResultSet.toIssuedCardRecord(): IssuedCardRecord =
        IssuedCardRecord(
            id = getObject("id", UUID::class.java),
            state = getString("state"),
            last4 = getString("last4"),
            expirationMonth = getInt("expiration_month"),
            expirationYear = getInt("expiration_year"),
            bin = getString("bin"),
        )
}
