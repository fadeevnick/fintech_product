package com.minifin.network.authorization

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "minifin.network")
data class NetworkProperties(val issuerBaseUrl: String = "http://localhost:8084", val serviceAuthSecret: String = "local-service-secret")
