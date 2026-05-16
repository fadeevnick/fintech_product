package com.minifin.platform.merchant

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
class MerchantStripeProperties(
    @Value("\${minifin.merchant.stripe.webhook-signing-secret}")
    val webhookSigningSecret: String,
    @Value("\${minifin.merchant.stripe.webhook-tolerance-seconds:300}")
    val webhookToleranceSeconds: Long,
)
