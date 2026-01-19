package me.kkywalk2

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import me.kkywalk2.config.ServerConfig
import me.kkywalk2.plugins.configurePlugins
import me.kkywalk2.routes.configureRouting

fun main() {
    val config = ServerConfig()

    embeddedServer(
        Netty,
        port = config.port,
        host = config.host,
        module = { module(config) }
    ).start(wait = true)
}

fun Application.module(config: ServerConfig = ServerConfig()) {
    configurePlugins()
    configureRouting(config)
}
