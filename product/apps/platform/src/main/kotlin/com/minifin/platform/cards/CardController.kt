package com.minifin.platform.cards

import com.minifin.platform.controls.ActorControlException
import com.minifin.platform.identity.ApiError
import com.minifin.platform.identity.ApiResponse
import com.minifin.platform.identity.IdentityException
import com.minifin.platform.identity.IdentityService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

private const val SESSION_COOKIE = "MFP_SESSION"

@RestController
class CardController(private val identityService: IdentityService, private val cardService: CardService) {
    @PostMapping("/api/v1/cards")
    fun issueCard(@CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?): ResponseEntity<ApiResponse<CardIssueResponse>> {
        val user = identityService.currentUser(sessionToken)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse(data = cardService.issueCard(user)))
    }

    @ExceptionHandler(CardException::class)
    fun handleCardException(exception: CardException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status).body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message))))

    @ExceptionHandler(IdentityException::class)
    fun handleIdentityException(exception: IdentityException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status).body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message, exception.field))))

    @ExceptionHandler(ActorControlException::class)
    fun handleActorControlException(exception: ActorControlException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status).body(ApiResponse(errors = listOf(ApiError(exception.code, exception.message))))
}
