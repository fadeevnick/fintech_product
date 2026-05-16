package com.minifin.platform.backoffice

import org.springframework.http.HttpStatus

data class BackofficeMeResponse(
    val subject: String,
    val email: String?,
    val roles: List<String>,
    val issuer: String,
)

class BackofficeException(
    val code: String,
    override val message: String,
    val status: HttpStatus,
) : RuntimeException(message)
