package com.minifin.platform.identity

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

private const val SESSION_COOKIE = "MFP_SESSION"

@RestController
class IdentityController(
    private val identityService: IdentityService,
) {
    @PostMapping("/api/v1/enduser/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<ApiResponse<RegisterResponse>> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse(data = identityService.register(request)))

    @PostMapping("/api/v1/enduser/email/verify")
    fun verifyEmail(@RequestBody request: VerifyEmailRequest): ApiResponse<VerifyEmailResponse> =
        ApiResponse(data = identityService.verifyEmail(request))

    @PostMapping("/api/v1/enduser/login")
    fun login(
        @RequestBody request: LoginRequest,
        response: HttpServletResponse,
    ): ApiResponse<LoginResponse> {
        val result = identityService.login(request)
        response.addCookie(sessionCookie(result.sessionToken))
        return ApiResponse(
            data = LoginResponse(
                userId = result.user.id.toString(),
                email = result.user.email,
                status = result.user.status,
            ),
        )
    }

    @GetMapping("/api/v1/enduser/me")
    fun me(@CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?): ApiResponse<MeResponse> {
        val user = identityService.currentUser(sessionToken)
        return ApiResponse(data = MeResponse(user.id.toString(), user.email, user.status))
    }

    @PostMapping("/api/v1/enduser/logout")
    fun logout(
        @CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?,
        response: HttpServletResponse,
    ): ApiResponse<LogoutResponse> {
        val loggedOut = identityService.logout(sessionToken)
        response.addCookie(expiredSessionCookie())
        return ApiResponse(data = LogoutResponse(loggedOut))
    }

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

    private fun sessionCookie(token: String): Cookie =
        Cookie(SESSION_COOKIE, token).apply {
            path = "/"
            isHttpOnly = true
            secure = false
            maxAge = 12 * 60 * 60
            setAttribute("SameSite", "Lax")
        }

    private fun expiredSessionCookie(): Cookie =
        Cookie(SESSION_COOKIE, "").apply {
            path = "/"
            isHttpOnly = true
            secure = false
            maxAge = 0
            setAttribute("SameSite", "Lax")
        }
}
