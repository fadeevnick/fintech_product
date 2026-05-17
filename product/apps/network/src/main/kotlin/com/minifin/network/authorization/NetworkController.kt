package com.minifin.network.authorization

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.*
import org.springframework.web.bind.annotation.*

@RestController
class NetworkController(private val service: NetworkService, private val props: NetworkProperties) {
    @PostMapping("/internal/network/authorize")
    fun authorize(request: HttpServletRequest, @RequestBody body: NetworkAuthorizeRequest): ApiResponse<NetworkAuthorizeResponse> {
        if (request.getHeader("X-Service-Secret") != props.serviceAuthSecret || request.getHeader("X-Service-Name") != "acquirer") throw NetworkException("service_auth_denied", "Service is not authorized.", HttpStatus.FORBIDDEN)
        return ApiResponse(data = service.authorize(body))
    }
    @ExceptionHandler(NetworkException::class)
    fun handle(ex: NetworkException): ResponseEntity<ApiResponse<Nothing>> = ResponseEntity.status(ex.status).body(ApiResponse(errors = listOf(ApiError(ex.code, ex.message))))
}
