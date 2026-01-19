package me.kkywalk2.auth

import me.kkywalk2.db.repositories.PermissionRepository

/**
 * Authorization service
 *
 * Checks if a user has permission to perform an operation on a path.
 * Follows these rules:
 * - More specific paths take precedence over general paths
 * - deny rules take precedence (if deny is true, access is denied)
 * - Default policy: deny (if no matching rule, access is denied)
 */
object AuthorizationService {

    /**
     * Check if user has permission for a path
     */
    fun hasPermission(username: String, path: String, permission: Permission): Boolean {
        val rule = PermissionRepository.findMostSpecificRule(username, path)
            ?: return false // Default: deny

        // If deny flag is set, deny access
        if (rule.deny) {
            return false
        }

        // Check specific permission
        return when (permission) {
            Permission.LIST -> rule.canList
            Permission.READ -> rule.canRead
            Permission.WRITE -> rule.canWrite
            Permission.DELETE -> rule.canDelete
            Permission.MKCOL -> rule.canMkcol
        }
    }

    /**
     * Filter resources by LIST permission
     * Returns only resources that the user has permission to list
     */
    fun filterByListPermission(username: String, paths: List<String>): List<String> {
        return paths.filter { path ->
            hasPermission(username, path, Permission.LIST)
        }
    }
}
