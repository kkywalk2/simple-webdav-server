package me.kkywalk2.storage

import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import kotlin.io.path.*

/**
 * File system based storage implementation
 */
class FileSystemStorage : StorageService {

    override fun exists(path: Path): Boolean {
        return path.exists()
    }

    override fun isDirectory(path: Path): Boolean {
        return path.isDirectory()
    }

    override fun isFile(path: Path): Boolean {
        return path.isRegularFile()
    }

    override fun getMetadata(path: Path): ResourceMetadata? {
        if (!exists(path)) {
            return null
        }

        return try {
            val attrs = Files.readAttributes(path, BasicFileAttributes::class.java)
            ResourceMetadata(
                path = path,
                isDirectory = attrs.isDirectory,
                size = if (attrs.isDirectory) 0L else attrs.size(),
                lastModified = attrs.lastModifiedTime().toInstant(),
                creationTime = attrs.creationTime().toInstant()
            )
        } catch (e: IOException) {
            null
        }
    }

    override fun listDirectory(path: Path): List<Path> {
        if (!isDirectory(path)) {
            return emptyList()
        }

        return try {
            Files.list(path).use { stream ->
                stream.toList()
            }
        } catch (e: IOException) {
            emptyList()
        }
    }

    override fun readFile(path: Path): ByteArray {
        if (!isFile(path)) {
            throw NoSuchFileException(path.toString())
        }
        return path.readBytes()
    }

    override fun writeFile(path: Path, content: ByteArray) {
        // Atomic write: write to temp file, then move
        val parent = path.parent
        if (parent != null && !exists(parent)) {
            throw NoSuchFileException(parent.toString(), null, "Parent directory does not exist")
        }

        val tempFile = Files.createTempFile(parent, ".webdav-upload-", ".tmp")
        try {
            tempFile.writeBytes(content)
            Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            // Clean up temp file if something went wrong
            try {
                Files.deleteIfExists(tempFile)
            } catch (cleanupException: Exception) {
                // Ignore cleanup errors
            }
            throw e
        }
    }

    override fun deleteFile(path: Path) {
        if (!isFile(path)) {
            throw NoSuchFileException(path.toString())
        }
        Files.delete(path)
    }

    override fun deleteDirectory(path: Path) {
        if (!isDirectory(path)) {
            throw NoSuchFileException(path.toString())
        }

        if (!isDirectoryEmpty(path)) {
            throw DirectoryNotEmptyException(path.toString())
        }

        Files.delete(path)
    }

    override fun createDirectory(path: Path) {
        val parent = path.parent
        if (parent != null && !exists(parent)) {
            throw NoSuchFileException(parent.toString(), null, "Parent directory does not exist")
        }

        if (exists(path)) {
            throw FileAlreadyExistsException(path.toString())
        }

        Files.createDirectory(path)
    }

    override fun isDirectoryEmpty(path: Path): Boolean {
        if (!isDirectory(path)) {
            return false
        }

        return try {
            Files.list(path).use { stream ->
                !stream.findFirst().isPresent
            }
        } catch (e: IOException) {
            false
        }
    }
}
