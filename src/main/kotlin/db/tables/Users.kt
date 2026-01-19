package me.kkywalk2.db.tables

import org.jetbrains.exposed.sql.Table

/**
 * Users table
 */
object Users : Table("users") {
    val username = varchar("username", 50).uniqueIndex()
    val password = varchar("password", 255) // In production, store hashed passwords
    val displayName = varchar("display_name", 100)
    val enabled = bool("enabled").default(true)

    override val primaryKey = PrimaryKey(username)
}
