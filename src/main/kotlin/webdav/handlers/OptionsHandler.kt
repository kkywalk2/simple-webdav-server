package me.kkywalk2.webdav.handlers

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

/**
 * OPTIONS handler for WebDAV
 * Returns supported methods and DAV compliance level
 */
object OptionsHandler {

    suspend fun handle(call: ApplicationCall) {
        call.response.headers.append("DAV", "1")
        call.response.headers.append("Allow", "OPTIONS, PROPFIND, GET, HEAD, PUT, DELETE, MKCOL")
        call.response.headers.append("MS-Author-Via", "DAV")

        call.respond(HttpStatusCode.OK)
    }
}
