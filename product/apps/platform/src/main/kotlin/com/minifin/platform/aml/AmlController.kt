package com.minifin.platform.aml

import com.minifin.platform.identity.ApiError
import com.minifin.platform.identity.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
open class AmlController(
    private val amlService: AmlService,
) {
    @PostMapping("/internal/aml/evaluate-velocity")
    open fun evaluateVelocity(
        @RequestBody request: AmlVelocityEvaluationRequest,
    ): ApiResponse<AmlVelocityEvaluationResponse> =
        ApiResponse(data = amlService.evaluateVelocity(request))

    @ExceptionHandler(AmlException::class)
    open fun handleAmlException(exception: AmlException): ResponseEntity<ApiResponse<Nothing>> =
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
