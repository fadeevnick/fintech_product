package com.minifin.platform.identity

import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

data class EndUserRecord(
    val id: UUID,
    val email: String,
    val normalizedEmail: String,
    val passwordHash: String,
    val status: String,
)

data class VerificationRecord(
    val id: UUID,
    val endUserId: UUID,
    val expiresAt: OffsetDateTime,
    val consumedAt: OffsetDateTime?,
)

data class MerchantRecord(
    val id: UUID,
    val companyName: String,
    val country: String,
    val businessType: String,
    val kybStatus: String,
)

data class MerchantEmployeeRecord(
    val id: UUID,
    val merchantId: UUID,
    val email: String,
    val normalizedEmail: String,
    val passwordHash: String,
    val role: String,
    val status: String,
    val merchantStatus: String,
)

data class MerchantVerificationRecord(
    val id: UUID,
    val merchantEmployeeId: UUID,
    val expiresAt: OffsetDateTime,
    val consumedAt: OffsetDateTime?,
)

data class SessionRecord(
    val id: UUID,
    val actorType: String,
    val actorId: UUID,
    val expiresAt: OffsetDateTime,
    val revokedAt: OffsetDateTime?,
)

@Repository
class IdentityRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun insertEndUser(id: UUID, email: String, normalizedEmail: String, passwordHash: String) {
        jdbcTemplate.update(
            """
            insert into identity.end_users (id, email, normalized_email, password_hash, status)
            values (?, ?, ?, ?, 'EMAIL_UNVERIFIED')
            """.trimIndent(),
            id,
            email,
            normalizedEmail,
            passwordHash,
        )
    }

    fun reserveEmail(normalizedEmail: String, pool: String, ownerId: UUID) {
        try {
            jdbcTemplate.update(
                """
                insert into identity.email_reservations (normalized_email, pool, owner_id)
                values (?, ?, ?)
                """.trimIndent(),
                normalizedEmail,
                pool,
                ownerId,
            )
        } catch (_: DuplicateKeyException) {
            throw IdentityException(
                code = "email_already_registered",
                message = "Email is already registered.",
                field = "email",
                status = org.springframework.http.HttpStatus.CONFLICT,
            )
        }
    }

    fun insertEmailVerification(id: UUID, endUserId: UUID, tokenHash: String, expiresAt: OffsetDateTime) {
        jdbcTemplate.update(
            """
            insert into identity.email_verifications (id, end_user_id, token_hash, expires_at)
            values (?, ?, ?, ?)
            """.trimIndent(),
            id,
            endUserId,
            tokenHash,
            expiresAt,
        )
    }

    fun insertMerchant(id: UUID, companyName: String, country: String, businessType: String) {
        jdbcTemplate.update(
            """
            insert into merchant.merchants (id, company_name, country, business_type, kyb_status)
            values (?, ?, ?, ?, 'NOT_STARTED')
            """.trimIndent(),
            id,
            companyName,
            country,
            businessType,
        )
    }

    fun insertMerchantEmployee(
        id: UUID,
        merchantId: UUID,
        email: String,
        normalizedEmail: String,
        passwordHash: String,
        role: String,
    ) {
        jdbcTemplate.update(
            """
            insert into identity.merchant_employees (
                id,
                merchant_id,
                email,
                normalized_email,
                password_hash,
                role,
                status
            )
            values (?, ?, ?, ?, ?, ?, 'EMAIL_UNVERIFIED')
            """.trimIndent(),
            id,
            merchantId,
            email,
            normalizedEmail,
            passwordHash,
            role,
        )
    }

    fun insertMerchantEmailVerification(
        id: UUID,
        merchantEmployeeId: UUID,
        tokenHash: String,
        expiresAt: OffsetDateTime,
    ) {
        jdbcTemplate.update(
            """
            insert into identity.merchant_email_verifications (id, merchant_employee_id, token_hash, expires_at)
            values (?, ?, ?, ?)
            """.trimIndent(),
            id,
            merchantEmployeeId,
            tokenHash,
            expiresAt,
        )
    }

    fun findEndUserByNormalizedEmail(normalizedEmail: String): EndUserRecord? =
        jdbcTemplate.query(
            """
            select id, email, normalized_email, password_hash, status
            from identity.end_users
            where normalized_email = ?
            """.trimIndent(),
            { rs, _ -> rs.toEndUserRecord() },
            normalizedEmail,
        ).firstOrNull()

    fun findEndUserById(id: UUID): EndUserRecord? =
        jdbcTemplate.query(
            """
            select id, email, normalized_email, password_hash, status
            from identity.end_users
            where id = ?
            """.trimIndent(),
            { rs, _ -> rs.toEndUserRecord() },
            id,
        ).firstOrNull()

    fun findMerchantEmployeeByNormalizedEmail(normalizedEmail: String): MerchantEmployeeRecord? =
        jdbcTemplate.query(
            """
            select me.id,
                   me.merchant_id,
                   me.email,
                   me.normalized_email,
                   me.password_hash,
                   me.role,
                   me.status,
                   m.kyb_status as merchant_status
            from identity.merchant_employees me
            join merchant.merchants m on m.id = me.merchant_id
            where me.normalized_email = ?
            """.trimIndent(),
            { rs, _ -> rs.toMerchantEmployeeRecord() },
            normalizedEmail,
        ).firstOrNull()

    fun findMerchantEmployeeById(id: UUID): MerchantEmployeeRecord? =
        jdbcTemplate.query(
            """
            select me.id,
                   me.merchant_id,
                   me.email,
                   me.normalized_email,
                   me.password_hash,
                   me.role,
                   me.status,
                   m.kyb_status as merchant_status
            from identity.merchant_employees me
            join merchant.merchants m on m.id = me.merchant_id
            where me.id = ?
            """.trimIndent(),
            { rs, _ -> rs.toMerchantEmployeeRecord() },
            id,
        ).firstOrNull()

    fun findVerificationByTokenHash(tokenHash: String): VerificationRecord? =
        jdbcTemplate.query(
            """
            select id, end_user_id, expires_at, consumed_at
            from identity.email_verifications
            where token_hash = ?
            """.trimIndent(),
            { rs, _ ->
                VerificationRecord(
                    id = rs.getObject("id", UUID::class.java),
                    endUserId = rs.getObject("end_user_id", UUID::class.java),
                    expiresAt = rs.getObject("expires_at", OffsetDateTime::class.java),
                    consumedAt = rs.getObject("consumed_at", OffsetDateTime::class.java),
                )
            },
            tokenHash,
        ).firstOrNull()

    fun findMerchantVerificationByTokenHash(tokenHash: String): MerchantVerificationRecord? =
        jdbcTemplate.query(
            """
            select id, merchant_employee_id, expires_at, consumed_at
            from identity.merchant_email_verifications
            where token_hash = ?
            """.trimIndent(),
            { rs, _ ->
                MerchantVerificationRecord(
                    id = rs.getObject("id", UUID::class.java),
                    merchantEmployeeId = rs.getObject("merchant_employee_id", UUID::class.java),
                    expiresAt = rs.getObject("expires_at", OffsetDateTime::class.java),
                    consumedAt = rs.getObject("consumed_at", OffsetDateTime::class.java),
                )
            },
            tokenHash,
        ).firstOrNull()

    fun markEmailVerified(verificationId: UUID, endUserId: UUID, now: OffsetDateTime) {
        jdbcTemplate.update(
            """
            update identity.email_verifications
            set consumed_at = coalesce(consumed_at, ?)
            where id = ?
            """.trimIndent(),
            now,
            verificationId,
        )
        jdbcTemplate.update(
            """
            update identity.end_users
            set status = 'ACTIVE',
                email_verified_at = coalesce(email_verified_at, ?),
                updated_at = ?,
                version = version + 1
            where id = ?
              and status = 'EMAIL_UNVERIFIED'
            """.trimIndent(),
            now,
            now,
            endUserId,
        )
    }

    fun markMerchantEmailVerified(verificationId: UUID, merchantEmployeeId: UUID, now: OffsetDateTime) {
        jdbcTemplate.update(
            """
            update identity.merchant_email_verifications
            set consumed_at = coalesce(consumed_at, ?)
            where id = ?
            """.trimIndent(),
            now,
            verificationId,
        )
        jdbcTemplate.update(
            """
            update identity.merchant_employees
            set status = 'ACTIVE',
                email_verified_at = coalesce(email_verified_at, ?),
                updated_at = ?,
                version = version + 1
            where id = ?
              and status = 'EMAIL_UNVERIFIED'
            """.trimIndent(),
            now,
            now,
            merchantEmployeeId,
        )
    }

    fun insertSession(id: UUID, sessionTokenHash: String, actorType: String, actorId: UUID, expiresAt: OffsetDateTime) {
        jdbcTemplate.update(
            """
            insert into identity.sessions (id, session_token_hash, actor_type, actor_id, expires_at)
            values (?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            sessionTokenHash,
            actorType,
            actorId,
            expiresAt,
        )
    }

    fun findActiveSessionByTokenHash(sessionTokenHash: String, now: OffsetDateTime): SessionRecord? =
        jdbcTemplate.query(
            """
            select id, actor_type, actor_id, expires_at, revoked_at
            from identity.sessions
            where session_token_hash = ?
              and revoked_at is null
              and expires_at > ?
            """.trimIndent(),
            { rs, _ ->
                SessionRecord(
                    id = rs.getObject("id", UUID::class.java),
                    actorType = rs.getString("actor_type"),
                    actorId = rs.getObject("actor_id", UUID::class.java),
                    expiresAt = rs.getObject("expires_at", OffsetDateTime::class.java),
                    revokedAt = rs.getObject("revoked_at", OffsetDateTime::class.java),
                )
            },
            sessionTokenHash,
            now,
        ).firstOrNull()

    fun touchSession(id: UUID, now: OffsetDateTime) {
        jdbcTemplate.update(
            "update identity.sessions set last_seen_at = ? where id = ?",
            now,
            id,
        )
    }

    fun revokeSession(sessionTokenHash: String, now: OffsetDateTime): Boolean =
        jdbcTemplate.update(
            """
            update identity.sessions
            set revoked_at = ?
            where session_token_hash = ?
              and revoked_at is null
            """.trimIndent(),
            now,
            sessionTokenHash,
        ) > 0

    private fun ResultSet.toEndUserRecord(): EndUserRecord =
        EndUserRecord(
            id = getObject("id", UUID::class.java),
            email = getString("email"),
            normalizedEmail = getString("normalized_email"),
            passwordHash = getString("password_hash"),
            status = getString("status"),
        )

    private fun ResultSet.toMerchantEmployeeRecord(): MerchantEmployeeRecord =
        MerchantEmployeeRecord(
            id = getObject("id", UUID::class.java),
            merchantId = getObject("merchant_id", UUID::class.java),
            email = getString("email"),
            normalizedEmail = getString("normalized_email"),
            passwordHash = getString("password_hash"),
            role = getString("role"),
            status = getString("status"),
            merchantStatus = getString("merchant_status"),
        )
}
