package com.minifin.platform.chargeback

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import com.minifin.platform.merchant.dashboard.MerchantDashboardException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

@Service
class ChargebackEvidenceObjectStorage(
    @Value("\${minifin.object-storage.endpoint:http://seaweedfs:8333}") endpoint: String,
    @Value("\${minifin.chargeback.evidence-bucket:chargeback-evidence}") private val bucket: String,
) {
    private val baseUri = endpoint.trimEnd('/')
    private val client = HttpClient.newBuilder().build()

    fun putEvidenceObject(storageKey: String, contentType: String, bytes: ByteArray) {
        ensureValidStorageKey(storageKey)
        ensureBucket()
        val request = HttpRequest.newBuilder(objectUri(storageKey))
            .header("Content-Type", contentType)
            .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.discarding())
        if (response.statusCode() !in 200..299) {
            throw MerchantDashboardException(
                "object_storage_write_failed",
                "Evidence attachment could not be written to object storage.",
                HttpStatus.BAD_GATEWAY,
                "attachments",
            )
        }
    }

    private fun ensureBucket() {
        val request = HttpRequest.newBuilder(URI.create("$baseUri/${encodeSegment(bucket)}"))
            .PUT(HttpRequest.BodyPublishers.noBody())
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.discarding())
        if (response.statusCode() !in setOf(200, 201, 204, 409)) {
            throw MerchantDashboardException(
                "object_storage_bucket_unavailable",
                "Evidence object storage bucket is unavailable.",
                HttpStatus.BAD_GATEWAY,
                "attachments",
            )
        }
    }

    private fun objectUri(storageKey: String): URI =
        URI.create("$baseUri/${encodeSegment(bucket)}/${storageKey.split('/').joinToString("/") { encodeSegment(it) }}")

    private fun ensureValidStorageKey(storageKey: String) {
        if (
            storageKey.startsWith("/") ||
            storageKey.contains("..") ||
            storageKey.any { it.isWhitespace() } ||
            storageKey.split('/').any { it.isBlank() }
        ) {
            throw MerchantDashboardException("invalid_attachment", "Attachment storage key is invalid.", HttpStatus.BAD_REQUEST, "attachments.storageKey")
        }
    }

    private fun encodeSegment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}
