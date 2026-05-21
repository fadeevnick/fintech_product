package com.minifin.platform.aml

import com.minifin.platform.backoffice.BackofficeException
import com.minifin.platform.backoffice.BackofficeRoleMapper
import com.minifin.platform.identity.ApiError
import com.minifin.platform.identity.ApiResponse
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
open class AmlBackofficeController(
    private val roleMapper: BackofficeRoleMapper,
    private val service: AmlService,
) {
    @GetMapping("/api/v1/backoffice/aml-alerts")
    open fun list(authentication: JwtAuthenticationToken): ApiResponse<List<AmlAlertResponse>> {
        roleMapper.requireBackofficePrincipal(authentication.token)
        return ApiResponse(data = service.listAlerts())
    }

    @GetMapping("/api/v1/backoffice/aml-alerts/{id}")
    open fun detail(authentication: JwtAuthenticationToken, @PathVariable id: String): ApiResponse<AmlAlertResponse> {
        roleMapper.requireBackofficePrincipal(authentication.token)
        return ApiResponse(data = service.getAlert(requireUuid(id)))
    }

    @PostMapping("/api/v1/backoffice/aml-alerts/{id}/decision")
    open fun decide(
        authentication: JwtAuthenticationToken,
        @PathVariable id: String,
        @RequestBody request: AmlAlertDecisionRequest,
    ): ApiResponse<AmlAlertDecisionResponse> {
        val principal = roleMapper.requireBackofficePrincipal(authentication.token)
        return ApiResponse(data = service.decideAlert(requireUuid(id), request, principal))
    }

    @ExceptionHandler(AmlException::class)
    open fun handleAmlException(exception: AmlException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status)
            .body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message))))

    @ExceptionHandler(BackofficeException::class)
    open fun handleBackofficeException(exception: BackofficeException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status)
            .body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message))))

    private fun requireUuid(value: String): UUID =
        runCatching { UUID.fromString(value) }
            .getOrElse { throw AmlException("invalid_uuid", "Invalid UUID.", HttpStatus.BAD_REQUEST) }
}
