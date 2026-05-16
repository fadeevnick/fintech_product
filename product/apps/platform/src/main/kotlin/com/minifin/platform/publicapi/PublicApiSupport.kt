package com.minifin.platform.publicapi

import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.http.HttpStatus

data class PublicApiResponse<T>(
    val data: T? = null,
    val errors: List<PublicApiError> = emptyList(),
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PublicApiError(
    val code: String,
    val message: String,
    val field: String? = null,
    val hint: String? = null,
)

class PublicApiException(
    val code: String,
    override val message: String,
    val status: HttpStatus,
    val field: String? = null,
) : RuntimeException(message)

object PublicApiAttributes {
    const val PRINCIPAL = "minifin.publicapi.principal"
    const val RAW_BODY = "minifin.publicapi.raw_body"
}
