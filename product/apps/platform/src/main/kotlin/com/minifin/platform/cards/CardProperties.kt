package com.minifin.platform.cards

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "minifin.cards")
data class CardProperties(
    val issuerBaseUrl: String = "http://localhost:8084",
    val acquirerBaseUrl: String = "http://localhost:8082",
    val serviceAuthSecret: String = "local-service-secret",
)
