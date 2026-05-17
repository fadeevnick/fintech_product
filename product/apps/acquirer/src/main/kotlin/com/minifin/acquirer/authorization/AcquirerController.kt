package com.minifin.acquirer.authorization

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.*
import org.springframework.web.bind.annotation.*

@RestController
class AcquirerController(private val service: AcquirerService, private val props: AcquirerProperties) {
    @PostMapping("/internal/acquirer/authorize")
    fun authorize(request: HttpServletRequest, @RequestBody body: AcquirerAuthorizeRequest): ApiResponse<AcquirerAuthorizeResponse> {
        if (request.getHeader("X-Service-Secret") != props.serviceAuthSecret || request.getHeader("X-Service-Name") != "platform") throw AcquirerException("service_auth_denied", "Service is not authorized.", HttpStatus.FORBIDDEN)
        return ApiResponse(data = service.authorize(body))
    }
    @ExceptionHandler(AcquirerException::class)
    fun handle(ex: AcquirerException): ResponseEntity<ApiResponse<Nothing>> = ResponseEntity.status(ex.status).body(ApiResponse(errors = listOf(ApiError(ex.code, ex.message))))
}
