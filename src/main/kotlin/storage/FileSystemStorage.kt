package me.kkywalk2.storage

import java.io.IOException
import java.io.InputStream
import java.nio.channels.Channels
import java.nio.channels.FileChannel
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

    override fun readFileRange(path: Path, offset: Long, length: Long): ByteArray {
        if (!isFile(path)) {
            throw NoSuchFileException(path.toString())
        }
        java.io.RandomAccessFile(path.toFile(), "r").use { raf ->
            raf.seek(offset)
            val buffer = ByteArray(length.toInt())
            raf.readFully(buffer)
            return buffer
        }
    }

    override fun openFile(path: Path): InputStream {
        if (!isFile(path)) throw NoSuchFileException(path.toString())
        return Files.newInputStream(path)
    }

    override fun openFileRange(path: Path, offset: Long, length: Long): InputStream {
        if (!isFile(path)) throw NoSuchFileException(path.toString())
        val channel = FileChannel.open(path, StandardOpenOption.READ)
        channel.position(offset)
        val inner = Channels.newInputStream(channel)
        return object : InputStream() {
            private var remaining = length
            override fun read(): Int {
                if (remaining <= 0) return -1
                val b = inner.read()
                if (b >= 0) remaining--
                return b
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (remaining <= 0) return -1
                val n = inner.read(b, off, minOf(len.toLong(), remaining).toInt())
                if (n > 0) remaining -= n
                return n
            }
            override fun close() = channel.close()
        }
    }

    override fun writeFile(path: Path, content: ByteArray) {
        val parent = path.parent
        if (parent != null && !exists(parent)) {
            throw NoSuchFileException(parent.toString(), null, "Parent directory does not exist")
        }

        val tempFile = Files.createTempFile(parent, ".webdav-upload-", ".tmp")
        try {
            tempFile.writeBytes(content)
            Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            try { Files.deleteIfExists(tempFile) } catch (_: Exception) {}
            throw e
        }
    }

    override fun writeFileStreaming(path: Path, inputStream: java.io.InputStream) {
        val parent = path.parent
        if (parent != null && !exists(parent)) {
            throw NoSuchFileException(parent.toString(), null, "Parent directory does not exist")
        }

        val tempFile = Files.createTempFile(parent, ".webdav-upload-", ".tmp")
        try {
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING)
            Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            try { Files.deleteIfExists(tempFile) } catch (_: Exception) {}
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
