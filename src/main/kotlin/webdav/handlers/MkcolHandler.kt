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
import java.nio.file.FileAlreadyExistsException
import java.nio.file.NoSuchFileException

/**
 * MKCOL handler for WebDAV
 * Creates directories
 */
class MkcolHandler(
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

        // Check MKCOL permission
        if (!AuthorizationService.hasPermission(username, urlPath, Permission.MKCOL)) {
            call.respond(HttpStatusCode.Forbidden, "No MKCOL permission")
            return
        }

        try {
            // Resolve path
            val fsPath = pathResolver.resolve(urlPath)

            // Check if resource already exists
            if (storage.exists(fsPath)) {
                call.respond(HttpStatusCode.MethodNotAllowed, "Resource already exists")
                return
            }

            // Check if parent exists
            val parent = fsPath.parent
            if (parent != null && !storage.exists(parent)) {
                call.respond(HttpStatusCode.Conflict, "Parent directory does not exist")
                return
            }

            // Create directory
            storage.createDirectory(fsPath)

            // Success
            call.respond(HttpStatusCode.Created)

        } catch (e: SecurityException) {
            call.respond(HttpStatusCode.Forbidden, e.message ?: "Access denied")
        } catch (e: NoSuchFileException) {
            call.respond(HttpStatusCode.Conflict, "Parent directory does not exist")
        } catch (e: FileAlreadyExistsException) {
            call.respond(HttpStatusCode.MethodNotAllowed, "Resource already exists")
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, e.message ?: "Internal error")
        }
    }
}
