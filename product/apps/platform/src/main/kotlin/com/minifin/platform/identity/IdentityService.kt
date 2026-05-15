package com.minifin.platform.identity

import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class LoginResult(
    val user: EndUserRecord,
    val sessionToken: String,
)

@Service
class IdentityService(
    private val repository: IdentityRepository,
    private val auditRepository: AuditRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokens: IdentityTokens,
    private val clock: IdentityClock,
    private val properties: IdentityProperties,
) {
    @Transactional
    fun register(request: RegisterRequest): RegisterResponse {
        val normalizedEmail = validateEmail(request.email)
        validatePassword(request.password)
        val userId = UUID.randomUUID()
        repository.reserveEmail(normalizedEmail, "END_USER", userId)
        repository.insertEndUser(
            id = userId,
            email = request.email.trim(),
            normalizedEmail = normalizedEmail,
            passwordHash = passwordEncoder.encode(request.password),
        )

        val verificationToken = tokens.newOpaqueToken()
        repository.insertEmailVerification(
            id = UUID.randomUUID(),
            endUserId = userId,
            tokenHash = tokens.hashToken(verificationToken),
            expiresAt = clock.now().plus(properties.verificationTtl),
        )
        auditRepository.write(
            eventType = "identity.end_user_registered",
            actorType = "END_USER",
            actorId = userId,
            subjectType = "END_USER",
            subjectId = userId,
            outcome = "SUCCESS",
        )

        return RegisterResponse(
            userId = userId.toString(),
            email = request.email.trim(),
            status = "EMAIL_UNVERIFIED",
            verificationToken = verificationToken.takeIf { properties.exposeVerificationToken },
        )
    }

    @Transactional
    fun verifyEmail(request: VerifyEmailRequest): VerifyEmailResponse {
        val tokenHash = tokens.hashToken(request.token.trim())
        val verification = repository.findVerificationByTokenHash(tokenHash)
            ?: throw IdentityException(
                code = "verification_token_invalid",
                message = "Verification token is invalid.",
                field = "token",
                status = HttpStatus.BAD_REQUEST,
            )
        val now = clock.now()
        if (verification.expiresAt.isBefore(now)) {
            throw IdentityException(
                code = "verification_token_expired",
                message = "Verification token has expired.",
                field = "token",
                status = HttpStatus.BAD_REQUEST,
            )
        }

        repository.markEmailVerified(verification.id, verification.endUserId, now)
        val user = repository.findEndUserById(verification.endUserId)
            ?: throw IllegalStateException("End user disappeared during verification")
        if (verification.consumedAt == null) {
            auditRepository.write(
                eventType = "identity.email_verified",
                actorType = "END_USER",
                actorId = user.id,
                subjectType = "END_USER",
                subjectId = user.id,
                outcome = "SUCCESS",
            )
        }

        return VerifyEmailResponse(user.id.toString(), user.email, user.status)
    }

    @Transactional
    fun login(request: LoginRequest): LoginResult {
        val normalizedEmail = normalizeEmail(request.email)
        val user = repository.findEndUserByNormalizedEmail(normalizedEmail)
        if (user == null || !passwordEncoder.matches(request.password, user.passwordHash)) {
            auditRepository.write(
                eventType = "identity.login_failed",
                actorType = "ANONYMOUS",
                actorId = null,
                subjectType = "END_USER",
                subjectId = user?.id,
                outcome = "FAILURE",
                metadataJson = """{"reason":"bad_credentials"}""",
            )
            throw IdentityException(
                code = "invalid_credentials",
                message = "Invalid email or password.",
                status = HttpStatus.UNAUTHORIZED,
            )
        }
        if (user.status != "ACTIVE") {
            auditRepository.write(
                eventType = "identity.login_failed",
                actorType = "END_USER",
                actorId = user.id,
                subjectType = "END_USER",
                subjectId = user.id,
                outcome = "FAILURE",
                metadataJson = """{"reason":"not_active"}""",
            )
            throw IdentityException(
                code = "email_not_verified",
                message = "Email must be verified before login.",
                status = HttpStatus.FORBIDDEN,
            )
        }

        val sessionToken = tokens.newOpaqueToken()
        repository.insertSession(
            id = UUID.randomUUID(),
            sessionTokenHash = tokens.hashToken(sessionToken),
            actorId = user.id,
            expiresAt = clock.now().plus(properties.sessionTtl),
        )
        auditRepository.write(
            eventType = "identity.login_succeeded",
            actorType = "END_USER",
            actorId = user.id,
            subjectType = "END_USER",
            subjectId = user.id,
            outcome = "SUCCESS",
        )
        return LoginResult(user, sessionToken)
    }

    @Transactional
    fun currentUser(sessionToken: String?): EndUserRecord {
        val token = sessionToken?.takeIf { it.isNotBlank() }
            ?: throw IdentityException(
                code = "unauthenticated",
                message = "Authentication is required.",
                status = HttpStatus.UNAUTHORIZED,
            )
        val now = clock.now()
        val session = repository.findSessionByTokenHash(tokens.hashToken(token), now)
            ?: throw IdentityException(
                code = "unauthenticated",
                message = "Authentication is required.",
                status = HttpStatus.UNAUTHORIZED,
            )
        repository.touchSession(session.id, now)
        return repository.findEndUserById(session.actorId)
            ?: throw IdentityException(
                code = "session_subject_missing",
                message = "Session subject is missing.",
                status = HttpStatus.UNAUTHORIZED,
            )
    }

    @Transactional
    fun logout(sessionToken: String?): Boolean {
        val token = sessionToken?.takeIf { it.isNotBlank() } ?: return false
        val now = clock.now()
        val user = runCatching { currentUser(token) }.getOrNull()
        val revoked = repository.revokeSession(tokens.hashToken(token), now)
        if (revoked) {
            auditRepository.write(
                eventType = "identity.logout",
                actorType = "END_USER",
                actorId = user?.id,
                subjectType = "END_USER",
                subjectId = user?.id,
                outcome = "SUCCESS",
            )
        }
        return revoked
    }

    private fun validateEmail(email: String): String {
        val normalized = normalizeEmail(email)
        if (!normalized.contains("@") || normalized.length > 320) {
            throw IdentityException(
                code = "invalid_email",
                message = "Email is invalid.",
                field = "email",
                status = HttpStatus.BAD_REQUEST,
            )
        }
        return normalized
    }

    private fun validatePassword(password: String) {
        if (password.length < 10) {
            throw IdentityException(
                code = "weak_password",
                message = "Password must be at least 10 characters.",
                field = "password",
                status = HttpStatus.BAD_REQUEST,
            )
        }
    }
}
