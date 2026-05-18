package com.minifin.platform.kyc

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.OffsetDateTime
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

@Component
class SumsubSignatureVerifier(private val properties: SumsubProperties) {
    fun verify(rawBody: String, signature: String?): Boolean {
        val provided = signature?.trim()?.removePrefix("sha256=") ?: return false
        if (provided.isBlank()) return false
        val expected = hmacHex(properties.webhookSecret, rawBody)
        return MessageDigest.isEqual(expected.toByteArray(StandardCharsets.UTF_8), provided.toByteArray(StandardCharsets.UTF_8))
    }
}

data class SumsubStartResult(val applicantId: String, val accessToken: String, val expiresAt: OffsetDateTime?)

@Component
class SumsubClient(private val properties: SumsubProperties, private val objectMapper: ObjectMapper) {
    private val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()

    fun configured(): Boolean = properties.appToken.isNotBlank() && properties.secretKey.isNotBlank()

    fun start(externalUserId: String): SumsubStartResult {
        if (!configured()) {
            throw KycException("sumsub_not_configured", "Sumsub sandbox credentials are not configured.", HttpStatus.SERVICE_UNAVAILABLE)
        }
        val applicantId = createApplicant(externalUserId)
        val token = createAccessToken(externalUserId)
        return SumsubStartResult(applicantId, token, null)
    }

    private fun createApplicant(externalUserId: String): String {
        val path = "/resources/applicants?levelName=${properties.levelName}"
        val body = objectMapper.writeValueAsString(mapOf("externalUserId" to externalUserId))
        val response = send("POST", path, body)
        if (response.statusCode() !in 200..299) {
            throw KycException("sumsub_applicant_failed", "Sumsub applicant creation failed.", HttpStatus.BAD_GATEWAY)
        }
        return objectMapper.readTree(response.body()).path("id").asText().takeIf { it.isNotBlank() }
            ?: throw KycException("sumsub_applicant_invalid", "Sumsub applicant response was invalid.", HttpStatus.BAD_GATEWAY)
    }

    private fun createAccessToken(externalUserId: String): String {
        val path = "/resources/accessTokens?userId=$externalUserId&levelName=${properties.levelName}"
        val response = send("POST", path, "")
        if (response.statusCode() !in 200..299) {
            throw KycException("sumsub_token_failed", "Sumsub access token creation failed.", HttpStatus.BAD_GATEWAY)
        }
        val json = objectMapper.readTree(response.body())
        return json.path("token").asText().takeIf { it.isNotBlank() } ?: json.path("accessToken").asText().takeIf { it.isNotBlank() }
            ?: throw KycException("sumsub_token_invalid", "Sumsub access token response was invalid.", HttpStatus.BAD_GATEWAY)
    }

    private fun send(method: String, path: String, body: String): HttpResponse<String> {
        val ts = (System.currentTimeMillis() / 1000).toString()
        val signature = hmacHex(properties.secretKey, ts + method + path + body)
        val request = HttpRequest.newBuilder(URI.create(properties.baseUrl.trimEnd('/') + path))
            .timeout(Duration.ofSeconds(8))
            .header("Content-Type", "application/json")
            .header("X-App-Token", properties.appToken)
            .header("X-App-Access-Ts", ts)
            .header("X-App-Access-Sig", signature)
            .method(method, HttpRequest.BodyPublishers.ofString(body))
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }
}

fun hmacHex(secret: String, data: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
    return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

fun sha256Hex(data: String): String =
    MessageDigest.getInstance("SHA-256").digest(data.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }

fun JsonNode.textAny(vararg names: String): String? =
    names.firstNotNullOfOrNull { name -> path(name).asText().takeIf { it.isNotBlank() && it != "null" } }
