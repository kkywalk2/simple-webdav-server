package me.kkywalk2.config

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Server configuration
 */
data class ServerConfig(
    val host: String = System.getenv("WEBDAV_HOST") ?: "0.0.0.0",
    val port: Int = System.getenv("WEBDAV_PORT")?.toIntOrNull() ?: 8080,
    val serverRoot: Path = Paths.get(
        System.getenv("WEBDAV_ROOT") ?: "${System.getProperty("user.home")}/webdav-root"
    )
) {
    init {
        serverRoot.toFile().mkdirs()
    }
}
