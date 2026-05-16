package com.minifin.platform.backoffice

import com.minifin.platform.identity.ApiError
import com.minifin.platform.identity.ApiResponse
import com.minifin.platform.identity.AuditRepository
import java.util.UUID
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class BackofficeController(
    private val roleMapper: BackofficeRoleMapper,
    private val auditRepository: AuditRepository,
) {
    @GetMapping("/api/v1/backoffice/me")
    fun me(authentication: JwtAuthenticationToken): ApiResponse<BackofficeMeResponse> {
        val principal = try {
            roleMapper.requireBackofficePrincipal(authentication.token)
        } catch (exception: BackofficeException) {
            val subjectUuid = runCatching { UUID.fromString(authentication.token.subject ?: "") }.getOrNull()
            auditRepository.write(
                eventType = "identity.backoffice_auth_failed",
                actorType = "BACKOFFICE",
                actorId = subjectUuid,
                subjectType = "BACKOFFICE_USER",
                subjectId = subjectUuid,
                outcome = "FAILURE",
                metadataJson = """{"reason":"${exception.code}"}""",
            )
            throw exception
        }
        auditRepository.write(
            eventType = "identity.backoffice_authenticated",
            actorType = "BACKOFFICE",
            actorId = principal.subjectUuid,
            subjectType = "BACKOFFICE_USER",
            subjectId = principal.subjectUuid,
            outcome = "SUCCESS",
            metadataJson = """{"roles":${principal.roles.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }}}""",
        )
        return ApiResponse(
            data = BackofficeMeResponse(
                subject = principal.subject,
                email = principal.email,
                roles = principal.roles,
                issuer = principal.issuer,
            ),
        )
    }

    @ExceptionHandler(BackofficeException::class)
    fun handleBackofficeException(exception: BackofficeException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status)
            .body(
                ApiResponse(
                    errors = listOf(
                        ApiError(
                            code = exception.code,
                            message = exception.message,
                        ),
                    ),
                ),
            )
}
