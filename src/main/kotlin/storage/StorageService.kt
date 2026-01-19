package me.kkywalk2.storage

import java.nio.file.Path
import java.time.Instant

/**
 * Storage service interface for file system operations
 */
interface StorageService {
    /**
     * Check if a resource exists
     */
    fun exists(path: Path): Boolean

    /**
     * Check if a resource is a directory
     */
    fun isDirectory(path: Path): Boolean

    /**
     * Check if a resource is a file
     */
    fun isFile(path: Path): Boolean

    /**
     * Get resource metadata
     */
    fun getMetadata(path: Path): ResourceMetadata?

    /**
     * List directory contents
     */
    fun listDirectory(path: Path): List<Path>

    /**
     * Read file content
     */
    fun readFile(path: Path): ByteArray

    /**
     * Write file content (atomic operation)
     */
    fun writeFile(path: Path, content: ByteArray)

    /**
     * Delete a file
     */
    fun deleteFile(path: Path)

    /**
     * Delete a directory (must be empty)
     */
    fun deleteDirectory(path: Path)

    /**
     * Create a directory
     */
    fun createDirectory(path: Path)

    /**
     * Check if directory is empty
     */
    fun isDirectoryEmpty(path: Path): Boolean
}

/**
 * Resource metadata
 */
data class ResourceMetadata(
    val path: Path,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Instant,
    val creationTime: Instant
) {
    /**
     * Generate ETag based on size and last modified time
     */
    fun generateETag(): String {
        val hash = (size.toString() + lastModified.toEpochMilli()).hashCode()
        return "\"${hash.toString(16)}\""
    }
}
