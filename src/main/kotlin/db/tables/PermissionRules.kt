package me.kkywalk2.db.tables

import org.jetbrains.exposed.sql.Table

/**
 * Permission rules table
 *
 * Each rule defines permissions for a user on a specific path.
 * More specific paths take precedence over general paths.
 */
object PermissionRules : Table("permission_rules") {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 50).references(Users.username)
    val path = varchar("path", 500) // Path pattern (e.g., "/", "/folder1", "/folder1/subfolder")
    val canList = bool("can_list").default(false)
    val canRead = bool("can_read").default(false)
    val canWrite = bool("can_write").default(false)
    val canDelete = bool("can_delete").default(false)
    val canMkcol = bool("can_mkcol").default(false)
    val deny = bool("deny").default(false) // If true, deny access (takes precedence)

    override val primaryKey = PrimaryKey(id)
}
