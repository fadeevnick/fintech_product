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

data class MerchantLoginResult(
    val employee: MerchantEmployeeRecord,
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

    @Transactional(noRollbackFor = [IdentityException::class])
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
            actorType = "END_USER",
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
        val session = repository.findActiveSessionByTokenHash(tokens.hashToken(token), now)
            ?: throw IdentityException(
                code = "unauthenticated",
                message = "Authentication is required.",
                status = HttpStatus.UNAUTHORIZED,
            )
        if (session.actorType != "END_USER") {
            throw IdentityException(
                code = "forbidden_actor_type",
                message = "This session cannot access end-user resources.",
                status = HttpStatus.FORBIDDEN,
            )
        }
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

    @Transactional
    fun registerMerchant(request: MerchantRegisterRequest): MerchantRegisterResponse {
        val normalizedEmail = validateEmail(request.email)
        validatePassword(request.password)
        val companyName = validateRequiredText(request.companyName, "companyName")
        val country = validateRequiredText(request.country, "country")
        val businessType = validateRequiredText(request.businessType, "businessType")
        val merchantId = UUID.randomUUID()
        val employeeId = UUID.randomUUID()

        repository.reserveEmail(normalizedEmail, "MERCHANT_EMPLOYEE", employeeId)
        repository.insertMerchant(
            id = merchantId,
            companyName = companyName,
            country = country,
            businessType = businessType,
        )
        repository.insertMerchantEmployee(
            id = employeeId,
            merchantId = merchantId,
            email = request.email.trim(),
            normalizedEmail = normalizedEmail,
            passwordHash = passwordEncoder.encode(request.password),
            role = "merchant_admin",
        )

        val verificationToken = tokens.newOpaqueToken()
        repository.insertMerchantEmailVerification(
            id = UUID.randomUUID(),
            merchantEmployeeId = employeeId,
            tokenHash = tokens.hashToken(verificationToken),
            expiresAt = clock.now().plus(properties.verificationTtl),
        )
        auditRepository.write(
            eventType = "identity.merchant_registered",
            actorType = "MERCHANT_EMPLOYEE",
            actorId = employeeId,
            subjectType = "MERCHANT",
            subjectId = merchantId,
            outcome = "SUCCESS",
        )

        return MerchantRegisterResponse(
            merchantId = merchantId.toString(),
            employeeId = employeeId.toString(),
            email = request.email.trim(),
            role = "merchant_admin",
            employeeStatus = "EMAIL_UNVERIFIED",
            merchantStatus = "NOT_STARTED",
            verificationToken = verificationToken.takeIf { properties.exposeVerificationToken },
        )
    }

    @Transactional
    fun verifyMerchantEmail(request: VerifyEmailRequest): MerchantVerifyEmailResponse {
        val tokenHash = tokens.hashToken(request.token.trim())
        val verification = repository.findMerchantVerificationByTokenHash(tokenHash)
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

        repository.markMerchantEmailVerified(verification.id, verification.merchantEmployeeId, now)
        val employee = repository.findMerchantEmployeeById(verification.merchantEmployeeId)
            ?: throw IllegalStateException("Merchant employee disappeared during verification")
        if (verification.consumedAt == null) {
            auditRepository.write(
                eventType = "identity.merchant_email_verified",
                actorType = "MERCHANT_EMPLOYEE",
                actorId = employee.id,
                subjectType = "MERCHANT_EMPLOYEE",
                subjectId = employee.id,
                outcome = "SUCCESS",
            )
        }

        return employee.toMerchantVerifyEmailResponse()
    }

    @Transactional(noRollbackFor = [IdentityException::class])
    fun loginMerchant(request: LoginRequest): MerchantLoginResult {
        val normalizedEmail = normalizeEmail(request.email)
        val employee = repository.findMerchantEmployeeByNormalizedEmail(normalizedEmail)
        if (employee == null || !passwordEncoder.matches(request.password, employee.passwordHash)) {
            auditRepository.write(
                eventType = "identity.merchant_login_failed",
                actorType = "ANONYMOUS",
                actorId = null,
                subjectType = "MERCHANT_EMPLOYEE",
                subjectId = employee?.id,
                outcome = "FAILURE",
                metadataJson = """{"reason":"bad_credentials"}""",
            )
            throw IdentityException(
                code = "invalid_credentials",
                message = "Invalid email or password.",
                status = HttpStatus.UNAUTHORIZED,
            )
        }
        if (employee.status != "ACTIVE") {
            auditRepository.write(
                eventType = "identity.merchant_login_failed",
                actorType = "MERCHANT_EMPLOYEE",
                actorId = employee.id,
                subjectType = "MERCHANT_EMPLOYEE",
                subjectId = employee.id,
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
            actorType = "MERCHANT_EMPLOYEE",
            actorId = employee.id,
            expiresAt = clock.now().plus(properties.sessionTtl),
        )
        auditRepository.write(
            eventType = "identity.merchant_login_succeeded",
            actorType = "MERCHANT_EMPLOYEE",
            actorId = employee.id,
            subjectType = "MERCHANT_EMPLOYEE",
            subjectId = employee.id,
            outcome = "SUCCESS",
        )
        return MerchantLoginResult(employee, sessionToken)
    }

    @Transactional
    fun currentMerchant(sessionToken: String?): MerchantEmployeeRecord {
        val token = sessionToken?.takeIf { it.isNotBlank() }
            ?: throw IdentityException(
                code = "unauthenticated",
                message = "Authentication is required.",
                status = HttpStatus.UNAUTHORIZED,
            )
        val now = clock.now()
        val session = repository.findActiveSessionByTokenHash(tokens.hashToken(token), now)
            ?: throw IdentityException(
                code = "unauthenticated",
                message = "Authentication is required.",
                status = HttpStatus.UNAUTHORIZED,
            )
        if (session.actorType != "MERCHANT_EMPLOYEE") {
            throw IdentityException(
                code = "forbidden_actor_type",
                message = "This session cannot access merchant resources.",
                status = HttpStatus.FORBIDDEN,
            )
        }
        repository.touchSession(session.id, now)
        return repository.findMerchantEmployeeById(session.actorId)
            ?: throw IdentityException(
                code = "session_subject_missing",
                message = "Session subject is missing.",
                status = HttpStatus.UNAUTHORIZED,
            )
    }

    @Transactional
    fun logoutMerchant(sessionToken: String?): Boolean {
        val token = sessionToken?.takeIf { it.isNotBlank() } ?: return false
        val now = clock.now()
        val employee = runCatching { currentMerchant(token) }.getOrNull()
        val revoked = repository.revokeSession(tokens.hashToken(token), now)
        if (revoked) {
            auditRepository.write(
                eventType = "identity.merchant_logout",
                actorType = "MERCHANT_EMPLOYEE",
                actorId = employee?.id,
                subjectType = "MERCHANT_EMPLOYEE",
                subjectId = employee?.id,
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

    private fun validateRequiredText(value: String, field: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank() || trimmed.length > 160) {
            throw IdentityException(
                code = "invalid_field",
                message = "Field is required.",
                field = field,
                status = HttpStatus.BAD_REQUEST,
            )
        }
        return trimmed
    }

    private fun MerchantEmployeeRecord.toMerchantVerifyEmailResponse(): MerchantVerifyEmailResponse =
        MerchantVerifyEmailResponse(
            merchantId = merchantId.toString(),
            employeeId = id.toString(),
            email = email,
            role = role,
            employeeStatus = status,
            merchantStatus = merchantStatus,
        )
}
