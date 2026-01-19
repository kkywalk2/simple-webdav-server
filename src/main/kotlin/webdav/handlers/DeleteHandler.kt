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
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.NoSuchFileException

/**
 * DELETE handler for WebDAV
 * Deletes files and empty directories
 */
class DeleteHandler(
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

        // Check DELETE permission
        if (!AuthorizationService.hasPermission(username, urlPath, Permission.DELETE)) {
            call.respond(HttpStatusCode.Forbidden, "No DELETE permission")
            return
        }

        try {
            // Resolve path
            val fsPath = pathResolver.resolve(urlPath)

            // Check if resource exists
            if (!storage.exists(fsPath)) {
                call.respond(HttpStatusCode.NotFound)
                return
            }

            // Delete based on type
            if (storage.isDirectory(fsPath)) {
                // Check if directory is empty
                if (!storage.isDirectoryEmpty(fsPath)) {
                    call.respond(HttpStatusCode.Conflict, "Directory is not empty")
                    return
                }
                storage.deleteDirectory(fsPath)
            } else {
                storage.deleteFile(fsPath)
            }

            // Success
            call.respond(HttpStatusCode.NoContent)

        } catch (e: SecurityException) {
            call.respond(HttpStatusCode.Forbidden, e.message ?: "Access denied")
        } catch (e: NoSuchFileException) {
            call.respond(HttpStatusCode.NotFound)
        } catch (e: DirectoryNotEmptyException) {
            call.respond(HttpStatusCode.Conflict, "Directory is not empty")
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, e.message ?: "Internal error")
        }
    }
}
