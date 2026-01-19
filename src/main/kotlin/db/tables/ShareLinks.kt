package me.kkywalk2.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Share links table
 *
 * Stores temporary share links for files and folders.
 * Allows anonymous access to resources without authentication.
 */
object ShareLinks : Table("share_links") {
    val id = integer("id").autoIncrement()
    val token = varchar("token", 64).uniqueIndex() // Secure random token
    val resourcePath = varchar("resource_path", 500) // Path to shared file/folder
    val resourceType = varchar("resource_type", 10) // FILE or FOLDER
    val createdBy = varchar("created_by", 50).references(Users.username)
    val createdAt = datetime("created_at")
    val expiresAt = datetime("expires_at").nullable() // Null = never expires
    val password = varchar("password", 255).nullable() // Optional password protection
    val maxAccessCount = integer("max_access_count").nullable() // Null = unlimited
    val accessCount = integer("access_count").default(0)
    val canRead = bool("can_read").default(true) // Read/download permission
    val canWrite = bool("can_write").default(false) // Write/upload permission (for folders)

    override val primaryKey = PrimaryKey(id)
}
