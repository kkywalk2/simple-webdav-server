package me.kkywalk2.db.repositories

import me.kkywalk2.db.tables.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

/**
 * User repository
 */
object UserRepository {

    data class User(
        val username: String,
        val password: String,
        val displayName: String,
        val enabled: Boolean,
        val isAdmin: Boolean = false,
        val createdAt: LocalDateTime? = null,
        val lastLoginAt: LocalDateTime? = null
    )

    /**
     * Find user by username
     */
    fun findByUsername(username: String): User? = transaction {
        Users.selectAll().where { Users.username eq username }
            .map { toUser(it) }
            .firstOrNull()
    }

    /**
     * Get all users
     */
    fun findAll(): List<User> = transaction {
        Users.selectAll()
            .orderBy(Users.username, SortOrder.ASC)
            .map { toUser(it) }
    }

    /**
     * Create a new user
     */
    fun create(
        username: String,
        password: String,
        displayName: String,
        isAdmin: Boolean = false
    ): User = transaction {
        val now = LocalDateTime.now()
        Users.insert {
            it[Users.username] = username
            it[Users.password] = password
            it[Users.displayName] = displayName
            it[Users.enabled] = true
            it[Users.isAdmin] = isAdmin
            it[Users.createdAt] = now
        }
        User(
            username = username,
            password = password,
            displayName = displayName,
            enabled = true,
            isAdmin = isAdmin,
            createdAt = now,
            lastLoginAt = null
        )
    }

    /**
     * Update user details
     */
    fun update(
        username: String,
        displayName: String? = null,
        isAdmin: Boolean? = null,
        enabled: Boolean? = null
    ): Boolean = transaction {
        val updated = Users.update({ Users.username eq username }) {
            displayName?.let { name -> it[Users.displayName] = name }
            isAdmin?.let { admin -> it[Users.isAdmin] = admin }
            enabled?.let { en -> it[Users.enabled] = en }
        }
        updated > 0
    }

    /**
     * Update password
     */
    fun updatePassword(username: String, newPassword: String): Boolean = transaction {
        val updated = Users.update({ Users.username eq username }) {
            it[password] = newPassword
        }
        updated > 0
    }

    /**
     * Delete user
     */
    fun delete(username: String): Boolean = transaction {
        Users.deleteWhere { Users.username eq username } > 0
    }

    /**
     * Check if user is admin
     */
    fun isAdmin(username: String): Boolean = transaction {
        Users.selectAll().where { Users.username eq username }
            .map { it[Users.isAdmin] }
            .firstOrNull() ?: false
    }

    /**
     * Update last login time
     */
    fun updateLastLogin(username: String): Unit = transaction {
        Users.update({ Users.username eq username }) {
            it[lastLoginAt] = LocalDateTime.now()
        }
    }

    /**
     * Check if username exists
     */
    fun exists(username: String): Boolean = transaction {
        Users.selectAll().where { Users.username eq username }.count() > 0
    }

    /**
     * Get user count
     */
    fun count(): Long = transaction {
        Users.selectAll().count()
    }

    /**
     * Get admin count
     */
    fun countAdmins(): Long = transaction {
        Users.selectAll().where { Users.isAdmin eq true }.count()
    }

    /**
     * Get enabled user count
     */
    fun countEnabled(): Long = transaction {
        Users.selectAll().where { Users.enabled eq true }.count()
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

        // Update last login time
        updateLastLogin(username)

        return user
    }

    private fun toUser(row: ResultRow): User = User(
        username = row[Users.username],
        password = row[Users.password],
        displayName = row[Users.displayName],
        enabled = row[Users.enabled],
        isAdmin = row[Users.isAdmin],
        createdAt = row[Users.createdAt],
        lastLoginAt = row[Users.lastLoginAt]
    )
}
