package com.minifin.issuer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class IssuerApplication

fun main(args: Array<String>) {
    runApplication<IssuerApplication>(*args)
}
