package me.kkywalk2.db.repositories

import me.kkywalk2.db.tables.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * User repository
 */
object UserRepository {

    data class User(
        val username: String,
        val password: String,
        val displayName: String,
        val enabled: Boolean
    )

    /**
     * Find user by username
     */
    fun findByUsername(username: String): User? = transaction {
        Users.selectAll().where { Users.username eq username }
            .map {
                User(
                    username = it[Users.username],
                    password = it[Users.password],
                    displayName = it[Users.displayName],
                    enabled = it[Users.enabled]
                )
            }
            .firstOrNull()
    }

    /**
     * Authenticate user
     * Note: In production, passwords should be hashed (BCrypt, Argon2, etc.)
     */
    fun authenticate(username: String, password: String): User? {
        val user = findByUsername(username) ?: return null

        if (!user.enabled) {
            return null
        }

        // Simple password comparison (should use hash comparison in production)
        if (user.password != password) {
            return null
        }

        return user
    }
}
