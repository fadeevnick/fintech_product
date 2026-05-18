package com.minifin.platform.kyc

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "minifin.kyc.sumsub")
data class SumsubProperties(
    val baseUrl: String = "https://api.sumsub.com",
    val appToken: String = "",
    val secretKey: String = "",
    val webhookSecret: String = "local-sumsub-webhook-secret",
    val levelName: String = "basic-kyc-level",
)
