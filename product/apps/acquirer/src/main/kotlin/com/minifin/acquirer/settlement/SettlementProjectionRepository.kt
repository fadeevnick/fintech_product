package com.minifin.acquirer.settlement

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class SettlementProjectionRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun insertProjection(item: SettlementProjectionItem): Int =
        jdbcTemplate.update(
            """
            insert into merchant_settlement.balance_projection (
                id,
                platform_settlement_item_id,
                platform_batch_id,
                merchant_id,
                payment_intent_id,
                gross_amount,
                merchant_net_amount,
                interchange_amount,
                network_assessment_amount,
                acquirer_margin_amount,
                currency,
                platform_settled_at
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (platform_settlement_item_id) do nothing
            """.trimIndent(),
            UUID.randomUUID(),
            UUID.fromString(item.platformSettlementItemId),
            UUID.fromString(item.platformBatchId),
            UUID.fromString(item.merchantId),
            UUID.fromString(item.paymentIntentId),
            BigDecimal(item.grossAmount),
            BigDecimal(item.merchantNetAmount),
            BigDecimal(item.interchangeAmount),
            BigDecimal(item.networkAssessmentAmount),
            BigDecimal(item.acquirerMarginAmount),
            item.currency,
            item.platformSettledAt?.let { OffsetDateTime.parse(it) },
        )
}
