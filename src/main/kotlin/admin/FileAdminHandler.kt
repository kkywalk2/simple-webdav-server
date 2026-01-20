package me.kkywalk2.admin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import me.kkywalk2.config.ServerConfig
import me.kkywalk2.path.PathResolver
import me.kkywalk2.storage.FileSystemStorage
import java.nio.file.Files

/**
 * Handler for file browser API
 */
class FileAdminHandler(config: ServerConfig) {

    private val pathResolver = PathResolver(config.serverRoot)
    private val storage = FileSystemStorage()

    @Serializable
    data class FileEntry(
        val name: String,
        val path: String,
        val type: String, // FILE or FOLDER
        val size: Long,
        val sizeFormatted: String,
        val lastModified: String,
        val mimeType: String? = null,
        val childCount: Int? = null
    )

    @Serializable
    data class DirectoryListResponse(
        val path: String,
        val parentPath: String?,
        val entries: List<FileEntry>,
        val totalCount: Int,
        val folderCount: Int,
        val fileCount: Int
    )

    @Serializable
    data class FileInfoResponse(
        val name: String,
        val path: String,
        val type: String,
        val size: Long,
        val sizeFormatted: String,
        val lastModified: String,
        val mimeType: String? = null,
        val childCount: Int? = null
    )

    @Serializable
    data class MkdirRequest(
        val path: String
    )

    @Serializable
    data class MkdirResponse(
        val name: String,
        val path: String,
        val type: String
    )

    @Serializable
    data class ErrorResponse(
        val error: String
    )

    /**
     * GET /api/admin/files - List directory contents
     */
    suspend fun handleList(call: ApplicationCall) {
        val urlPath = call.request.queryParameters["path"] ?: "/"
        val sortBy = call.request.queryParameters["sort"] ?: "name"
        val order = call.request.queryParameters["order"] ?: "asc"

        val fsPath = try {
            pathResolver.resolve(urlPath)
        } catch (e: SecurityException) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("Access denied"))
            return
        }

        if (!storage.exists(fsPath)) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Path not found: $urlPath"))
            return
        }

        if (!storage.isDirectory(fsPath)) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Not a directory: $urlPath"))
            return
        }

        val entries = storage.listDirectory(fsPath).map { entryPath ->
            val isDir = storage.isDirectory(entryPath)
            val metadata = storage.getMetadata(entryPath)
            val size = metadata?.size ?: 0
            val mimeType = if (!isDir) {
                Files.probeContentType(entryPath) ?: "application/octet-stream"
            } else null

            val childCount = if (isDir) {
                try {
                    storage.listDirectory(entryPath).size
                } catch (e: Exception) {
                    0
                }
            } else null

            FileEntry(
                name = entryPath.fileName.toString(),
                path = pathResolver.toUrlPath(entryPath),
                type = if (isDir) "FOLDER" else "FILE",
                size = size,
                sizeFormatted = formatSize(size),
                lastModified = metadata?.lastModified?.toString() ?: "",
                mimeType = mimeType,
                childCount = childCount
            )
        }

        // Sort entries
        val sortedEntries = when (sortBy) {
            "size" -> if (order == "desc") entries.sortedByDescending { it.size }
            else entries.sortedBy { it.size }
            "modified" -> if (order == "desc") entries.sortedByDescending { it.lastModified }
            else entries.sortedBy { it.lastModified }
            else -> { // name
                val sorted = entries.sortedWith(compareBy({ it.type != "FOLDER" }, { it.name.lowercase() }))
                if (order == "desc") sorted.reversed() else sorted
            }
        }

        val folderCount = sortedEntries.count { it.type == "FOLDER" }
        val fileCount = sortedEntries.count { it.type == "FILE" }

        val parentPath = if (urlPath == "/" || urlPath.isEmpty()) {
            null
        } else {
            val parent = urlPath.substringBeforeLast("/")
            if (parent.isEmpty()) "/" else parent
        }

        call.respond(
            DirectoryListResponse(
                path = urlPath,
                parentPath = parentPath,
                entries = sortedEntries,
                totalCount = sortedEntries.size,
                folderCount = folderCount,
                fileCount = fileCount
            )
        )
    }

    /**
     * GET /api/admin/files/info - Get file/folder info
     */
    suspend fun handleInfo(call: ApplicationCall) {
        val urlPath = call.request.queryParameters["path"]
        if (urlPath == null) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("path parameter required"))
            return
        }

        val fsPath = try {
            pathResolver.resolve(urlPath)
        } catch (e: SecurityException) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("Access denied"))
            return
        }

        if (!storage.exists(fsPath)) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Path not found: $urlPath"))
            return
        }

        val isDir = storage.isDirectory(fsPath)
        val metadata = storage.getMetadata(fsPath)
        val size = metadata?.size ?: 0

        val mimeType = if (!isDir) {
            Files.probeContentType(fsPath) ?: "application/octet-stream"
        } else null

        val childCount = if (isDir) {
            try {
                storage.listDirectory(fsPath).size
            } catch (e: Exception) {
                0
            }
        } else null

        call.respond(
            FileInfoResponse(
                name = fsPath.fileName?.toString() ?: "",
                path = urlPath,
                type = if (isDir) "FOLDER" else "FILE",
                size = size,
                sizeFormatted = formatSize(size),
                lastModified = metadata?.lastModified?.toString() ?: "",
                mimeType = mimeType,
                childCount = childCount
            )
        )
    }

    /**
     * POST /api/admin/files/mkdir - Create directory
     */
    suspend fun handleMkdir(call: ApplicationCall) {
        val request = try {
            call.receive<MkdirRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            return
        }

        if (!request.path.startsWith("/")) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Path must start with /"))
            return
        }

        if (request.path.contains("..")) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("Invalid path"))
            return
        }

        val fsPath = try {
            pathResolver.resolve(request.path)
        } catch (e: SecurityException) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("Access denied"))
            return
        }

        if (storage.exists(fsPath)) {
            call.respond(HttpStatusCode.Conflict, ErrorResponse("Path already exists: ${request.path}"))
            return
        }

        // Check parent exists
        val parentPath = fsPath.parent
        if (parentPath != null && !storage.exists(parentPath)) {
            call.respond(HttpStatusCode.Conflict, ErrorResponse("Parent directory does not exist"))
            return
        }

        try {
            storage.createDirectory(fsPath)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to create directory: ${e.message}"))
            return
        }

        call.respond(
            HttpStatusCode.Created,
            MkdirResponse(
                name = fsPath.fileName.toString(),
                path = request.path,
                type = "FOLDER"
            )
        )
    }

    /**
     * DELETE /api/admin/files - Delete file or directory
     */
    suspend fun handleDelete(call: ApplicationCall) {
        val urlPath = call.request.queryParameters["path"]
        val recursive = call.request.queryParameters["recursive"]?.toBoolean() ?: false

        if (urlPath == null) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("path parameter required"))
            return
        }

        if (urlPath == "/" || urlPath.isEmpty()) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("Cannot delete root directory"))
            return
        }

        val fsPath = try {
            pathResolver.resolve(urlPath)
        } catch (e: SecurityException) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("Access denied"))
            return
        }

        if (!storage.exists(fsPath)) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Path not found: $urlPath"))
            return
        }

        try {
            if (storage.isDirectory(fsPath)) {
                if (!storage.isDirectoryEmpty(fsPath) && !recursive) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ErrorResponse("Directory is not empty. Use recursive=true to delete non-empty directories")
                    )
                    return
                }

                if (recursive) {
                    deleteRecursive(fsPath)
                } else {
                    storage.deleteDirectory(fsPath)
                }
            } else {
                storage.deleteFile(fsPath)
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to delete: ${e.message}"))
            return
        }

        call.respond(HttpStatusCode.NoContent)
    }

    private fun deleteRecursive(path: java.nio.file.Path) {
        if (storage.isDirectory(path)) {
            for (child in storage.listDirectory(path)) {
                deleteRecursive(child)
            }
            storage.deleteDirectory(path)
        } else {
            storage.deleteFile(path)
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
}
