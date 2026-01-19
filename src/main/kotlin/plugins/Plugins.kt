package me.kkywalk2.plugins

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json
import me.kkywalk2.db.repositories.UserRepository

/**
 * Configure Ktor plugins
 */
fun Application.configurePlugins() {
    // Content Negotiation for JSON
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
        })
    }

    // HTTP Basic Authentication
    install(Authentication) {
        basic("webdav-auth") {
            realm = "WebDAV Server"
            validate { credentials ->
                val user = UserRepository.authenticate(credentials.name, credentials.password)
                if (user != null) {
                    UserIdPrincipal(user.username)
                } else {
                    null
                }
            }
        }
    }
}
