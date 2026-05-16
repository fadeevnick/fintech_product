package com.minifin.vault

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@ConfigurationPropertiesScan
@SpringBootApplication
class VaultApplication

fun main(args: Array<String>) {
    runApplication<VaultApplication>(*args)
}
