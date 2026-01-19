package me.kkywalk2.webdav.handlers

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import me.kkywalk2.config.ServerConfig
import me.kkywalk2.path.PathResolver
import me.kkywalk2.storage.FileSystemStorage
import me.kkywalk2.storage.StorageService
import java.nio.file.NoSuchFileException

/**
 * GET/HEAD handler for WebDAV
 * Downloads files and returns metadata
 */
class GetHandler(
    private val config: ServerConfig,
    private val storage: StorageService = FileSystemStorage()
) {
    private val pathResolver = PathResolver(config.serverRoot)

    suspend fun handle(call: ApplicationCall, urlPath: String) {
        try {
            // Resolve path
            val fsPath = pathResolver.resolve(urlPath)

            // Check if resource exists
            if (!storage.exists(fsPath)) {
                call.respond(HttpStatusCode.NotFound)
                return
            }

            // If it's a directory, return 403
            if (storage.isDirectory(fsPath)) {
                call.respond(HttpStatusCode.Forbidden, "Cannot GET a directory")
                return
            }

            // Get metadata
            val metadata = storage.getMetadata(fsPath)
            if (metadata == null) {
                call.respond(HttpStatusCode.NotFound)
                return
            }

            // Set headers
            call.response.headers.append("ETag", metadata.generateETag())
            call.response.headers.append("Last-Modified",
                java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                    .format(metadata.lastModified.atZone(java.time.ZoneOffset.UTC)))
            call.response.headers.append("Content-Length", metadata.size.toString())

            // Guess content type based on file extension
            val contentType = guessContentType(fsPath.toString())
            call.response.headers.append("Content-Type", contentType)

            // For HEAD requests, don't send body
            if (call.request.local.method == HttpMethod.Head) {
                call.respond(HttpStatusCode.OK)
                return
            }

            // Read and send file
            val content = storage.readFile(fsPath)
            call.respondBytes(content, ContentType.parse(contentType))

        } catch (e: SecurityException) {
            call.respond(HttpStatusCode.Forbidden, e.message ?: "Access denied")
        } catch (e: NoSuchFileException) {
            call.respond(HttpStatusCode.NotFound)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, e.message ?: "Internal error")
        }
    }

    private fun guessContentType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "txt" -> "text/plain"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "zip" -> "application/zip"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            else -> "application/octet-stream"
        }
    }
}
