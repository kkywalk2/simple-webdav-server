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
     * Read a byte range from a file
     */
    fun readFileRange(path: Path, offset: Long, length: Long): ByteArray

    /**
     * Open a file as a streaming InputStream (caller must close)
     */
    fun openFile(path: Path): java.io.InputStream

    /**
     * Open a byte range of a file as a streaming InputStream (caller must close)
     */
    fun openFileRange(path: Path, offset: Long, length: Long): java.io.InputStream

    /**
     * Write file content (atomic operation)
     */
    fun writeFile(path: Path, content: ByteArray)

    /**
     * Write file content from an InputStream (streaming, atomic operation)
     */
    fun writeFileStreaming(path: Path, inputStream: java.io.InputStream)

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
