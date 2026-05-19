package com.minifin.platform.sanctions

import com.fasterxml.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class OpenSanctionsClient(
    private val properties: OpenSanctionsProperties,
    private val objectMapper: ObjectMapper,
) {
    private val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(properties.timeoutMs)).build()

    fun screenEndUser(endUserId: UUID): OpenSanctionsScreeningResult {
        val mode = properties.localMode.trim().lowercase()
        if (mode != "disabled") return localResult(mode)
        if (properties.apiKey.isBlank()) {
            return OpenSanctionsScreeningResult(OpenSanctionsScreeningOutcome.UNAVAILABLE, "opensanctions-not-configured")
        }
        return try {
            val requestId = UUID.randomUUID().toString()
            val body = objectMapper.writeValueAsString(mapOf("queries" to mapOf(endUserId.toString() to mapOf("schema" to "Person", "properties" to mapOf("name" to listOf(endUserId.toString()))))))
            val request = HttpRequest.newBuilder(URI.create(properties.baseUrl.trimEnd('/') + "/match/default"))
                .timeout(Duration.ofMillis(properties.timeoutMs))
                .header("Authorization", "ApiKey ${properties.apiKey}")
                .header("Content-Type", "application/json")
                .header("X-Request-Id", requestId)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                OpenSanctionsScreeningResult(OpenSanctionsScreeningOutcome.UNAVAILABLE, requestId)
            } else {
                parseResponse(response.body(), requestId)
            }
        } catch (_: Exception) {
            OpenSanctionsScreeningResult(OpenSanctionsScreeningOutcome.UNAVAILABLE, "opensanctions-error")
        }
    }

    private fun localResult(mode: String): OpenSanctionsScreeningResult = when (mode) {
        "no_match" -> OpenSanctionsScreeningResult(OpenSanctionsScreeningOutcome.NO_MATCH, "local-no-match")
        "match" -> OpenSanctionsScreeningResult(
            OpenSanctionsScreeningOutcome.POSSIBLE_MATCH,
            "local-match",
            BigDecimal("0.9900"),
            "local-sanctions-entity-1",
            "Runtime Sanctions Match",
        )
        "unavailable", "timeout" -> OpenSanctionsScreeningResult(OpenSanctionsScreeningOutcome.UNAVAILABLE, "local-unavailable")
        else -> OpenSanctionsScreeningResult(OpenSanctionsScreeningOutcome.UNAVAILABLE, "local-invalid-mode")
    }

    private fun parseResponse(body: String, requestId: String): OpenSanctionsScreeningResult {
        val json = objectMapper.readTree(body)
        val firstResult = json.path("responses").elements().asSequence()
            .flatMap { it.path("results").elements().asSequence() }
            .maxByOrNull { it.path("score").asDouble(0.0) }
        val score = firstResult?.path("score")?.asDouble(0.0) ?: 0.0
        return if (score >= properties.matchThreshold) {
            OpenSanctionsScreeningResult(
                OpenSanctionsScreeningOutcome.POSSIBLE_MATCH,
                requestId,
                BigDecimal.valueOf(score).setScale(4, java.math.RoundingMode.HALF_UP),
                firstResult?.path("id")?.asText()?.take(200),
                firstResult?.path("caption")?.asText()?.take(300),
            )
        } else {
            OpenSanctionsScreeningResult(OpenSanctionsScreeningOutcome.NO_MATCH, requestId)
        }
    }
}
