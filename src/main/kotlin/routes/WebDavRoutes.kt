package me.kkywalk2.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Configure WebDAV routing
 */
fun Application.configureRouting() {
    routing {
        // Health check endpoint
        get("/") {
            call.respondText("WebDAV Server is running", ContentType.Text.Plain)
        }

        // WebDAV endpoints will be added here
        route("/webdav") {
            // TODO: Implement WebDAV methods (OPTIONS, PROPFIND, GET, PUT, DELETE, MKCOL)
        }
    }
}
