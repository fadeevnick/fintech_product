package com.minifin.acquirer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@ConfigurationPropertiesScan
@SpringBootApplication
class AcquirerApplication

fun main(args: Array<String>) {
    runApplication<AcquirerApplication>(*args)
}
