package me.kkywalk2.webdav

import me.kkywalk2.storage.ResourceMetadata
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * WebDAV resource model
 */
data class WebDavResource(
    val href: String,
    val displayName: String,
    val isCollection: Boolean,
    val contentLength: Long,
    val lastModified: Instant,
    val etag: String,
    val contentType: String = if (isCollection) "httpd/unix-directory" else "application/octet-stream"
) {
    companion object {
        /**
         * RFC 1123 date format for HTTP headers
         */
        private val RFC1123_FORMATTER = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'")
            .withZone(ZoneOffset.UTC)

        /**
         * Create from storage metadata
         */
        fun from(href: String, displayName: String, metadata: ResourceMetadata): WebDavResource {
            return WebDavResource(
                href = href,
                displayName = displayName,
                isCollection = metadata.isDirectory,
                contentLength = metadata.size,
                lastModified = metadata.lastModified,
                etag = metadata.generateETag()
            )
        }
    }

    /**
     * Format last modified date in RFC 1123 format
     */
    fun formatLastModified(): String {
        return RFC1123_FORMATTER.format(lastModified)
    }

    /**
     * Get resource type for WebDAV
     */
    fun getResourceType(): String {
        return if (isCollection) "<D:collection/>" else ""
    }
}
