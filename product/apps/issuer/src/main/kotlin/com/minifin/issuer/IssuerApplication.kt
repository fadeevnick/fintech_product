package com.minifin.issuer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@ConfigurationPropertiesScan
@SpringBootApplication
class IssuerApplication

fun main(args: Array<String>) {
    runApplication<IssuerApplication>(*args)
}
