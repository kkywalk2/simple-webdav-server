package me.kkywalk2.webdav.handlers

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import me.kkywalk2.auth.AuthorizationService
import me.kkywalk2.auth.Permission
import me.kkywalk2.config.ServerConfig
import me.kkywalk2.path.PathResolver
import me.kkywalk2.storage.FileSystemStorage
import me.kkywalk2.storage.StorageService
import java.nio.file.NoSuchFileException

/**
 * PUT handler for WebDAV
 * Uploads files with streaming support
 */
class PutHandler(
    private val config: ServerConfig,
    private val storage: StorageService = FileSystemStorage()
) {
    private val pathResolver = PathResolver(config.serverRoot)

    suspend fun handle(call: ApplicationCall, urlPath: String) {
        // Get authenticated user
        val principal = call.principal<UserIdPrincipal>()
        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized)
            return
        }
        val username = principal.name

        // Check WRITE permission
        if (!AuthorizationService.hasPermission(username, urlPath, Permission.WRITE)) {
            call.respond(HttpStatusCode.Forbidden, "No WRITE permission")
            return
        }

        try {
            // Resolve path
            val fsPath = pathResolver.resolve(urlPath)

            // Check if parent directory exists
            val parent = fsPath.parent
            if (parent != null && !storage.exists(parent)) {
                call.respond(HttpStatusCode.Conflict, "Parent directory does not exist")
                return
            }

            // Check if it's trying to overwrite a directory
            if (storage.exists(fsPath) && storage.isDirectory(fsPath)) {
                call.respond(HttpStatusCode.MethodNotAllowed, "Cannot overwrite a directory with a file")
                return
            }

            // Check for conditional requests
            val ifMatch = call.request.headers["If-Match"]
            val ifNoneMatch = call.request.headers["If-None-Match"]

            if (ifMatch != null) {
                // If-Match: only proceed if ETag matches
                if (!storage.exists(fsPath)) {
                    call.respond(HttpStatusCode.PreconditionFailed, "Resource does not exist")
                    return
                }
                val metadata = storage.getMetadata(fsPath)
                if (metadata != null && metadata.generateETag() != ifMatch) {
                    call.respond(HttpStatusCode.PreconditionFailed, "ETag does not match")
                    return
                }
            }

            if (ifNoneMatch != null && ifNoneMatch == "*") {
                // If-None-Match: * means only create if it doesn't exist
                if (storage.exists(fsPath)) {
                    call.respond(HttpStatusCode.PreconditionFailed, "Resource already exists")
                    return
                }
            }

            // Determine if this is a create or update
            val isCreate = !storage.exists(fsPath)

            // Read request body and write file (streaming)
            val content = call.receiveChannel().toByteArray()
            storage.writeFile(fsPath, content)

            // Return appropriate status
            if (isCreate) {
                call.respond(HttpStatusCode.Created)
            } else {
                call.respond(HttpStatusCode.NoContent)
            }

        } catch (e: SecurityException) {
            call.respond(HttpStatusCode.Forbidden, e.message ?: "Access denied")
        } catch (e: NoSuchFileException) {
            call.respond(HttpStatusCode.Conflict, "Parent directory does not exist")
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, e.message ?: "Internal error")
        }
    }

    private suspend fun ByteReadChannel.toByteArray(): ByteArray {
        val buffer = ByteArray(8192)
        val result = mutableListOf<Byte>()

        while (!isClosedForRead) {
            val bytesRead = readAvailable(buffer)
            if (bytesRead == -1) break
            result.addAll(buffer.take(bytesRead))
        }

        return result.toByteArray()
    }
}
