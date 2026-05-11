package me.kkywalk2.webdav.handlers

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import me.kkywalk2.auth.AuthorizationService
import me.kkywalk2.auth.Permission
import me.kkywalk2.config.ServerConfig
import me.kkywalk2.path.PathResolver
import me.kkywalk2.storage.FileSystemStorage
import me.kkywalk2.storage.StorageService
import java.nio.file.NoSuchFileException

class GetHandler(
    private val config: ServerConfig,
    private val storage: StorageService = FileSystemStorage()
) {
    private val pathResolver = PathResolver(config.serverRoot)

    suspend fun handle(call: ApplicationCall, urlPath: String) {
        val principal = call.principal<UserIdPrincipal>()
        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized)
            return
        }

        if (!AuthorizationService.hasPermission(principal.name, urlPath, Permission.READ)) {
            call.respond(HttpStatusCode.Forbidden, "No READ permission")
            return
        }

        try {
            val fsPath = pathResolver.resolve(urlPath)

            if (!storage.exists(fsPath)) {
                call.respond(HttpStatusCode.NotFound)
                return
            }

            if (storage.isDirectory(fsPath)) {
                call.respond(HttpStatusCode.Forbidden, "Cannot GET a directory")
                return
            }

            val metadata = storage.getMetadata(fsPath) ?: run {
                call.respond(HttpStatusCode.NotFound)
                return
            }

            val contentType = guessContentType(fsPath.toString())
            val isHead = call.request.local.method == HttpMethod.Head

            call.response.headers.append(HttpHeaders.ETag, metadata.generateETag())
            call.response.headers.append(HttpHeaders.LastModified,
                java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                    .format(metadata.lastModified.atZone(java.time.ZoneOffset.UTC)))
            call.response.headers.append(HttpHeaders.ContentType, contentType)
            call.response.headers.append(HttpHeaders.AcceptRanges, "bytes")

            val rangeHeader = call.request.headers[HttpHeaders.Range]
            if (rangeHeader != null) {
                val range = parseRange(rangeHeader, metadata.size)
                if (range == null || range.start >= metadata.size) {
                    call.response.headers.append(HttpHeaders.ContentRange, "bytes */${metadata.size}")
                    call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
                    return
                }
                val length = range.end - range.start + 1
                call.response.headers.append(HttpHeaders.ContentRange,
                    "bytes ${range.start}-${range.end}/${metadata.size}")
                call.response.headers.append(HttpHeaders.ContentLength, length.toString())

                if (isHead) {
                    call.respond(HttpStatusCode.PartialContent)
                    return
                }
                val content = storage.readFileRange(fsPath, range.start, length)
                call.respondBytes(content, ContentType.parse(contentType), HttpStatusCode.PartialContent)
                return
            }

            call.response.headers.append(HttpHeaders.ContentLength, metadata.size.toString())
            if (isHead) {
                call.respond(HttpStatusCode.OK)
                return
            }
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

    private data class RangeRequest(val start: Long, val end: Long)

    private fun parseRange(header: String, fileSize: Long): RangeRequest? {
        if (!header.startsWith("bytes=")) return null
        val spec = header.removePrefix("bytes=")
        return when {
            spec.startsWith("-") -> {
                // suffix: bytes=-N  →  last N bytes
                val n = spec.substring(1).toLongOrNull() ?: return null
                RangeRequest(maxOf(0L, fileSize - n), fileSize - 1)
            }
            spec.endsWith("-") -> {
                // open-ended: bytes=N-
                val start = spec.dropLast(1).toLongOrNull() ?: return null
                RangeRequest(start, fileSize - 1)
            }
            else -> {
                // bytes=N-M
                val parts = spec.split("-")
                if (parts.size != 2) return null
                val start = parts[0].toLongOrNull() ?: return null
                val end = parts[1].toLongOrNull() ?: return null
                if (end < start) return null
                RangeRequest(start, minOf(end, fileSize - 1))
            }
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
