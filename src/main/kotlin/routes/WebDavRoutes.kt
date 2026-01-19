package me.kkywalk2.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.kkywalk2.config.ServerConfig
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

    routing {
        // Health check endpoint
        get("/") {
            call.respondText("WebDAV Server is running", ContentType.Text.Plain)
        }

        // WebDAV endpoints
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
}
