package me.kkywalk2.db.repositories

import me.kkywalk2.db.tables.PermissionRules
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Permission repository
 */
object PermissionRepository {

    data class PermissionRule(
        val id: Int,
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
     * Get all permission rules
     */
    fun findAll(): List<PermissionRule> = transaction {
        PermissionRules.selectAll()
            .orderBy(PermissionRules.username, SortOrder.ASC)
            .orderBy(PermissionRules.path, SortOrder.ASC)
            .map { toPermissionRule(it) }
    }

    /**
     * Get permission rule by ID
     */
    fun findById(id: Int): PermissionRule? = transaction {
        PermissionRules.selectAll().where { PermissionRules.id eq id }
            .map { toPermissionRule(it) }
            .firstOrNull()
    }

    /**
     * Get all permission rules for a user
     */
    fun getPermissions(username: String): List<PermissionRule> = transaction {
        PermissionRules.selectAll().where { PermissionRules.username eq username }
            .orderBy(PermissionRules.path, SortOrder.ASC)
            .map { toPermissionRule(it) }
    }

    /**
     * Create a new permission rule
     */
    fun create(
        username: String,
        path: String,
        canList: Boolean = false,
        canRead: Boolean = false,
        canWrite: Boolean = false,
        canDelete: Boolean = false,
        canMkcol: Boolean = false,
        deny: Boolean = false
    ): PermissionRule = transaction {
        val id = PermissionRules.insert {
            it[PermissionRules.username] = username
            it[PermissionRules.path] = path
            it[PermissionRules.canList] = canList
            it[PermissionRules.canRead] = canRead
            it[PermissionRules.canWrite] = canWrite
            it[PermissionRules.canDelete] = canDelete
            it[PermissionRules.canMkcol] = canMkcol
            it[PermissionRules.deny] = deny
        } get PermissionRules.id

        PermissionRule(
            id = id,
            username = username,
            path = path,
            canList = canList,
            canRead = canRead,
            canWrite = canWrite,
            canDelete = canDelete,
            canMkcol = canMkcol,
            deny = deny
        )
    }

    /**
     * Update a permission rule
     */
    fun update(
        id: Int,
        canList: Boolean? = null,
        canRead: Boolean? = null,
        canWrite: Boolean? = null,
        canDelete: Boolean? = null,
        canMkcol: Boolean? = null,
        deny: Boolean? = null
    ): Boolean = transaction {
        val updated = PermissionRules.update({ PermissionRules.id eq id }) {
            canList?.let { v -> it[PermissionRules.canList] = v }
            canRead?.let { v -> it[PermissionRules.canRead] = v }
            canWrite?.let { v -> it[PermissionRules.canWrite] = v }
            canDelete?.let { v -> it[PermissionRules.canDelete] = v }
            canMkcol?.let { v -> it[PermissionRules.canMkcol] = v }
            deny?.let { v -> it[PermissionRules.deny] = v }
        }
        updated > 0
    }

    /**
     * Delete a permission rule
     */
    fun delete(id: Int): Boolean = transaction {
        PermissionRules.deleteWhere { PermissionRules.id eq id } > 0
    }

    /**
     * Delete all permission rules for a user
     */
    fun deleteByUsername(username: String): Int = transaction {
        PermissionRules.deleteWhere { PermissionRules.username eq username }
    }

    /**
     * Check if a permission rule exists for user and path
     */
    fun existsByUserAndPath(username: String, path: String): Boolean = transaction {
        PermissionRules.selectAll().where {
            (PermissionRules.username eq username) and (PermissionRules.path eq path)
        }.count() > 0
    }

    /**
     * Get permission rule count for a user
     */
    fun countByUsername(username: String): Long = transaction {
        PermissionRules.selectAll().where { PermissionRules.username eq username }.count()
    }

    /**
     * Get total permission rule count
     */
    fun count(): Long = transaction {
        PermissionRules.selectAll().count()
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

    private fun toPermissionRule(row: ResultRow): PermissionRule = PermissionRule(
        id = row[PermissionRules.id],
        username = row[PermissionRules.username],
        path = row[PermissionRules.path],
        canList = row[PermissionRules.canList],
        canRead = row[PermissionRules.canRead],
        canWrite = row[PermissionRules.canWrite],
        canDelete = row[PermissionRules.canDelete],
        canMkcol = row[PermissionRules.canMkcol],
        deny = row[PermissionRules.deny]
    )
}
