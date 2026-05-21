package com.minifin.platform.cards

import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

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
}
