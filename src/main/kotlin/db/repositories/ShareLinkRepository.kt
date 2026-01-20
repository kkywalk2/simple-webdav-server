package me.kkywalk2.db.repositories

import me.kkywalk2.db.tables.ShareLinks
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

/**
 * Share link repository
 */
object ShareLinkRepository {

    data class ShareLink(
        val id: Int,
        val token: String,
        val resourcePath: String,
        val resourceType: String,
        val createdBy: String,
        val createdAt: LocalDateTime,
        val expiresAt: LocalDateTime?,
        val password: String?,
        val maxAccessCount: Int?,
        val accessCount: Int,
        val canRead: Boolean,
        val canWrite: Boolean
    )

    /**
     * Create a new share link
     */
    fun create(
        token: String,
        resourcePath: String,
        resourceType: String,
        createdBy: String,
        expiresAt: LocalDateTime? = null,
        password: String? = null,
        maxAccessCount: Int? = null,
        canRead: Boolean = true,
        canWrite: Boolean = false
    ): ShareLink = transaction {
        val now = LocalDateTime.now()
        val id = ShareLinks.insert {
            it[ShareLinks.token] = token
            it[ShareLinks.resourcePath] = resourcePath
            it[ShareLinks.resourceType] = resourceType
            it[ShareLinks.createdBy] = createdBy
            it[ShareLinks.createdAt] = now
            it[ShareLinks.expiresAt] = expiresAt
            it[ShareLinks.password] = password
            it[ShareLinks.maxAccessCount] = maxAccessCount
            it[ShareLinks.accessCount] = 0
            it[ShareLinks.canRead] = canRead
            it[ShareLinks.canWrite] = canWrite
        } get ShareLinks.id

        ShareLink(
            id = id,
            token = token,
            resourcePath = resourcePath,
            resourceType = resourceType,
            createdBy = createdBy,
            createdAt = now,
            expiresAt = expiresAt,
            password = password,
            maxAccessCount = maxAccessCount,
            accessCount = 0,
            canRead = canRead,
            canWrite = canWrite
        )
    }

    /**
     * Find share link by token
     */
    fun findByToken(token: String): ShareLink? = transaction {
        ShareLinks.selectAll().where { ShareLinks.token eq token }
            .map { toShareLink(it) }
            .firstOrNull()
    }

    /**
     * Find share link by ID
     */
    fun findById(id: Int): ShareLink? = transaction {
        ShareLinks.selectAll().where { ShareLinks.id eq id }
            .map { toShareLink(it) }
            .firstOrNull()
    }

    /**
     * List all share links created by a user
     */
    fun findByUser(username: String): List<ShareLink> = transaction {
        ShareLinks.selectAll().where { ShareLinks.createdBy eq username }
            .orderBy(ShareLinks.createdAt, SortOrder.DESC)
            .map { toShareLink(it) }
    }

    /**
     * Increment access count
     */
    fun incrementAccessCount(token: String): Unit = transaction {
        ShareLinks.update({ ShareLinks.token eq token }) {
            with(SqlExpressionBuilder) {
                it[accessCount] = accessCount + 1
            }
        }
    }

    /**
     * Delete share link by ID
     */
    fun deleteById(id: Int): Boolean = transaction {
        ShareLinks.deleteWhere { ShareLinks.id eq id } > 0
    }

    /**
     * Delete share link by token
     */
    fun deleteByToken(token: String): Boolean = transaction {
        ShareLinks.deleteWhere { ShareLinks.token eq token } > 0
    }

    /**
     * Delete expired share links
     */
    fun deleteExpired(): Int = transaction {
        val now = LocalDateTime.now()
        ShareLinks.deleteWhere {
            (ShareLinks.expiresAt.isNotNull()) and (ShareLinks.expiresAt less now)
        }
    }

    /**
     * Find all share links (for admin)
     */
    fun findAll(): List<ShareLink> = transaction {
        ShareLinks.selectAll()
            .orderBy(ShareLinks.createdAt, SortOrder.DESC)
            .map { toShareLink(it) }
    }

    /**
     * Count total share links
     */
    fun count(): Long = transaction {
        ShareLinks.selectAll().count()
    }

    /**
     * Count active (non-expired) share links
     */
    fun countActive(): Long = transaction {
        val now = LocalDateTime.now()
        ShareLinks.selectAll()
            .where {
                (ShareLinks.expiresAt.isNull()) or (ShareLinks.expiresAt greaterEq now)
            }
            .count()
    }

    /**
     * Count expired share links
     */
    fun countExpired(): Long = transaction {
        val now = LocalDateTime.now()
        ShareLinks.selectAll()
            .where {
                (ShareLinks.expiresAt.isNotNull()) and (ShareLinks.expiresAt less now)
            }
            .count()
    }

    private fun toShareLink(row: ResultRow): ShareLink = ShareLink(
        id = row[ShareLinks.id],
        token = row[ShareLinks.token],
        resourcePath = row[ShareLinks.resourcePath],
        resourceType = row[ShareLinks.resourceType],
        createdBy = row[ShareLinks.createdBy],
        createdAt = row[ShareLinks.createdAt],
        expiresAt = row[ShareLinks.expiresAt],
        password = row[ShareLinks.password],
        maxAccessCount = row[ShareLinks.maxAccessCount],
        accessCount = row[ShareLinks.accessCount],
        canRead = row[ShareLinks.canRead],
        canWrite = row[ShareLinks.canWrite]
    )
}
