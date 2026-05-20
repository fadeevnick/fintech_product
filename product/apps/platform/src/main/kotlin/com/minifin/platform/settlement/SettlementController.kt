package com.minifin.platform.settlement

import com.minifin.platform.identity.ApiError
import com.minifin.platform.identity.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class SettlementController(
    private val settlementService: SettlementService,
) {
    @PostMapping("/internal/settlement/process-captured")
    fun processCaptured(
        @RequestParam(defaultValue = "50") limit: Int,
    ): ApiResponse<SettlementProcessResponse> =
        ApiResponse(data = settlementService.processCaptured(limit))

    @PostMapping("/internal/settlement/publish-projections")
    fun publishProjections(
        @RequestParam(defaultValue = "100") limit: Int,
    ): ApiResponse<SettlementProjectionPublishResponse> =
        ApiResponse(data = settlementService.publishProjections(limit))

    @ExceptionHandler(SettlementException::class)
    fun handleSettlementException(exception: SettlementException): ResponseEntity<ApiResponse<Nothing>> =
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
