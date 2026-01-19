package me.kkywalk2.db

import me.kkywalk2.db.tables.PermissionRules
import me.kkywalk2.db.tables.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

/**
 * Database factory for SQLite initialization
 */
object DatabaseFactory {

    fun init(dbPath: String = "webdav.db") {
        // Connect to SQLite database
        val dbFile = File(dbPath)
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", "org.sqlite.JDBC")

        // Create tables
        transaction {
            SchemaUtils.create(Users, PermissionRules)

            // Create default admin user if no users exist
            val userCount = Users.selectAll().count()
            if (userCount == 0L) {
                Users.insert {
                    it[username] = "admin"
                    it[password] = "admin" // In production, this should be hashed
                    it[displayName] = "Administrator"
                }

                // Grant all permissions to admin for root path
                PermissionRules.insert {
                    it[username] = "admin"
                    it[path] = "/"
                    it[canList] = true
                    it[canRead] = true
                    it[canWrite] = true
                    it[canDelete] = true
                    it[canMkcol] = true
                }
            }
        }
    }
}
