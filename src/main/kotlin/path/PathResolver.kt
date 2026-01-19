package me.kkywalk2.path

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolute

/**
 * Path resolver with security checks
 *
 * Ensures all paths are:
 * - UTF-8 decoded
 * - Percent-encoded decoded
 * - Normalized (no .. traversal)
 * - Within serverRoot
 */
class PathResolver(private val serverRoot: Path) {

    private val normalizedRoot: Path = serverRoot.normalize().absolute()

    /**
     * Resolve a URL path to a file system path
     *
     * @param urlPath The URL path (e.g., "/folder/file.txt")
     * @return Resolved absolute path
     * @throws SecurityException if path is outside serverRoot
     */
    fun resolve(urlPath: String): Path {
        // Decode percent-encoded characters
        val decodedPath = urlPath.decodeUrl()

        // Remove leading slash and resolve against serverRoot
        val relativePath = decodedPath.removePrefix("/")

        // Resolve and normalize
        val resolvedPath = normalizedRoot.resolve(relativePath).normalize().absolute()

        // Security check: ensure path is within serverRoot
        if (!isWithinRoot(resolvedPath)) {
            throw SecurityException("Path traversal attempt detected: $urlPath")
        }

        return resolvedPath
    }

    /**
     * Convert a file system path back to URL path
     */
    fun toUrlPath(fsPath: Path): String {
        val normalizedPath = fsPath.normalize().absolute()

        if (!isWithinRoot(normalizedPath)) {
            throw SecurityException("Path is outside server root: $fsPath")
        }

        val relativePath = normalizedRoot.relativize(normalizedPath)
        return "/" + relativePath.toString().replace('\\', '/')
    }

    /**
     * Check if a path is within the server root
     */
    private fun isWithinRoot(path: Path): Boolean {
        val normalizedPath = path.normalize().absolute()
        return normalizedPath.startsWith(normalizedRoot)
    }

    /**
     * Decode URL-encoded string
     */
    private fun String.decodeUrl(): String {
        return try {
            URLDecoder.decode(this, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            // If decoding fails, return original string
            this
        }
    }
}
