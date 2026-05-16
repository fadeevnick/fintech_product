package com.minifin.platform.merchant

import java.security.MessageDigest
import java.time.Clock
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.springframework.stereotype.Component

@Component
class StripeWebhookSignatureVerifier(
    private val properties: MerchantStripeProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    sealed class Outcome {
        data object MissingHeader : Outcome()
        data object MissingTimestamp : Outcome()
        data object MissingSignature : Outcome()
        data object TimestampOutsideTolerance : Outcome()
        data object SignatureInvalid : Outcome()
        data class Valid(val timestamp: Long) : Outcome()
    }

    fun verify(rawPayload: ByteArray, header: String?): Outcome {
        if (header.isNullOrBlank()) return Outcome.MissingHeader
        val parsed = parseHeader(header)
        val timestamp = parsed.timestamp ?: return Outcome.MissingTimestamp
        if (parsed.signatures.isEmpty()) return Outcome.MissingSignature

        val nowSeconds = clock.instant().epochSecond
        if (Math.abs(nowSeconds - timestamp) > properties.webhookToleranceSeconds) {
            return Outcome.TimestampOutsideTolerance
        }

        val expectedHex = computeHmacHex(properties.webhookSigningSecret, timestamp, rawPayload)
        val expectedBytes = expectedHex.toByteArray(Charsets.US_ASCII)
        val matched = parsed.signatures.any { provided ->
            val providedBytes = provided.toByteArray(Charsets.US_ASCII)
            providedBytes.size == expectedBytes.size &&
                MessageDigest.isEqual(providedBytes, expectedBytes)
        }
        return if (matched) Outcome.Valid(timestamp) else Outcome.SignatureInvalid
    }

    private data class ParsedHeader(val timestamp: Long?, val signatures: List<String>)

    private fun parseHeader(header: String): ParsedHeader {
        var timestamp: Long? = null
        val signatures = mutableListOf<String>()
        for (part in header.split(",")) {
            val kv = part.trim().split("=", limit = 2)
            if (kv.size != 2) continue
            val key = kv[0].trim()
            val value = kv[1].trim()
            when (key) {
                "t" -> timestamp = value.toLongOrNull()
                "v1" -> signatures.add(value)
            }
        }
        return ParsedHeader(timestamp, signatures)
    }

    private fun computeHmacHex(secret: String, timestamp: Long, payload: ByteArray): String {
        val signedPayload = "$timestamp.".toByteArray(Charsets.UTF_8) + payload
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(signedPayload)
        return buildString(digest.size * 2) {
            for (b in digest) {
                val v = b.toInt() and 0xff
                append(HEX[v ushr 4])
                append(HEX[v and 0x0f])
            }
        }
    }

    private companion object {
        private val HEX = "0123456789abcdef".toCharArray()
    }
}
