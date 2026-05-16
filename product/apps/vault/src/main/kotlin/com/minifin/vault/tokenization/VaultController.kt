package com.minifin.vault.tokenization

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class VaultController(private val service: VaultService, private val properties: VaultProperties) {
    @PostMapping("/internal/vault/tokenize")
    fun tokenize(request: HttpServletRequest, @RequestBody body: TokenizeRequest): ApiResponse<TokenizeResponse> {
        requireService(request, "issuer")
        return ApiResponse(data = service.tokenize())
    }

    @PostMapping("/internal/vault/detokenize")
    fun detokenize(request: HttpServletRequest, @RequestBody body: DetokenizeRequest): ApiResponse<DetokenizeResponse> {
        val caller = request.getHeader("X-Service-Name") ?: ""
        requireSecret(request)
        return ApiResponse(data = service.detokenize(body, caller))
    }

    private fun requireService(request: HttpServletRequest, expected: String) {
        requireSecret(request)
        if (request.getHeader("X-Service-Name") != expected) {
            throw VaultException("service_auth_denied", "Service is not authorized.", org.springframework.http.HttpStatus.FORBIDDEN)
        }
    }

    private fun requireSecret(request: HttpServletRequest) {
        if (request.getHeader("X-Service-Secret") != properties.serviceAuthSecret) {
            throw VaultException("service_auth_denied", "Service authentication failed.", org.springframework.http.HttpStatus.UNAUTHORIZED)
        }
    }

    @ExceptionHandler(VaultException::class)
    fun handle(ex: VaultException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(ex.status).body(ApiResponse(errors = listOf(ApiError(ex.code, ex.message))))
}
