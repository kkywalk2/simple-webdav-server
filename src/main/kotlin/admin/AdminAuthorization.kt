package me.kkywalk2.admin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import me.kkywalk2.db.repositories.UserRepository

/**
 * Admin authorization plugin
 *
 * Checks if the authenticated user has admin privileges.
 * Must be used inside an authenticate block.
 */
val AdminAuthorization = createRouteScopedPlugin("AdminAuthorization") {
    on(AuthenticationChecked) { call ->
        val principal = call.principal<UserIdPrincipal>()

        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Authentication required"))
            return@on
        }

        val isAdmin = UserRepository.isAdmin(principal.name)
        if (!isAdmin) {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin privileges required"))
            return@on
        }
    }
}

/**
 * Extension function to get admin username from call
 */
fun ApplicationCall.adminUsername(): String? {
    return principal<UserIdPrincipal>()?.name
}
