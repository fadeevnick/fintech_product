package com.minifin.platform.identity

import com.fasterxml.jackson.annotation.JsonInclude

data class ApiResponse<T>(
    val data: T? = null,
    val errors: List<ApiError> = emptyList(),
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiError(
    val code: String,
    val message: String,
    val field: String? = null,
    val hint: String? = null,
)

data class RegisterRequest(
    val email: String,
    val password: String,
)

data class MerchantRegisterRequest(
    val email: String,
    val password: String,
    val companyName: String,
    val country: String,
    val businessType: String,
)

data class RegisterResponse(
    val userId: String,
    val email: String,
    val status: String,
    val verificationToken: String? = null,
)

data class MerchantRegisterResponse(
    val merchantId: String,
    val employeeId: String,
    val email: String,
    val role: String,
    val employeeStatus: String,
    val merchantStatus: String,
    val verificationToken: String? = null,
)

data class VerifyEmailRequest(
    val token: String,
)

data class VerifyEmailResponse(
    val userId: String,
    val email: String,
    val status: String,
)

data class MerchantVerifyEmailResponse(
    val merchantId: String,
    val employeeId: String,
    val email: String,
    val role: String,
    val employeeStatus: String,
    val merchantStatus: String,
)

data class LoginRequest(
    val email: String,
    val password: String,
)

data class LoginResponse(
    val userId: String,
    val email: String,
    val status: String,
)

data class MerchantLoginResponse(
    val merchantId: String,
    val employeeId: String,
    val email: String,
    val role: String,
    val employeeStatus: String,
    val merchantStatus: String,
)

data class MeResponse(
    val userId: String,
    val email: String,
    val status: String,
)

data class MerchantMeResponse(
    val merchantId: String,
    val employeeId: String,
    val email: String,
    val role: String,
    val employeeStatus: String,
    val merchantStatus: String,
)

data class LogoutResponse(
    val loggedOut: Boolean,
)

class IdentityException(
    val code: String,
    override val message: String,
    val field: String? = null,
    val status: org.springframework.http.HttpStatus,
) : RuntimeException(message)
