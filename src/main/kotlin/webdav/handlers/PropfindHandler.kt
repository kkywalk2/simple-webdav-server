package me.kkywalk2.webdav.handlers

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import me.kkywalk2.config.ServerConfig
import me.kkywalk2.path.PathResolver
import me.kkywalk2.storage.FileSystemStorage
import me.kkywalk2.storage.StorageService
import me.kkywalk2.webdav.WebDavResource
import me.kkywalk2.webdav.XmlBuilder
import kotlin.io.path.name

/**
 * PROPFIND handler for WebDAV
 * Returns properties and directory listings
 */
class PropfindHandler(
    private val config: ServerConfig,
    private val storage: StorageService = FileSystemStorage()
) {
    private val pathResolver = PathResolver(config.serverRoot)

    suspend fun handle(call: ApplicationCall, urlPath: String) {
        // Get Depth header (default to infinity, but we'll limit it)
        val depth = when (call.request.headers["Depth"]) {
            "0" -> 0
            "1" -> 1
            "infinity" -> {
                // Reject infinity depth
                call.respond(HttpStatusCode.Forbidden, "Depth: infinity not supported")
                return
            }
            else -> 1 // Default to 1
        }

        try {
            // Resolve path
            val fsPath = pathResolver.resolve(urlPath)

            // Check if resource exists
            if (!storage.exists(fsPath)) {
                call.respond(HttpStatusCode.NotFound)
                return
            }

            // Build resource list
            val resources = mutableListOf<WebDavResource>()

            // Add current resource
            val metadata = storage.getMetadata(fsPath)
            if (metadata != null) {
                val displayName = if (urlPath == "/") "/" else fsPath.name
                resources.add(WebDavResource.from(urlPath, displayName, metadata))
            }

            // If depth is 1 and it's a directory, add children
            if (depth == 1 && storage.isDirectory(fsPath)) {
                val children = storage.listDirectory(fsPath)
                for (childPath in children) {
                    val childMetadata = storage.getMetadata(childPath)
                    if (childMetadata != null) {
                        val childUrlPath = pathResolver.toUrlPath(childPath)
                        resources.add(WebDavResource.from(childUrlPath, childPath.name, childMetadata))
                    }
                }
            }

            // Build multi-status XML response
            val xml = XmlBuilder.buildMultiStatus(resources)

            call.response.headers.append("Content-Type", "application/xml; charset=utf-8")
            call.respond(HttpStatusCode.MultiStatus, xml)

        } catch (e: SecurityException) {
            call.respond(HttpStatusCode.Forbidden, e.message ?: "Access denied")
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, e.message ?: "Internal error")
        }
    }
}
