package com.minifin.platform.wallet

import com.minifin.platform.backoffice.BackofficeException
import com.minifin.platform.backoffice.BackofficeRoleMapper
import com.minifin.platform.controls.ActorControlException
import com.minifin.platform.identity.ApiError
import com.minifin.platform.identity.ApiResponse
import com.minifin.platform.identity.IdentityException
import com.minifin.platform.identity.IdentityService
import com.minifin.platform.ledger.LedgerException
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

private const val SESSION_COOKIE = "MFP_SESSION"

@RestController
class WalletController(
    private val walletService: WalletService,
    private val manualOpsService: ManualOpsService,
    private val identityService: IdentityService,
    private val roleMapper: BackofficeRoleMapper,
) {
    @PostMapping("/api/v1/deposits")
    fun createDeposit(
        @CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?,
        @RequestBody request: DepositRequestCreate,
    ): ResponseEntity<ApiResponse<DepositRequestResponse>> {
        val user = identityService.currentUser(sessionToken)
        val deposit = walletService.createDeposit(user, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse(data = deposit))
    }

    @PostMapping("/api/v1/withdrawals")
    fun createWithdrawal(
        @CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?,
        @RequestBody request: WithdrawalRequestCreate,
    ): ResponseEntity<ApiResponse<WithdrawalRequestResponse>> {
        val user = identityService.currentUser(sessionToken)
        val withdrawal = walletService.createWithdrawal(user, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse(data = withdrawal))
    }

    @GetMapping("/api/v1/wallet")
    fun walletSummary(
        @CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?,
    ): ApiResponse<WalletSummaryResponse> {
        val user = identityService.currentUser(sessionToken)
        return ApiResponse(data = walletService.walletSummary(user))
    }

    @GetMapping("/api/v1/backoffice/manual-ops/deposits")
    fun listPendingDeposits(
        authentication: JwtAuthenticationToken,
    ): ApiResponse<List<DepositRequestResponse>> {
        val principal = roleMapper.requireBackofficePrincipal(authentication.token)
        return ApiResponse(data = manualOpsService.listPendingDeposits(principal, limit = 100))
    }

    @GetMapping("/api/v1/backoffice/manual-ops/withdrawals")
    fun listHeldWithdrawals(
        authentication: JwtAuthenticationToken,
    ): ApiResponse<List<WithdrawalRequestResponse>> {
        val principal = roleMapper.requireBackofficePrincipal(authentication.token)
        return ApiResponse(data = manualOpsService.listHeldWithdrawals(principal, limit = 100))
    }

    @PostMapping("/api/v1/backoffice/manual-ops/deposits/{depositId}/decision")
    fun decide(
        authentication: JwtAuthenticationToken,
        @PathVariable depositId: String,
        @RequestBody request: ManualOpsDepositDecision,
    ): ApiResponse<DepositRequestResponse> {
        val principal = roleMapper.requireBackofficePrincipal(authentication.token)
        val depositUuid = requireUuid(depositId)
        return ApiResponse(data = manualOpsService.decide(depositUuid, request, principal))
    }

    @PostMapping("/api/v1/backoffice/manual-ops/withdrawals/{withdrawalId}/decision")
    fun decideWithdrawal(
        authentication: JwtAuthenticationToken,
        @PathVariable withdrawalId: String,
        @RequestBody request: ManualOpsWithdrawalDecision,
    ): ApiResponse<WithdrawalRequestResponse> {
        val principal = roleMapper.requireBackofficePrincipal(authentication.token)
        val withdrawalUuid = requireUuid(withdrawalId)
        return ApiResponse(data = manualOpsService.decideWithdrawal(withdrawalUuid, request, principal))
    }

    @ExceptionHandler(WalletException::class)
    fun handleWalletException(exception: WalletException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status)
            .body(
                ApiResponse(
                    errors = listOf(
                        ApiError(
                            code = exception.code,
                            message = exception.message,
                            field = exception.field,
                        ),
                    ),
                ),
            )

    @ExceptionHandler(ActorControlException::class)
    fun handleActorControlException(exception: ActorControlException): ResponseEntity<ApiResponse<Nothing>> =
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

    @ExceptionHandler(BackofficeException::class)
    fun handleBackofficeException(exception: BackofficeException): ResponseEntity<ApiResponse<Nothing>> =
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

    @ExceptionHandler(IdentityException::class)
    fun handleIdentityException(exception: IdentityException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status)
            .body(
                ApiResponse(
                    errors = listOf(
                        ApiError(
                            code = exception.code,
                            message = exception.message,
                            field = exception.field,
                        ),
                    ),
                ),
            )

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

    private fun requireUuid(value: String): UUID =
        runCatching { UUID.fromString(value) }
            .getOrElse {
                throw WalletException(
                    code = "invalid_uuid",
                    message = "Invalid UUID.",
                    status = HttpStatus.BAD_REQUEST,
                )
            }
}
