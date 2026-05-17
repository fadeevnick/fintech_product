package com.minifin.issuer.cards

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class IssuerController(private val service: IssuerService, private val properties: IssuerProperties) {
    @PostMapping("/internal/issuer/cards")
    fun issue(request: HttpServletRequest, @RequestBody body: IssueCardRequest): ApiResponse<CardResponse> {
        if (request.getHeader("X-Service-Secret") != properties.serviceAuthSecret || request.getHeader("X-Service-Name") != "platform") {
            throw IssuerException("service_auth_denied", "Service is not authorized.", HttpStatus.FORBIDDEN)
        }
        return ApiResponse(data = service.issueCard(body))
    }

    @PostMapping("/internal/issuer/authorize")
    fun authorize(request: HttpServletRequest, @RequestBody body: AuthorizeCardRequest): ApiResponse<AuthorizeCardResponse> {
        if (request.getHeader("X-Service-Secret") != properties.serviceAuthSecret || request.getHeader("X-Service-Name") != "network") {
            throw IssuerException("service_auth_denied", "Service is not authorized.", HttpStatus.FORBIDDEN)
        }
        return ApiResponse(data = service.authorize(body))
    }

    @ExceptionHandler(IssuerException::class)
    fun handle(ex: IssuerException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(ex.status).body(ApiResponse(errors = listOf(ApiError(ex.code, ex.message))))
}
