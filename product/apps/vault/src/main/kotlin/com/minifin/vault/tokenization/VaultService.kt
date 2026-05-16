package com.minifin.vault.tokenization

import java.security.SecureRandom
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

class VaultException(val code: String, override val message: String, val status: HttpStatus) : RuntimeException(message)

@Service
class VaultService(private val repository: VaultRepository, private val properties: VaultProperties) {
    private val secureRandom = SecureRandom()

    fun tokenize(): TokenizeResponse {
        val pan = generatePan()
        val token = "card_tok_" + Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(24).also(secureRandom::nextBytes))
        val expirationYear = OffsetDateTime.now().year + 4
        val expirationMonth = 12
        repository.insertToken(UUID.randomUUID(), token, pan, pan.takeLast(4), properties.bin, expirationMonth, expirationYear)
        return TokenizeResponse(token, pan.takeLast(4), expirationMonth, expirationYear, properties.bin)
    }

    fun detokenize(request: DetokenizeRequest, caller: String): DetokenizeResponse {
        if (caller != "issuer") {
            repository.writeDetokenizeAudit(request.cardToken, caller.ifBlank { "unknown" }, "DENIED", "caller_not_issuer", request.requestId, request.correlationId)
            throw VaultException("service_auth_denied", "Only issuer may detokenize card tokens.", HttpStatus.FORBIDDEN)
        }
        val pan = repository.findPan(request.cardToken)
            ?: throw VaultException("token_not_found", "Card token was not found.", HttpStatus.NOT_FOUND)
        repository.writeDetokenizeAudit(request.cardToken, caller, "ALLOWED", null, request.requestId, request.correlationId)
        return DetokenizeResponse(request.cardToken, pan)
    }

    private fun generatePan(): String {
        val body = properties.bin + (1..9).joinToString("") { secureRandom.nextInt(10).toString() }
        return body + luhnCheckDigit(body)
    }

    private fun luhnCheckDigit(numberWithoutCheck: String): Int {
        val sum = numberWithoutCheck.reversed().mapIndexed { index, c ->
            var n = c.digitToInt()
            if (index % 2 == 0) {
                n *= 2
                if (n > 9) n -= 9
            }
            n
        }.sum()
        return (10 - (sum % 10)) % 10
    }
}
