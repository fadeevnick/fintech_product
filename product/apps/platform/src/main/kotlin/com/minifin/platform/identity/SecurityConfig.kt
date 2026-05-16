package com.minifin.platform.identity

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.beans.factory.annotation.Value

@Configuration
class SecurityConfig(
    private val objectMapper: ObjectMapper,
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(12)

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/api/v1/backoffice/**").authenticated()
                it.anyRequest().permitAll()
            }
            .oauth2ResourceServer {
                it.jwt { }
                it.authenticationEntryPoint { _, response, _ ->
                    response.status = HttpServletResponse.SC_UNAUTHORIZED
                    response.contentType = "application/json"
                    objectMapper.writeValue(
                        response.outputStream,
                        ApiResponse<Nothing>(
                            errors = listOf(
                                ApiError(
                                    code = "unauthenticated",
                                    message = "Authentication is required.",
                                ),
                            ),
                        ),
                    )
                }
            }
            .build()

    @Bean
    fun jwtDecoder(@Value("\${minifin.backoffice.oidc.jwk-set-uri}") jwkSetUri: String): JwtDecoder =
        NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build()
}
