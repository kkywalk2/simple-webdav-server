package me.kkywalk2.db.repositories

import me.kkywalk2.db.DatabaseFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PermissionRepositoryTest {

    private lateinit var dbFile: java.nio.file.Path

    @BeforeEach
    fun setUp() {
        dbFile = createTempFile("webdav-permission-test-", ".db")
        DatabaseFactory.init(dbPath = dbFile.toString())
    }

    @AfterEach
    fun tearDown() {
        dbFile.deleteIfExists()
    }

    // Regression tests for the NTFS-on-Linux case-folding bypass: some filesystems the
    // storage root can be mounted on (NTFS via ntfs-3g even when the host OS is Linux,
    // macOS APFS by default, etc.) resolve "/private/x" and "/Private/x" to the same
    // physical file, even though the app's own path-matching used to be case-sensitive.
    // That mismatch let a client dodge a more specific `deny` rule just by changing the
    // request URL's case.

    @Test
    fun `deny rule matches a request path that differs only in case`() {
        PermissionRepository.create(username = "user1", path = "/", canRead = true)
        PermissionRepository.create(username = "user1", path = "/private", deny = true)

        val rule = PermissionRepository.findMostSpecificRule("user1", "/Private/secret.txt")

        assertNotNull(rule)
        assertTrue(rule.deny)
    }

    @Test
    fun `most specific rule still wins regardless of case`() {
        PermissionRepository.create(username = "user1", path = "/docs", canRead = true)
        PermissionRepository.create(username = "user1", path = "/docs/private", deny = true)

        val rule = PermissionRepository.findMostSpecificRule("user1", "/DOCS/Private/secret.txt")

        assertNotNull(rule)
        assertTrue(rule.deny)
        assertEqualsIgnoreCase("/docs/private", rule.path)
    }

    @Test
    fun `default deny when no rule matches`() {
        val rule = PermissionRepository.findMostSpecificRule("nobody", "/anything")

        assertNull(rule)
    }

    @Test
    fun `existsByUserAndPath detects case-variant duplicates`() {
        PermissionRepository.create(username = "user1", path = "/private", canRead = true)

        assertTrue(PermissionRepository.existsByUserAndPath("user1", "/Private"))
        assertTrue(PermissionRepository.existsByUserAndPath("user1", "/private"))
        assertFalse(PermissionRepository.existsByUserAndPath("user1", "/other"))
    }

    private fun assertEqualsIgnoreCase(expected: String, actual: String) {
        assertTrue(
            expected.equals(actual, ignoreCase = true),
            "expected \"$expected\" (ignoring case) but was \"$actual\""
        )
    }
}
