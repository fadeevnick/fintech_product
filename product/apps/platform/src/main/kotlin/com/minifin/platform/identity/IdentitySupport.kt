package com.minifin.platform.identity

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.OffsetDateTime
import java.util.Base64
import java.util.Locale
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class IdentityTokens {
    private val secureRandom = SecureRandom()

    fun newOpaqueToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

@Component
class IdentityClock {
    fun now(): OffsetDateTime = OffsetDateTime.now()
}

@Component
class IdentityProperties(
    @Value("\${minifin.identity.expose-verification-token:false}") val exposeVerificationToken: Boolean,
    @Value("\${minifin.identity.session-ttl-hours:12}") sessionTtlHours: Long,
) {
    val verificationTtl: Duration = Duration.ofHours(24)
    val sessionTtl: Duration = Duration.ofHours(sessionTtlHours)
}

fun normalizeEmail(email: String): String = email.trim().lowercase(Locale.ROOT)

fun requireUuid(value: String, field: String): UUID =
    runCatching { UUID.fromString(value) }
        .getOrElse {
            throw IdentityException(
                code = "invalid_uuid",
                message = "Invalid UUID.",
                field = field,
                status = org.springframework.http.HttpStatus.BAD_REQUEST,
            )
        }
