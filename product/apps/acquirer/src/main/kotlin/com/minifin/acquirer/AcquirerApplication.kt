package com.minifin.acquirer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AcquirerApplication

fun main(args: Array<String>) {
    runApplication<AcquirerApplication>(*args)
}
