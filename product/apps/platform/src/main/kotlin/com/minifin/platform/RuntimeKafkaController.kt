package com.minifin.platform

import java.util.Properties
import java.util.concurrent.TimeUnit
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class RuntimeKafkaResponse(
    val service: String,
    val status: String,
    val clusterId: String,
)

@RestController
class RuntimeKafkaController(
    @Value("\${spring.application.name}") private val serviceName: String,
    @Value("\${spring.kafka.bootstrap-servers}") private val bootstrapServers: String,
) {
    @GetMapping("/internal/runtime/kafka")
    fun kafka(): RuntimeKafkaResponse {
        val properties = Properties().apply {
            put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(AdminClientConfig.CLIENT_ID_CONFIG, "$serviceName-runtime-smoke")
            put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000")
        }

        AdminClient.create(properties).use { adminClient ->
            val clusterId = adminClient.describeCluster().clusterId().get(5, TimeUnit.SECONDS)
            return RuntimeKafkaResponse(serviceName, "CONNECTED", clusterId)
        }
    }
}
