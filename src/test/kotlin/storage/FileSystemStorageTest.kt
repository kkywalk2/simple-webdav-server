package me.kkywalk2.storage

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.NoSuchFileException
import kotlin.io.path.*
import kotlin.test.*

class FileSystemStorageTest {

    private val storage = FileSystemStorage()

    @Test
    fun `should check if file exists`() {
        val tempDir = createTempDirectory("storage-test")
        val file = tempDir.resolve("test.txt")
        file.writeText("content")

        assertTrue(storage.exists(file))
        assertFalse(storage.exists(tempDir.resolve("nonexistent.txt")))

        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `should check if path is directory`() {
        val tempDir = createTempDirectory("storage-test")
        val file = tempDir.resolve("test.txt")
        file.writeText("content")

        assertTrue(storage.isDirectory(tempDir))
        assertFalse(storage.isDirectory(file))

        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `should get metadata`() {
        val tempDir = createTempDirectory("storage-test")
        val file = tempDir.resolve("test.txt")
        file.writeText("hello")

        val metadata = storage.getMetadata(file)

        assertNotNull(metadata)
        assertFalse(metadata.isDirectory)
        assertEquals(5L, metadata.size)
        assertNotNull(metadata.lastModified)

        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `should list directory contents`() {
        val tempDir = createTempDirectory("storage-test")
        tempDir.resolve("file1.txt").writeText("a")
        tempDir.resolve("file2.txt").writeText("b")
        tempDir.resolve("subdir").createDirectory()

        val contents = storage.listDirectory(tempDir)

        assertEquals(3, contents.size)

        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `should read and write file`() {
        val tempDir = createTempDirectory("storage-test")
        val file = tempDir.resolve("test.txt")

        val content = "Hello, WebDAV!".toByteArray()
        storage.writeFile(file, content)

        val read = storage.readFile(file)

        assertEquals(content.contentToString(), read.contentToString())

        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `should fail to write file without parent directory`() {
        val tempDir = createTempDirectory("storage-test")
        val file = tempDir.resolve("nonexistent/test.txt")

        assertThrows<NoSuchFileException> {
            storage.writeFile(file, "content".toByteArray())
        }

        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `should create directory`() {
        val tempDir = createTempDirectory("storage-test")
        val newDir = tempDir.resolve("newdir")

        storage.createDirectory(newDir)

        assertTrue(storage.exists(newDir))
        assertTrue(storage.isDirectory(newDir))

        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `should fail to create directory without parent`() {
        val tempDir = createTempDirectory("storage-test")
        val newDir = tempDir.resolve("nonexistent/newdir")

        assertThrows<NoSuchFileException> {
            storage.createDirectory(newDir)
        }

        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `should fail to create directory that already exists`() {
        val tempDir = createTempDirectory("storage-test")
        val newDir = tempDir.resolve("newdir")
        newDir.createDirectory()

        assertThrows<FileAlreadyExistsException> {
            storage.createDirectory(newDir)
        }

        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `should delete file`() {
        val tempDir = createTempDirectory("storage-test")
        val file = tempDir.resolve("test.txt")
        file.writeText("content")

        storage.deleteFile(file)

        assertFalse(storage.exists(file))

        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `should delete empty directory`() {
        val tempDir = createTempDirectory("storage-test")
        val subDir = tempDir.resolve("subdir")
        subDir.createDirectory()

        storage.deleteDirectory(subDir)

        assertFalse(storage.exists(subDir))

        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `should fail to delete non-empty directory`() {
        val tempDir = createTempDirectory("storage-test")
        val subDir = tempDir.resolve("subdir")
        subDir.createDirectory()
        subDir.resolve("file.txt").writeText("content")

        assertThrows<DirectoryNotEmptyException> {
            storage.deleteDirectory(subDir)
        }

        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `should check if directory is empty`() {
        val tempDir = createTempDirectory("storage-test")
        val emptyDir = tempDir.resolve("empty")
        val nonEmptyDir = tempDir.resolve("nonempty")
        emptyDir.createDirectory()
        nonEmptyDir.createDirectory()
        nonEmptyDir.resolve("file.txt").writeText("content")

        assertTrue(storage.isDirectoryEmpty(emptyDir))
        assertFalse(storage.isDirectoryEmpty(nonEmptyDir))

        tempDir.toFile().deleteRecursively()
    }
}
