package com.minifin.platform.controls

import org.springframework.http.HttpStatus

data class ActorControlRequest(
    val actorType: String,
    val actorId: String,
    val state: String,
    val reasonCode: String,
)

data class ActorControlResponse(
    val actorType: String,
    val actorId: String,
    val state: String,
    val reasonCode: String,
)

data class WriteGuardProbeResponse(
    val actorType: String,
    val actorId: String,
    val allowed: Boolean,
)

data class ReadAuditProbeResponse(
    val resourceId: String,
    val resourceType: String,
    val readAudited: Boolean,
)

class ActorControlException(
    val code: String,
    override val message: String,
    val status: HttpStatus,
) : RuntimeException(message)
