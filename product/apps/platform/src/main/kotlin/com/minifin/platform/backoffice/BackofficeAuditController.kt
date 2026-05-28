package com.minifin.platform.backoffice

import com.minifin.platform.identity.ApiError
import com.minifin.platform.identity.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class BackofficeAuditController(
    private val roleMapper: BackofficeRoleMapper,
    private val service: BackofficeAuditService,
) {
    @GetMapping("/api/v1/backoffice/audit-log")
    fun listFeed(
        authentication: JwtAuthenticationToken,
        @RequestParam(name = "stream", required = false) stream: String?,
        @RequestParam(name = "limit", required = false) limit: Int?,
    ): ApiResponse<BackofficeAuditFeedResponse> {
        val principal = roleMapper.requireBackofficePrincipal(authentication.token)
        return ApiResponse(data = service.listFeed(principal, stream, limit))
    }

    @ExceptionHandler(BackofficeException::class)
    fun handleBackofficeException(exception: BackofficeException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status)
            .body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message))))
}
