package me.kkywalk2.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.kkywalk2.config.ServerConfig
import me.kkywalk2.share.ShareLinkHandler
import me.kkywalk2.webdav.handlers.*

/**
 * Configure WebDAV routing
 */
fun Application.configureRouting(config: ServerConfig) {
    // Initialize handlers
    val propfindHandler = PropfindHandler(config)
    val getHandler = GetHandler(config)
    val putHandler = PutHandler(config)
    val deleteHandler = DeleteHandler(config)
    val mkcolHandler = MkcolHandler(config)
    val shareLinkHandler = ShareLinkHandler(config)

    routing {
        // Health check endpoint
        get("/") {
            call.respondText("WebDAV Server is running", ContentType.Text.Plain)
        }

        // Public share link access (no authentication required)
        route("/s/{token}") {
            get {
                val token = call.parameters["token"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest
                )
                shareLinkHandler.handleAccess(call, token)
            }

            // Download specific file from shared folder
            get("/file") {
                val token = call.parameters["token"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest
                )
                shareLinkHandler.handleFileAccess(call, token)
            }
        }

        // WebDAV endpoints (with authentication)
        authenticate("webdav-auth") {
            route("/webdav") {
                // Catch all paths under /webdav
                route("{path...}") {
                    // OPTIONS - discover server capabilities
                    options {
                        OptionsHandler.handle(call)
                    }

                // PROPFIND - list directory contents
                handle {
                    if (call.request.local.method.value == "PROPFIND") {
                        val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
                        val urlPath = "/$path"
                        propfindHandler.handle(call, urlPath)
                    }
                }

                // GET - download file
                get {
                    val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
                    val urlPath = "/$path"
                    getHandler.handle(call, urlPath)
                }

                // HEAD - get file metadata
                head {
                    val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
                    val urlPath = "/$path"
                    getHandler.handle(call, urlPath)
                }

                // PUT - upload file
                put {
                    val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
                    val urlPath = "/$path"
                    putHandler.handle(call, urlPath)
                }

                // DELETE - delete file or directory
                delete {
                    val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
                    val urlPath = "/$path"
                    deleteHandler.handle(call, urlPath)
                }

                // MKCOL - create directory
                handle {
                    if (call.request.local.method.value == "MKCOL") {
                        val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
                        val urlPath = "/$path"
                        mkcolHandler.handle(call, urlPath)
                    }
                }
            }

                // Root WebDAV OPTIONS
                options("/webdav") {
                    OptionsHandler.handle(call)
                }

                // Root WebDAV PROPFIND
                handle {
                    if (call.request.local.method.value == "PROPFIND" && call.request.local.uri == "/webdav") {
                        propfindHandler.handle(call, "/")
                    }
                }
            }

            // Share link management API (requires authentication)
            route("/api/shares") {
                // Create share link
                post {
                    shareLinkHandler.handleCreate(call)
                }

                // List all share links for current user
                get {
                    shareLinkHandler.handleList(call)
                }

                // Get specific share link
                get("/{id}") {
                    val id = call.parameters["id"]?.toIntOrNull()
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@get
                    }
                    shareLinkHandler.handleGet(call, id)
                }

                // Delete share link
                delete("/{id}") {
                    val id = call.parameters["id"]?.toIntOrNull()
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@delete
                    }
                    shareLinkHandler.handleDelete(call, id)
                }
            }
        }
    }
}
