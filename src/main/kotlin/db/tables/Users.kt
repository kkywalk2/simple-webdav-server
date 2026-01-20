package me.kkywalk2.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Users table
 */
object Users : Table("users") {
    val username = varchar("username", 50).uniqueIndex()
    val password = varchar("password", 255) // In production, store hashed passwords
    val displayName = varchar("display_name", 100)
    val enabled = bool("enabled").default(true)
    val isAdmin = bool("is_admin").default(false)
    val createdAt = datetime("created_at").nullable()
    val lastLoginAt = datetime("last_login_at").nullable()

    override val primaryKey = PrimaryKey(username)
}
