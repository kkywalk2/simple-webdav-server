package me.kkywalk2.db.repositories

import me.kkywalk2.db.tables.PermissionRules
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Permission repository
 */
object PermissionRepository {

    data class PermissionRule(
        val username: String,
        val path: String,
        val canList: Boolean,
        val canRead: Boolean,
        val canWrite: Boolean,
        val canDelete: Boolean,
        val canMkcol: Boolean,
        val deny: Boolean
    )

    /**
     * Get all permission rules for a user
     */
    fun getPermissions(username: String): List<PermissionRule> = transaction {
        PermissionRules.selectAll().where { PermissionRules.username eq username }
            .map {
                PermissionRule(
                    username = it[PermissionRules.username],
                    path = it[PermissionRules.path],
                    canList = it[PermissionRules.canList],
                    canRead = it[PermissionRules.canRead],
                    canWrite = it[PermissionRules.canWrite],
                    canDelete = it[PermissionRules.canDelete],
                    canMkcol = it[PermissionRules.canMkcol],
                    deny = it[PermissionRules.deny]
                )
            }
    }

    /**
     * Find the most specific permission rule for a path
     * More specific paths take precedence
     */
    fun findMostSpecificRule(username: String, requestPath: String): PermissionRule? {
        val rules = getPermissions(username)

        // Normalize path
        val normalized = requestPath.trim().removeSuffix("/").ifEmpty { "/" }

        // Find matching rules (path must be a prefix of requestPath)
        val matchingRules = rules.filter { rule ->
            val rulePath = rule.path.trim().removeSuffix("/").ifEmpty { "/" }
            normalized.startsWith(rulePath) || rulePath == "/"
        }

        // Sort by path length (most specific first)
        return matchingRules.maxByOrNull { it.path.length }
    }
}
