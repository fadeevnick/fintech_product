package com.minifin.issuer.cards

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "minifin.issuer")
data class IssuerProperties(
    val serviceAuthSecret: String = "local-service-secret",
    val vaultBaseUrl: String = "http://localhost:8085",
)
