package com.minifin.acquirer.authorization

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "minifin.acquirer")
data class AcquirerProperties(val networkBaseUrl: String = "http://localhost:8083", val serviceAuthSecret: String = "local-service-secret")
