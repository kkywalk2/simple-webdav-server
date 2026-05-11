package me.kkywalk2.webdav.handlers

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kkywalk2.auth.AuthorizationService
import me.kkywalk2.auth.Permission
import me.kkywalk2.config.ServerConfig
import me.kkywalk2.path.PathResolver
import me.kkywalk2.storage.FileSystemStorage
import me.kkywalk2.storage.StorageService
import java.nio.file.NoSuchFileException

class PutHandler(
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

        if (!AuthorizationService.hasPermission(principal.name, urlPath, Permission.WRITE)) {
            call.respond(HttpStatusCode.Forbidden, "No WRITE permission")
            return
        }

        try {
            val fsPath = pathResolver.resolve(urlPath)

            val parent = fsPath.parent
            if (parent != null && !storage.exists(parent)) {
                call.respond(HttpStatusCode.Conflict, "Parent directory does not exist")
                return
            }

            if (storage.exists(fsPath) && storage.isDirectory(fsPath)) {
                call.respond(HttpStatusCode.MethodNotAllowed, "Cannot overwrite a directory with a file")
                return
            }

            val ifMatch = call.request.headers["If-Match"]
            val ifNoneMatch = call.request.headers["If-None-Match"]

            if (ifMatch != null) {
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
                if (storage.exists(fsPath)) {
                    call.respond(HttpStatusCode.PreconditionFailed, "Resource already exists")
                    return
                }
            }

            val isCreate = !storage.exists(fsPath)

            // receiveStream() is blocking — run on IO dispatcher to avoid blocking the event loop
            withContext(Dispatchers.IO) {
                call.receiveStream().use { inputStream ->
                    storage.writeFileStreaming(fsPath, inputStream)
                }
            }

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

}
