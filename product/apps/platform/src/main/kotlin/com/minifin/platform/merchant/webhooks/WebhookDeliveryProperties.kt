package com.minifin.platform.merchant.webhooks

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "minifin.merchant.webhooks")
data class WebhookDeliveryProperties(
    val signingSecretEncryptionKey: String = "local-webhook-signing-secret-key-change-me",
)
