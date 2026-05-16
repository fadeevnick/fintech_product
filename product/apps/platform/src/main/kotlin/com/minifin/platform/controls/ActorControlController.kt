package com.minifin.platform.controls

import com.minifin.platform.backoffice.BackofficeException
import com.minifin.platform.backoffice.BackofficeRoleMapper
import com.minifin.platform.identity.ApiError
import com.minifin.platform.identity.ApiResponse
import com.minifin.platform.identity.IdentityService
import java.util.UUID
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

private const val SESSION_COOKIE = "MFP_SESSION"

@RestController
class ActorControlController(
    private val roleMapper: BackofficeRoleMapper,
    private val actorControlService: ActorControlService,
    private val identityService: IdentityService,
) {
    @GetMapping("/api/v1/backoffice/read-audit/probe/{resourceId}")
    fun readAuditProbe(
        authentication: JwtAuthenticationToken,
        @PathVariable resourceId: String,
    ): ApiResponse<ReadAuditProbeResponse> {
        val principal = roleMapper.requireBackofficePrincipal(authentication.token)
        val resourceUuid = requireUuid(resourceId)
        actorControlService.writeReadAuditProbe(principal, resourceUuid)
        return ApiResponse(
            data = ReadAuditProbeResponse(
                resourceId = resourceUuid.toString(),
                resourceType = "READ_AUDIT_PROBE",
                readAudited = true,
            ),
        )
    }

    @PostMapping("/api/v1/backoffice/actor-controls")
    fun setActorControl(
        authentication: JwtAuthenticationToken,
        @RequestBody request: ActorControlRequest,
    ): ApiResponse<ActorControlResponse> {
        val principal = roleMapper.requireBackofficePrincipal(authentication.token)
        return ApiResponse(data = actorControlService.setControl(request, principal))
    }

    @PostMapping("/api/v1/enduser/write-guard/probe")
    fun endUserWriteGuardProbe(
        @CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?,
    ): ApiResponse<WriteGuardProbeResponse> {
        val user = identityService.currentUser(sessionToken)
        actorControlService.requireWriteAllowed("END_USER", user.id)
        return ApiResponse(data = WriteGuardProbeResponse("END_USER", user.id.toString(), true))
    }

    @PostMapping("/api/v1/merchant/write-guard/probe")
    fun merchantWriteGuardProbe(
        @CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?,
    ): ApiResponse<WriteGuardProbeResponse> {
        val employee = identityService.currentMerchant(sessionToken)
        actorControlService.requireWriteAllowed("MERCHANT", employee.merchantId)
        return ApiResponse(data = WriteGuardProbeResponse("MERCHANT", employee.merchantId.toString(), true))
    }

    @ExceptionHandler(ActorControlException::class)
    fun handleActorControlException(exception: ActorControlException): ResponseEntity<ApiResponse<Nothing>> =
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

    private fun requireUuid(value: String): UUID =
        runCatching { UUID.fromString(value) }
            .getOrElse {
                throw ActorControlException(
                    code = "invalid_uuid",
                    message = "Invalid UUID.",
                    status = org.springframework.http.HttpStatus.BAD_REQUEST,
                )
            }
}
