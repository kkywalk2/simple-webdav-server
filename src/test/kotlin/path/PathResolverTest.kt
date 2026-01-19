package me.kkywalk2.path

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Paths
import kotlin.io.path.absolute
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PathResolverTest {

    @Test
    fun `should resolve simple path`() {
        val serverRoot = createTempDirectory("webdav-test")
        val resolver = PathResolver(serverRoot)

        val resolved = resolver.resolve("/folder/file.txt")

        assertTrue(resolved.toString().contains("folder"))
        assertTrue(resolved.toString().contains("file.txt"))
    }

    @Test
    fun `should decode percent-encoded path`() {
        val serverRoot = createTempDirectory("webdav-test")
        val resolver = PathResolver(serverRoot)

        val resolved = resolver.resolve("/folder/file%20name.txt")

        assertTrue(resolved.toString().contains("file name.txt"))
    }

    @Test
    fun `should block path traversal with double dots`() {
        val serverRoot = createTempDirectory("webdav-test")
        val resolver = PathResolver(serverRoot)

        assertThrows<SecurityException> {
            resolver.resolve("/folder/../../etc/passwd")
        }
    }

    @Test
    fun `should block path traversal with encoded double dots`() {
        val serverRoot = createTempDirectory("webdav-test")
        val resolver = PathResolver(serverRoot)

        assertThrows<SecurityException> {
            resolver.resolve("/folder/%2e%2e/%2e%2e/etc/passwd")
        }
    }

    @Test
    fun `should resolve root path`() {
        val serverRoot = createTempDirectory("webdav-test")
        val resolver = PathResolver(serverRoot)

        val resolved = resolver.resolve("/")

        assertEquals(serverRoot.absolute().normalize(), resolved)
    }

    @Test
    fun `should convert file system path to URL path`() {
        val serverRoot = createTempDirectory("webdav-test")
        val resolver = PathResolver(serverRoot)

        val fsPath = serverRoot.resolve("folder/file.txt")
        val urlPath = resolver.toUrlPath(fsPath)

        assertEquals("/folder/file.txt", urlPath)
    }

    @Test
    fun `should reject path outside server root`() {
        val serverRoot = createTempDirectory("webdav-test")
        val resolver = PathResolver(serverRoot)
        val outsidePath = Paths.get("/etc/passwd")

        assertThrows<SecurityException> {
            resolver.toUrlPath(outsidePath)
        }
    }
}
