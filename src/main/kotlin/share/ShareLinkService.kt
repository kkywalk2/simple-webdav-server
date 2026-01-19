package me.kkywalk2.share

import me.kkywalk2.db.repositories.ShareLinkRepository
import me.kkywalk2.db.repositories.ShareLinkRepository.ShareLink
import java.security.SecureRandom
import java.time.LocalDateTime

/**
 * Share link service
 *
 * Handles business logic for share link operations.
 */
object ShareLinkService {

    private const val TOKEN_LENGTH = 32
    private val CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val secureRandom = SecureRandom()

    enum class ResourceType {
        FILE, FOLDER
    }

    sealed class ValidationResult {
        data class Valid(val shareLink: ShareLink) : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    /**
     * Generate a secure random token
     */
    fun generateToken(): String {
        return (1..TOKEN_LENGTH)
            .map { CHARS[secureRandom.nextInt(CHARS.length)] }
            .joinToString("")
    }

    /**
     * Create a new share link
     */
    fun createShareLink(
        resourcePath: String,
        resourceType: ResourceType,
        createdBy: String,
        expiresInHours: Long? = null,
        password: String? = null,
        maxAccessCount: Int? = null,
        canRead: Boolean = true,
        canWrite: Boolean = false
    ): ShareLink {
        val token = generateToken()
        val expiresAt = expiresInHours?.let {
            LocalDateTime.now().plusHours(it)
        }

        return ShareLinkRepository.create(
            token = token,
            resourcePath = resourcePath,
            resourceType = resourceType.name,
            createdBy = createdBy,
            expiresAt = expiresAt,
            password = password,
            maxAccessCount = maxAccessCount,
            canRead = canRead,
            canWrite = canWrite
        )
    }

    /**
     * Validate a share link token
     */
    fun validateToken(token: String, providedPassword: String? = null): ValidationResult {
        val shareLink = ShareLinkRepository.findByToken(token)
            ?: return ValidationResult.Invalid("Share link not found")

        // Check expiration
        shareLink.expiresAt?.let { expiresAt ->
            if (LocalDateTime.now().isAfter(expiresAt)) {
                return ValidationResult.Invalid("Share link has expired")
            }
        }

        // Check access count limit
        shareLink.maxAccessCount?.let { maxCount ->
            if (shareLink.accessCount >= maxCount) {
                return ValidationResult.Invalid("Access limit reached")
            }
        }

        // Check password
        shareLink.password?.let { storedPassword ->
            if (providedPassword != storedPassword) {
                return ValidationResult.Invalid("Invalid password")
            }
        }

        return ValidationResult.Valid(shareLink)
    }

    /**
     * Record access and increment counter
     */
    fun recordAccess(token: String) {
        ShareLinkRepository.incrementAccessCount(token)
    }

    /**
     * Get share link by ID (for management)
     */
    fun getById(id: Int): ShareLink? {
        return ShareLinkRepository.findById(id)
    }

    /**
     * Get share link by token
     */
    fun getByToken(token: String): ShareLink? {
        return ShareLinkRepository.findByToken(token)
    }

    /**
     * List all share links for a user
     */
    fun listByUser(username: String): List<ShareLink> {
        return ShareLinkRepository.findByUser(username)
    }

    /**
     * Delete a share link
     */
    fun deleteById(id: Int): Boolean {
        return ShareLinkRepository.deleteById(id)
    }

    /**
     * Cleanup expired share links
     */
    fun cleanupExpired(): Int {
        return ShareLinkRepository.deleteExpired()
    }
}
