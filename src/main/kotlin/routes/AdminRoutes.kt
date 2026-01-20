package me.kkywalk2.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.kkywalk2.admin.*
import me.kkywalk2.config.ServerConfig

/**
 * Configure admin routes
 */
fun Route.configureAdminRoutes(config: ServerConfig) {
    val fileAdminHandler = FileAdminHandler(config)

    // Admin API routes (requires authentication + admin privileges)
    authenticate("webdav-auth") {
        route("/api/admin") {
            // Apply admin authorization check to all routes
            install(AdminAuthorization)

            // User management
            route("/users") {
                get {
                    UserAdminHandler.handleList(call)
                }

                post {
                    UserAdminHandler.handleCreate(call)
                }

                get("/{username}") {
                    val username = call.parameters["username"]
                    if (username == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@get
                    }
                    UserAdminHandler.handleGet(call, username)
                }

                put("/{username}") {
                    val username = call.parameters["username"]
                    if (username == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@put
                    }
                    UserAdminHandler.handleUpdate(call, username)
                }

                delete("/{username}") {
                    val username = call.parameters["username"]
                    if (username == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@delete
                    }
                    UserAdminHandler.handleDelete(call, username)
                }

                put("/{username}/password") {
                    val username = call.parameters["username"]
                    if (username == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@put
                    }
                    UserAdminHandler.handleUpdatePassword(call, username)
                }
            }

            // Permission management
            route("/permissions") {
                get {
                    PermissionAdminHandler.handleList(call)
                }

                post {
                    PermissionAdminHandler.handleCreate(call)
                }

                get("/user/{username}") {
                    val username = call.parameters["username"]
                    if (username == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@get
                    }
                    PermissionAdminHandler.handleGetByUser(call, username)
                }

                get("/{id}") {
                    val id = call.parameters["id"]?.toIntOrNull()
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@get
                    }
                    PermissionAdminHandler.handleGet(call, id)
                }

                put("/{id}") {
                    val id = call.parameters["id"]?.toIntOrNull()
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@put
                    }
                    PermissionAdminHandler.handleUpdate(call, id)
                }

                delete("/{id}") {
                    val id = call.parameters["id"]?.toIntOrNull()
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@delete
                    }
                    PermissionAdminHandler.handleDelete(call, id)
                }
            }

            // File browser
            route("/files") {
                get {
                    fileAdminHandler.handleList(call)
                }

                get("/info") {
                    fileAdminHandler.handleInfo(call)
                }

                post("/mkdir") {
                    fileAdminHandler.handleMkdir(call)
                }

                delete {
                    fileAdminHandler.handleDelete(call)
                }
            }

            // Share link management
            route("/shares") {
                get {
                    ShareAdminHandler.handleList(call)
                }

                delete("/expired") {
                    ShareAdminHandler.handleDeleteExpired(call)
                }

                get("/{id}") {
                    val id = call.parameters["id"]?.toIntOrNull()
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@get
                    }
                    ShareAdminHandler.handleGet(call, id)
                }

                delete("/{id}") {
                    val id = call.parameters["id"]?.toIntOrNull()
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@delete
                    }
                    ShareAdminHandler.handleDelete(call, id)
                }
            }
        }

        // Admin web UI (requires authentication + admin privileges)
        // Redirect /admin to /admin/ for consistent URL handling
        get("/admin") {
            call.respondRedirect("/admin/")
        }

        route("/admin") {
            install(AdminAuthorization)

            // Serve static files from resources/admin
            staticResources("/", "admin") {
                default("index.html")
            }
        }
    }
}
