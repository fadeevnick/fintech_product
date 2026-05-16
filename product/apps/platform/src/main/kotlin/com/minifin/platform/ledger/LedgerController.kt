package com.minifin.platform.ledger

import com.minifin.platform.identity.ApiError
import com.minifin.platform.identity.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class LedgerController(
    private val ledgerService: LedgerService,
) {
    @PostMapping("/internal/ledger/runtime/accounts")
    fun upsertAccount(@RequestBody request: LedgerAccountRequest): ApiResponse<LedgerAccountResponse> =
        ApiResponse(data = ledgerService.upsertAccount(request))

    @PostMapping("/internal/ledger/runtime/journals")
    fun postJournal(@RequestBody request: LedgerJournalRequest): ApiResponse<LedgerJournalResponse> =
        ApiResponse(data = ledgerService.postJournal(request))

    @GetMapping("/internal/ledger/runtime/accounts/{accountId}/balance")
    fun balance(@PathVariable accountId: String): ApiResponse<LedgerBalanceResponse> =
        ApiResponse(data = ledgerService.balance(accountId))

    @GetMapping("/internal/ledger/runtime/reconciliation")
    fun reconciliation(): ApiResponse<LedgerReconciliationResponse> =
        ApiResponse(data = ledgerService.reconciliation())

    @ExceptionHandler(LedgerException::class)
    fun handleLedgerException(exception: LedgerException): ResponseEntity<ApiResponse<Nothing>> =
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
