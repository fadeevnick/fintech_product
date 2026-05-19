package com.minifin.platform.sanctions

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "minifin.sanctions.opensanctions")
data class OpenSanctionsProperties(
    val baseUrl: String = "https://api.opensanctions.org",
    val apiKey: String = "",
    val timeoutMs: Long = 3000,
    val localMode: String = "disabled",
    val matchThreshold: Double = 0.85,
)
