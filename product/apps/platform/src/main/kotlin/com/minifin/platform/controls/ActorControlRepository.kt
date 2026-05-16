package com.minifin.platform.controls

import java.sql.ResultSet
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

data class ActorControlRecord(
    val actorType: String,
    val actorId: UUID,
    val state: String,
    val reasonCode: String,
)

@Repository
class ActorControlRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun upsert(
        actorType: String,
        actorId: UUID,
        state: String,
        reasonCode: String,
        updatedByActorType: String,
        updatedByActorId: UUID?,
        updatedByReference: String?,
    ): ActorControlRecord {
        jdbcTemplate.update(
            """
            insert into identity.actor_controls (
                actor_type,
                actor_id,
                state,
                reason_code,
                updated_by_actor_type,
                updated_by_actor_id,
                updated_by_reference
            )
            values (?, ?, ?, ?, ?, ?, ?)
            on conflict (actor_type, actor_id)
            do update set
                state = excluded.state,
                reason_code = excluded.reason_code,
                updated_by_actor_type = excluded.updated_by_actor_type,
                updated_by_actor_id = excluded.updated_by_actor_id,
                updated_by_reference = excluded.updated_by_reference,
                updated_at = now(),
                version = identity.actor_controls.version + 1
            """.trimIndent(),
            actorType,
            actorId,
            state,
            reasonCode,
            updatedByActorType,
            updatedByActorId,
            updatedByReference,
        )
        return find(actorType, actorId) ?: error("Actor control disappeared after upsert")
    }

    fun find(actorType: String, actorId: UUID): ActorControlRecord? =
        jdbcTemplate.query(
            """
            select actor_type, actor_id, state, reason_code
            from identity.actor_controls
            where actor_type = ?
              and actor_id = ?
            """.trimIndent(),
            { rs, _ -> rs.toActorControlRecord() },
            actorType,
            actorId,
        ).firstOrNull()

    private fun ResultSet.toActorControlRecord(): ActorControlRecord =
        ActorControlRecord(
            actorType = getString("actor_type"),
            actorId = getObject("actor_id", UUID::class.java),
            state = getString("state"),
            reasonCode = getString("reason_code"),
        )
}
