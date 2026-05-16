package com.minifin.platform.publicapi

import com.fasterxml.jackson.databind.ObjectMapper
import com.minifin.platform.identity.AuditRepository
import com.minifin.platform.identity.IdentityClock
import com.minifin.platform.merchant.apikeys.ApiKeyRecord
import com.minifin.platform.merchant.apikeys.ApiKeyRepository
import com.minifin.platform.merchant.apikeys.sha256Hex
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

data class PublicApiPrincipal(
    val merchantId: UUID,
    val apiKeyId: UUID,
)

@Component
class PublicApiAuthFilter(
    private val apiKeyRepository: ApiKeyRepository,
    private val auditRepository: AuditRepository,
    private val clock: IdentityClock,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.startsWith("/v1/")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header.isNullOrBlank() || !header.startsWith("Bearer ")) {
            writeError(response, HttpStatus.UNAUTHORIZED, "unauthenticated", "API key is required.")
            auditAuthFailure(reason = "missing_bearer", request = request)
            return
        }
        val rawKey = header.removePrefix("Bearer ").trim()
        if (rawKey.isBlank() || !rawKey.startsWith("mfp_live_")) {
            writeError(response, HttpStatus.UNAUTHORIZED, "invalid_api_key", "API key is invalid.")
            auditAuthFailure(reason = "malformed_key", request = request)
            return
        }
        val record: ApiKeyRecord? = apiKeyRepository.findActiveByKeyHash(sha256Hex(rawKey))
        if (record == null) {
            writeError(response, HttpStatus.UNAUTHORIZED, "invalid_api_key", "API key is invalid.")
            auditAuthFailure(reason = "unknown_or_revoked_key", request = request)
            return
        }
        apiKeyRepository.touchLastUsed(record.id, clock.now())
        request.setAttribute(
            PublicApiAttributes.PRINCIPAL,
            PublicApiPrincipal(merchantId = record.merchantId, apiKeyId = record.id),
        )
        filterChain.doFilter(request, response)
    }

    private fun writeError(
        response: HttpServletResponse,
        status: HttpStatus,
        code: String,
        message: String,
    ) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val payload = PublicApiResponse<Nothing>(
            errors = listOf(PublicApiError(code = code, message = message)),
        )
        objectMapper.writeValue(response.outputStream, payload)
    }

    private fun auditAuthFailure(reason: String, request: HttpServletRequest) {
        runCatching {
            auditRepository.write(
                eventType = "publicapi.auth_failed",
                actorType = "ANONYMOUS",
                actorId = null,
                subjectType = "PUBLIC_API",
                subjectId = null,
                outcome = "FAILURE",
                metadataJson = """{"reason":"$reason","route":"${request.requestURI}","method":"${request.method}"}""",
            )
        }
    }
}
