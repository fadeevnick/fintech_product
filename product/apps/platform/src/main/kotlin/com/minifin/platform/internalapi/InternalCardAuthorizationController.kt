package com.minifin.platform.internalapi

import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.servlet.http.HttpServletRequest
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.*

data class ApiResponse<T>(val data: T? = null, val errors: List<ApiError> = emptyList())
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiError(val code: String, val message: String, val field: String? = null)
data class ActorControlData(val state: String)
data class AvailableBalanceData(val walletAccountId: String, val ledgerAccountId: String, val availableBalance: String, val currency: String)
data class HoldRequest(val authorizationId: String, val walletAccountId: String, val amount: String, val currency: String, val requestId: String? = null, val correlationId: String? = null)
data class HoldResponse(val journalEntryId: String, val walletAccountId: String, val holdAccountId: String, val amount: String, val currency: String)
class InternalApiException(val code: String, override val message: String, val status: HttpStatus) : RuntimeException(message)

@RestController
class InternalCardAuthorizationController(private val jdbc: JdbcTemplate, @Value("\${minifin.cards.service-auth-secret}") private val secret: String) {
    @GetMapping("/internal/identity/actor-controls/end-users/{endUserId}")
    fun actor(request: HttpServletRequest, @PathVariable endUserId: String): ApiResponse<ActorControlData> {
        requireIssuer(request)
        val state = jdbc.query("select state from identity.actor_controls where actor_type='END_USER' and actor_id=?::uuid", { rs, _ -> rs.getString(1) }, endUserId).firstOrNull() ?: "ACTIVE"
        return ApiResponse(ActorControlData(state))
    }

    @GetMapping("/internal/wallet/accounts/{walletAccountId}/available-balance")
    fun balance(request: HttpServletRequest, @PathVariable walletAccountId: String): ApiResponse<AvailableBalanceData> {
        requireIssuer(request)
        val row = jdbc.query("""
            select wa.id, wa.ledger_account_id, wa.currency, coalesce(b.balance,0) as balance
            from wallet.wallet_accounts wa left join ledger.account_balances b on b.account_id = wa.ledger_account_id
            where wa.id = ?::uuid
        """.trimIndent(), { rs, _ -> AvailableBalanceData(rs.getString("id"), rs.getString("ledger_account_id"), rs.getBigDecimal("balance").setScale(2, RoundingMode.HALF_UP).toPlainString(), rs.getString("currency")) }, walletAccountId).firstOrNull()
            ?: throw InternalApiException("wallet_not_found", "Wallet account was not found.", HttpStatus.NOT_FOUND)
        return ApiResponse(row)
    }

    @PostMapping("/internal/ledger/holds")
    fun hold(request: HttpServletRequest, @RequestBody body: HoldRequest): ApiResponse<HoldResponse> {
        requireIssuer(request)
        val wallet = jdbc.query("select wa.user_id, wa.ledger_account_id from wallet.wallet_accounts wa where wa.id=?::uuid", { rs, _ -> Pair(rs.getObject("user_id", UUID::class.java), rs.getObject("ledger_account_id", UUID::class.java)) }, body.walletAccountId).firstOrNull()
            ?: throw InternalApiException("wallet_not_found", "Wallet account was not found.", HttpStatus.NOT_FOUND)
        val holdAccountId = upsertHoldAccount(wallet.first)
        val journalId = UUID.randomUUID()
        val amount = BigDecimal(body.amount).setScale(4, RoundingMode.UNNECESSARY)
        val postings = """[{"accountId":"${wallet.second}","side":"DEBIT","amount":"${amount.toPlainString()}"},{"accountId":"$holdAccountId","side":"CREDIT","amount":"${amount.toPlainString()}"}]"""
        jdbc.queryForObject("select ledger.post_journal(?::uuid,'CARD_AUTHORIZATION_HOLD','CARD_AUTHORIZATION',?::uuid,?, ?, ?::jsonb)", UUID::class.java, journalId, body.authorizationId, body.currency, "Card authorization hold", postings)
        return ApiResponse(HoldResponse(journalId.toString(), body.walletAccountId, holdAccountId.toString(), amount.setScale(2, RoundingMode.HALF_UP).toPlainString(), body.currency))
    }

    @ExceptionHandler(InternalApiException::class)
    fun handle(ex: InternalApiException): ResponseEntity<ApiResponse<Nothing>> = ResponseEntity.status(ex.status).body(ApiResponse(errors = listOf(ApiError(ex.code, ex.message))))

    private fun requireIssuer(request: HttpServletRequest) {
        if (request.getHeader("X-Service-Secret") != secret || request.getHeader("X-Service-Name") != "issuer") throw InternalApiException("service_auth_denied", "Service is not authorized.", HttpStatus.FORBIDDEN)
    }
    private fun upsertHoldAccount(userId: UUID): UUID {
        val existing = jdbc.query("select id from ledger.accounts where code=?", { rs, _ -> rs.getObject(1, UUID::class.java) }, "WALLET_CARD_HOLD:$userId").firstOrNull()
        if (existing != null) return existing
        val id = UUID.randomUUID()
        jdbc.update("insert into ledger.accounts (id, code, currency, account_type, normal_side, owner_type, owner_id) values (?, ?, 'EUR', 'WALLET_HOLD', 'CREDIT', 'END_USER', ?) on conflict (code) do nothing", id, "WALLET_CARD_HOLD:$userId", userId)
        return id
    }
}
