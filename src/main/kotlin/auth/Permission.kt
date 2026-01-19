package me.kkywalk2.auth

/**
 * Permission types for WebDAV operations
 */
enum class Permission {
    LIST,    // PROPFIND directory listing
    READ,    // GET/HEAD file download
    WRITE,   // PUT file upload
    DELETE,  // DELETE file/directory
    MKCOL    // MKCOL directory creation
}
