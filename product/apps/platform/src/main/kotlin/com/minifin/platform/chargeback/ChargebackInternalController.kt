package com.minifin.platform.chargeback

import com.minifin.platform.identity.ApiError
import com.minifin.platform.identity.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ChargebackInternalController(
    private val chargebackService: ChargebackService,
) {
    @PostMapping("/internal/chargebacks/process-deadlines")
    fun processDeadlines(
        @RequestParam(defaultValue = "50") limit: Int,
    ): ApiResponse<DeadlineExpiryProcessDto> =
        ApiResponse(data = chargebackService.processMerchantDeadlines(limit))

    @ExceptionHandler(ChargebackException::class)
    fun handleChargebackException(exception: ChargebackException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status)
            .body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message, exception.field))))
}
