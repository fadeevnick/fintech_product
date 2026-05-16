package com.minifin.platform.backoffice

import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

data class BackofficePrincipal(
    val subject: String,
    val subjectUuid: UUID?,
    val email: String?,
    val roles: List<String>,
    val issuer: String,
)

@Component
class BackofficeRoleMapper(
    @Value("\${minifin.backoffice.oidc.client-id}") private val clientId: String,
    @Value("\${minifin.backoffice.oidc.issuer-display}") private val issuerDisplay: String,
) {
    private val approvedRoles = setOf(
        "backoffice_operator",
        "compliance_officer",
        "senior_compliance",
    )

    fun requireBackofficePrincipal(jwt: Jwt): BackofficePrincipal {
        val roles = extractRoles(jwt).filter { it in approvedRoles }.sorted()
        if (roles.isEmpty()) {
            throw BackofficeException(
                code = "forbidden_role",
                message = "Backoffice role is required.",
                status = HttpStatus.FORBIDDEN,
            )
        }

        val subject = jwt.subject ?: ""
        return BackofficePrincipal(
            subject = subject,
            subjectUuid = runCatching { UUID.fromString(subject) }.getOrNull(),
            email = jwt.getClaimAsString("email") ?: jwt.getClaimAsString("preferred_username"),
            roles = roles,
            issuer = jwt.issuer?.toString() ?: issuerDisplay,
        )
    }

    private fun extractRoles(jwt: Jwt): Set<String> {
        val roles = mutableSetOf<String>()
        roles += rolesFromClaimMap(jwt.claims["realm_access"])

        val resourceAccess = jwt.claims["resource_access"] as? Map<*, *> ?: emptyMap<Any, Any>()
        roles += rolesFromClaimMap(resourceAccess[clientId])

        return roles
    }

    private fun rolesFromClaimMap(value: Any?): Set<String> {
        val claimMap = value as? Map<*, *> ?: return emptySet()
        val claimRoles = claimMap["roles"] as? Collection<*> ?: return emptySet()
        return claimRoles.filterIsInstance<String>().toSet()
    }
}
