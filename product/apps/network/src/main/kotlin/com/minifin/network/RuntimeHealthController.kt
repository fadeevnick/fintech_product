package com.minifin.network

import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class RuntimeHealthResponse(
    val service: String,
    val status: String,
)

@RestController
class RuntimeHealthController(
    private val jdbcTemplate: JdbcTemplate,
    @Value("\${spring.application.name}") private val serviceName: String,
) {
    @GetMapping("/internal/health")
    fun health(): RuntimeHealthResponse = RuntimeHealthResponse(serviceName, "UP")

    @GetMapping("/internal/ready")
    fun ready(): RuntimeHealthResponse {
        jdbcTemplate.queryForObject("select 1", Int::class.java)
        return RuntimeHealthResponse(serviceName, "READY")
    }
}
