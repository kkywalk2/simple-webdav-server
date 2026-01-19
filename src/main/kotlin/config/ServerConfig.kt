package me.kkywalk2.config

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Server configuration
 */
data class ServerConfig(
    val host: String = "0.0.0.0",
    val port: Int = 8080,
    val serverRoot: Path = Paths.get(System.getProperty("user.home"), "webdav-root")
) {
    init {
        // Ensure server root directory exists
        serverRoot.toFile().mkdirs()
    }
}
