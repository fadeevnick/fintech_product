package com.minifin.platform.merchant

import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

data class StripeAccountLinkRecord(
    val id: UUID,
    val merchantId: UUID,
    val stripeAccountId: String,
    val chargesEnabled: Boolean,
    val payoutsEnabled: Boolean,
    val detailsSubmitted: Boolean,
    val lastEventId: String?,
)

@Repository
class MerchantStripeRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun insertEventRow(
        id: UUID,
        stripeEventId: String,
        eventType: String,
        outcome: String,
        payloadJson: String,
        signatureHeader: String?,
        processedAt: OffsetDateTime?,
    ): Boolean {
        val inserted = jdbcTemplate.update(
            """
            insert into merchant.stripe_webhook_events (
                id,
                stripe_event_id,
                event_type,
                outcome,
                payload_jsonb,
                signature_header,
                processed_at
            )
            values (?, ?, ?, ?, ?::jsonb, ?, ?)
            on conflict (stripe_event_id) do nothing
            """.trimIndent(),
            id,
            stripeEventId,
            eventType,
            outcome,
            payloadJson,
            signatureHeader,
            processedAt,
        )
        return inserted == 1
    }

    fun insertRejectionRow(
        id: UUID,
        stripeEventId: String,
        eventType: String,
        outcome: String,
        payloadJson: String,
        signatureHeader: String?,
    ) {
        jdbcTemplate.update(
            """
            insert into merchant.stripe_webhook_events (
                id,
                stripe_event_id,
                event_type,
                outcome,
                payload_jsonb,
                signature_header,
                processed_at
            )
            values (?, ?, ?, ?, ?::jsonb, ?, now())
            on conflict (stripe_event_id) do nothing
            """.trimIndent(),
            id,
            stripeEventId,
            eventType,
            outcome,
            payloadJson,
            signatureHeader,
        )
    }

    fun findExistingEventOutcome(stripeEventId: String): String? =
        jdbcTemplate.query(
            "select outcome from merchant.stripe_webhook_events where stripe_event_id = ?",
            { rs, _ -> rs.getString("outcome") },
            stripeEventId,
        ).firstOrNull()

    fun findAccountLinkByStripeAccountId(stripeAccountId: String): StripeAccountLinkRecord? =
        jdbcTemplate.query(
            """
            select id, merchant_id, stripe_account_id,
                   charges_enabled, payouts_enabled, details_submitted, last_event_id
            from merchant.stripe_account_links
            where stripe_account_id = ?
            """.trimIndent(),
            { rs, _ -> rs.toAccountLinkRecord() },
            stripeAccountId,
        ).firstOrNull()

    fun updateAccountLinkFromAccountUpdated(
        link: StripeAccountLinkRecord,
        chargesEnabled: Boolean,
        payoutsEnabled: Boolean,
        detailsSubmitted: Boolean,
        lastEventId: String,
    ) {
        jdbcTemplate.update(
            """
            update merchant.stripe_account_links
            set charges_enabled = ?,
                payouts_enabled = ?,
                details_submitted = ?,
                last_event_id = ?,
                last_event_received_at = now(),
                updated_at = now()
            where id = ?
            """.trimIndent(),
            chargesEnabled,
            payoutsEnabled,
            detailsSubmitted,
            lastEventId,
            link.id,
        )
    }

    fun updateMerchantKybStatus(merchantId: UUID, kybStatus: String) {
        jdbcTemplate.update(
            """
            update merchant.merchants
            set kyb_status = ?,
                updated_at = now(),
                version = version + 1
            where id = ?
            """.trimIndent(),
            kybStatus,
            merchantId,
        )
    }

    fun currentMerchantKybStatus(merchantId: UUID): String? =
        jdbcTemplate.query(
            "select kyb_status from merchant.merchants where id = ?",
            { rs, _ -> rs.getString("kyb_status") },
            merchantId,
        ).firstOrNull()

    private fun ResultSet.toAccountLinkRecord(): StripeAccountLinkRecord =
        StripeAccountLinkRecord(
            id = getObject("id", UUID::class.java),
            merchantId = getObject("merchant_id", UUID::class.java),
            stripeAccountId = getString("stripe_account_id"),
            chargesEnabled = getBoolean("charges_enabled"),
            payoutsEnabled = getBoolean("payouts_enabled"),
            detailsSubmitted = getBoolean("details_submitted"),
            lastEventId = getString("last_event_id"),
        )
}
