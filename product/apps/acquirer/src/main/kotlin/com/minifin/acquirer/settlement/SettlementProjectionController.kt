package com.minifin.acquirer.settlement

import com.minifin.acquirer.authorization.AcquirerProperties
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class SettlementProjectionController(
    private val service: SettlementProjectionService,
    private val props: AcquirerProperties,
) {
    @PostMapping("/internal/settlement/projections")
    fun ingest(
        request: HttpServletRequest,
        @RequestBody body: SettlementProjectionRequest,
    ): ApiResponse<SettlementProjectionResponse> {
        if (request.getHeader("X-Service-Secret") != props.serviceAuthSecret || request.getHeader("X-Service-Name") != "platform") {
            throw SettlementProjectionException("service_auth_denied", "Service is not authorized.", HttpStatus.FORBIDDEN)
        }
        return ApiResponse(data = service.ingest(body))
    }

    @ExceptionHandler(SettlementProjectionException::class)
    fun handle(exception: SettlementProjectionException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status)
            .body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message))))
}

class SettlementProjectionException(
    val code: String,
    override val message: String,
    val status: HttpStatus,
) : RuntimeException(message)
