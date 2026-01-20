package me.kkywalk2.admin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import me.kkywalk2.db.repositories.PermissionRepository
import me.kkywalk2.db.repositories.UserRepository

/**
 * Handler for user management API
 */
object UserAdminHandler {

    @Serializable
    data class CreateUserRequest(
        val username: String,
        val password: String,
        val displayName: String,
        val isAdmin: Boolean = false
    )

    @Serializable
    data class UpdateUserRequest(
        val displayName: String? = null,
        val isAdmin: Boolean? = null,
        val enabled: Boolean? = null
    )

    @Serializable
    data class UpdatePasswordRequest(
        val newPassword: String
    )

    @Serializable
    data class UserResponse(
        val username: String,
        val displayName: String,
        val enabled: Boolean,
        val isAdmin: Boolean,
        val createdAt: String?,
        val lastLoginAt: String?,
        val permissionCount: Long
    )

    @Serializable
    data class UserListResponse(
        val users: List<UserResponse>,
        val total: Int
    )

    @Serializable
    data class ErrorResponse(
        val error: String
    )

    /**
     * GET /api/admin/users - List all users
     */
    suspend fun handleList(call: ApplicationCall) {
        val users = UserRepository.findAll().map { user ->
            UserResponse(
                username = user.username,
                displayName = user.displayName,
                enabled = user.enabled,
                isAdmin = user.isAdmin,
                createdAt = user.createdAt?.toString(),
                lastLoginAt = user.lastLoginAt?.toString(),
                permissionCount = PermissionRepository.countByUsername(user.username)
            )
        }

        call.respond(UserListResponse(users = users, total = users.size))
    }

    /**
     * GET /api/admin/users/{username} - Get user details
     */
    suspend fun handleGet(call: ApplicationCall, username: String) {
        val user = UserRepository.findByUsername(username)
        if (user == null) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found: $username"))
            return
        }

        call.respond(
            UserResponse(
                username = user.username,
                displayName = user.displayName,
                enabled = user.enabled,
                isAdmin = user.isAdmin,
                createdAt = user.createdAt?.toString(),
                lastLoginAt = user.lastLoginAt?.toString(),
                permissionCount = PermissionRepository.countByUsername(user.username)
            )
        )
    }

    /**
     * POST /api/admin/users - Create a new user
     */
    suspend fun handleCreate(call: ApplicationCall) {
        val request = try {
            call.receive<CreateUserRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            return
        }

        // Validate username
        if (!isValidUsername(request.username)) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Invalid username. Use 3-50 characters: letters, numbers, underscore, hyphen")
            )
            return
        }

        // Validate password
        if (request.password.length < 4) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Password must be at least 4 characters"))
            return
        }

        // Check if username exists
        if (UserRepository.exists(request.username)) {
            call.respond(HttpStatusCode.Conflict, ErrorResponse("Username already exists: ${request.username}"))
            return
        }

        val user = UserRepository.create(
            username = request.username,
            password = request.password,
            displayName = request.displayName,
            isAdmin = request.isAdmin
        )

        call.respond(
            HttpStatusCode.Created,
            UserResponse(
                username = user.username,
                displayName = user.displayName,
                enabled = user.enabled,
                isAdmin = user.isAdmin,
                createdAt = user.createdAt?.toString(),
                lastLoginAt = user.lastLoginAt?.toString(),
                permissionCount = 0
            )
        )
    }

    /**
     * PUT /api/admin/users/{username} - Update user
     */
    suspend fun handleUpdate(call: ApplicationCall, username: String) {
        val request = try {
            call.receive<UpdateUserRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            return
        }

        if (!UserRepository.exists(username)) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found: $username"))
            return
        }

        // Prevent demoting the last admin
        if (request.isAdmin == false && username == "admin") {
            val adminCount = UserRepository.countAdmins()
            if (adminCount <= 1) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Cannot demote the last admin user"))
                return
            }
        }

        val updated = UserRepository.update(
            username = username,
            displayName = request.displayName,
            isAdmin = request.isAdmin,
            enabled = request.enabled
        )

        if (!updated) {
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to update user"))
            return
        }

        val user = UserRepository.findByUsername(username)!!
        call.respond(
            UserResponse(
                username = user.username,
                displayName = user.displayName,
                enabled = user.enabled,
                isAdmin = user.isAdmin,
                createdAt = user.createdAt?.toString(),
                lastLoginAt = user.lastLoginAt?.toString(),
                permissionCount = PermissionRepository.countByUsername(user.username)
            )
        )
    }

    /**
     * DELETE /api/admin/users/{username} - Delete user
     */
    suspend fun handleDelete(call: ApplicationCall, username: String) {
        if (!UserRepository.exists(username)) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found: $username"))
            return
        }

        // Prevent deleting the last admin
        if (UserRepository.isAdmin(username)) {
            val adminCount = UserRepository.countAdmins()
            if (adminCount <= 1) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Cannot delete the last admin user"))
                return
            }
        }

        // Delete user's permission rules first
        PermissionRepository.deleteByUsername(username)

        // Delete user
        val deleted = UserRepository.delete(username)
        if (!deleted) {
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to delete user"))
            return
        }

        call.respond(HttpStatusCode.NoContent)
    }

    /**
     * PUT /api/admin/users/{username}/password - Change password
     */
    suspend fun handleUpdatePassword(call: ApplicationCall, username: String) {
        val request = try {
            call.receive<UpdatePasswordRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            return
        }

        if (!UserRepository.exists(username)) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found: $username"))
            return
        }

        if (request.newPassword.length < 4) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Password must be at least 4 characters"))
            return
        }

        val updated = UserRepository.updatePassword(username, request.newPassword)
        if (!updated) {
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to update password"))
            return
        }

        call.respond(HttpStatusCode.NoContent)
    }

    private fun isValidUsername(username: String): Boolean {
        val regex = Regex("^[a-zA-Z0-9_-]{3,50}$")
        return regex.matches(username)
    }
}
