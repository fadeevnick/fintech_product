package com.minifin.vault.tokenization

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "minifin.vault")
data class VaultProperties(
    val panEncryptionKey: String = "local-vault-pan-key-change-me",
    val serviceAuthSecret: String = "local-service-secret",
    val bin: String = "400000",
)
