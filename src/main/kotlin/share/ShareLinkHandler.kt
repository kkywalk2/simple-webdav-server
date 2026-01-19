package me.kkywalk2.share

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import me.kkywalk2.config.ServerConfig
import me.kkywalk2.path.PathResolver
import me.kkywalk2.storage.FileSystemStorage
import java.nio.file.Files

/**
 * Handler for share link API operations
 */
class ShareLinkHandler(config: ServerConfig) {

    private val pathResolver = PathResolver(config.serverRoot)
    private val storage = FileSystemStorage()

    @Serializable
    data class CreateShareRequest(
        val path: String,
        val expiresInHours: Long? = null,
        val password: String? = null,
        val maxAccessCount: Int? = null,
        val canWrite: Boolean = false
    )

    @Serializable
    data class ShareLinkResponse(
        val id: Int,
        val token: String,
        val path: String,
        val resourceType: String,
        val url: String,
        val createdAt: String,
        val expiresAt: String?,
        val hasPassword: Boolean,
        val maxAccessCount: Int?,
        val accessCount: Int,
        val canRead: Boolean,
        val canWrite: Boolean
    )

    @Serializable
    data class ErrorResponse(
        val error: String
    )

    /**
     * POST /api/shares - Create a new share link
     */
    suspend fun handleCreate(call: ApplicationCall) {
        val principal = call.principal<UserIdPrincipal>()
        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))
            return
        }

        val request = try {
            call.receive<CreateShareRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            return
        }

        // Validate path
        val fsPath = try {
            pathResolver.resolve(request.path)
        } catch (e: SecurityException) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("Access denied"))
            return
        }

        if (!storage.exists(fsPath)) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Resource not found"))
            return
        }

        val resourceType = if (storage.isDirectory(fsPath)) {
            ShareLinkService.ResourceType.FOLDER
        } else {
            ShareLinkService.ResourceType.FILE
        }

        val shareLink = ShareLinkService.createShareLink(
            resourcePath = request.path,
            resourceType = resourceType,
            createdBy = principal.name,
            expiresInHours = request.expiresInHours,
            password = request.password,
            maxAccessCount = request.maxAccessCount,
            canRead = true,
            canWrite = request.canWrite
        )

        val baseUrl = call.request.origin.let { origin ->
            "${origin.scheme}://${origin.serverHost}:${origin.serverPort}"
        }

        call.respond(
            HttpStatusCode.Created,
            ShareLinkResponse(
                id = shareLink.id,
                token = shareLink.token,
                path = shareLink.resourcePath,
                resourceType = shareLink.resourceType,
                url = "$baseUrl/s/${shareLink.token}",
                createdAt = shareLink.createdAt.toString(),
                expiresAt = shareLink.expiresAt?.toString(),
                hasPassword = shareLink.password != null,
                maxAccessCount = shareLink.maxAccessCount,
                accessCount = shareLink.accessCount,
                canRead = shareLink.canRead,
                canWrite = shareLink.canWrite
            )
        )
    }

    /**
     * GET /api/shares - List all share links for current user
     */
    suspend fun handleList(call: ApplicationCall) {
        val principal = call.principal<UserIdPrincipal>()
        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))
            return
        }

        val baseUrl = call.request.origin.let { origin ->
            "${origin.scheme}://${origin.serverHost}:${origin.serverPort}"
        }

        val shareLinks = ShareLinkService.listByUser(principal.name).map { link ->
            ShareLinkResponse(
                id = link.id,
                token = link.token,
                path = link.resourcePath,
                resourceType = link.resourceType,
                url = "$baseUrl/s/${link.token}",
                createdAt = link.createdAt.toString(),
                expiresAt = link.expiresAt?.toString(),
                hasPassword = link.password != null,
                maxAccessCount = link.maxAccessCount,
                accessCount = link.accessCount,
                canRead = link.canRead,
                canWrite = link.canWrite
            )
        }

        call.respond(shareLinks)
    }

    /**
     * GET /api/shares/{id} - Get share link details
     */
    suspend fun handleGet(call: ApplicationCall, id: Int) {
        val principal = call.principal<UserIdPrincipal>()
        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))
            return
        }

        val shareLink = ShareLinkService.getById(id)
        if (shareLink == null || shareLink.createdBy != principal.name) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Share link not found"))
            return
        }

        val baseUrl = call.request.origin.let { origin ->
            "${origin.scheme}://${origin.serverHost}:${origin.serverPort}"
        }

        call.respond(
            ShareLinkResponse(
                id = shareLink.id,
                token = shareLink.token,
                path = shareLink.resourcePath,
                resourceType = shareLink.resourceType,
                url = "$baseUrl/s/${shareLink.token}",
                createdAt = shareLink.createdAt.toString(),
                expiresAt = shareLink.expiresAt?.toString(),
                hasPassword = shareLink.password != null,
                maxAccessCount = shareLink.maxAccessCount,
                accessCount = shareLink.accessCount,
                canRead = shareLink.canRead,
                canWrite = shareLink.canWrite
            )
        )
    }

    /**
     * DELETE /api/shares/{id} - Delete a share link
     */
    suspend fun handleDelete(call: ApplicationCall, id: Int) {
        val principal = call.principal<UserIdPrincipal>()
        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))
            return
        }

        val shareLink = ShareLinkService.getById(id)
        if (shareLink == null || shareLink.createdBy != principal.name) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Share link not found"))
            return
        }

        ShareLinkService.deleteById(id)
        call.respond(HttpStatusCode.NoContent)
    }

    /**
     * GET /s/{token} - Access shared resource (public, no auth required)
     */
    suspend fun handleAccess(call: ApplicationCall, token: String) {
        val password = call.request.queryParameters["password"]

        when (val result = ShareLinkService.validateToken(token, password)) {
            is ShareLinkService.ValidationResult.Invalid -> {
                if (result.reason == "Invalid password") {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse(result.reason))
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(result.reason))
                }
                return
            }
            is ShareLinkService.ValidationResult.Valid -> {
                val shareLink = result.shareLink

                // Record access
                ShareLinkService.recordAccess(token)

                // Resolve path
                val fsPath = try {
                    pathResolver.resolve(shareLink.resourcePath)
                } catch (e: SecurityException) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("Access denied"))
                    return
                }

                if (!storage.exists(fsPath)) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Resource no longer exists"))
                    return
                }

                if (shareLink.resourceType == "FILE") {
                    // Serve file
                    serveFile(call, fsPath)
                } else {
                    // Serve folder listing as HTML
                    serveFolderListing(call, fsPath, shareLink.resourcePath, token)
                }
            }
        }
    }

    private suspend fun serveFile(call: ApplicationCall, fsPath: java.nio.file.Path) {
        val contentType = Files.probeContentType(fsPath) ?: "application/octet-stream"
        val fileName = fsPath.fileName.toString()
        val fileBytes = storage.readFile(fsPath)

        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(
                ContentDisposition.Parameters.FileName, fileName
            ).toString()
        )
        call.respondBytes(fileBytes, ContentType.parse(contentType))
    }

    private suspend fun serveFolderListing(
        call: ApplicationCall,
        fsPath: java.nio.file.Path,
        resourcePath: String,
        token: String
    ) {
        val entries = storage.listDirectory(fsPath)

        val html = buildString {
            append("<!DOCTYPE html>")
            append("<html><head>")
            append("<meta charset=\"UTF-8\">")
            append("<title>Shared Folder: ${fsPath.fileName}</title>")
            append("<style>")
            append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 800px; margin: 0 auto; padding: 20px; }")
            append("h1 { color: #333; }")
            append("ul { list-style: none; padding: 0; }")
            append("li { padding: 10px; border-bottom: 1px solid #eee; }")
            append("li:hover { background: #f5f5f5; }")
            append("a { text-decoration: none; color: #0066cc; }")
            append(".folder::before { content: '📁 '; }")
            append(".file::before { content: '📄 '; }")
            append(".size { color: #666; font-size: 0.9em; margin-left: 10px; }")
            append("</style>")
            append("</head><body>")
            append("<h1>📁 ${fsPath.fileName}</h1>")
            append("<ul>")

            for (entry in entries.sortedBy { it.fileName.toString().lowercase() }) {
                val name = entry.fileName.toString()
                val isDir = Files.isDirectory(entry)
                val cssClass = if (isDir) "folder" else "file"
                val size = if (!isDir) {
                    val bytes = Files.size(entry)
                    formatSize(bytes)
                } else ""

                // For subfolders/files, we append to the path
                val subPath = if (resourcePath.endsWith("/")) {
                    "$resourcePath$name"
                } else {
                    "$resourcePath/$name"
                }

                if (isDir) {
                    // Folders are not clickable in MVP (would need nested share logic)
                    append("<li class=\"$cssClass\">$name</li>")
                } else {
                    append("<li class=\"$cssClass\"><a href=\"/s/$token/file?path=$subPath\">$name</a><span class=\"size\">$size</span></li>")
                }
            }

            append("</ul>")
            append("</body></html>")
        }

        call.respondText(html, ContentType.Text.Html)
    }

    /**
     * GET /s/{token}/file?path=... - Download a specific file from shared folder
     */
    suspend fun handleFileAccess(call: ApplicationCall, token: String) {
        val password = call.request.queryParameters["password"]
        val filePath = call.request.queryParameters["path"]

        if (filePath == null) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("path parameter required"))
            return
        }

        when (val result = ShareLinkService.validateToken(token, password)) {
            is ShareLinkService.ValidationResult.Invalid -> {
                if (result.reason == "Invalid password") {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse(result.reason))
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(result.reason))
                }
                return
            }
            is ShareLinkService.ValidationResult.Valid -> {
                val shareLink = result.shareLink

                // Verify the requested file is under the shared folder
                if (!filePath.startsWith(shareLink.resourcePath)) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("Access denied"))
                    return
                }

                // Record access
                ShareLinkService.recordAccess(token)

                // Resolve path
                val fsPath = try {
                    pathResolver.resolve(filePath)
                } catch (e: SecurityException) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("Access denied"))
                    return
                }

                if (!storage.exists(fsPath) || storage.isDirectory(fsPath)) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("File not found"))
                    return
                }

                serveFile(call, fsPath)
            }
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}
