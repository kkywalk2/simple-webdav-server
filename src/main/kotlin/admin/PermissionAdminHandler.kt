package me.kkywalk2.admin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import me.kkywalk2.db.repositories.PermissionRepository
import me.kkywalk2.db.repositories.UserRepository

/**
 * Handler for permission management API
 */
object PermissionAdminHandler {

    @Serializable
    data class CreatePermissionRequest(
        val username: String,
        val path: String,
        val canList: Boolean = false,
        val canRead: Boolean = false,
        val canWrite: Boolean = false,
        val canDelete: Boolean = false,
        val canMkcol: Boolean = false,
        val deny: Boolean = false
    )

    @Serializable
    data class UpdatePermissionRequest(
        val canList: Boolean? = null,
        val canRead: Boolean? = null,
        val canWrite: Boolean? = null,
        val canDelete: Boolean? = null,
        val canMkcol: Boolean? = null,
        val deny: Boolean? = null
    )

    @Serializable
    data class PermissionResponse(
        val id: Int,
        val username: String,
        val path: String,
        val canList: Boolean,
        val canRead: Boolean,
        val canWrite: Boolean,
        val canDelete: Boolean,
        val canMkcol: Boolean,
        val deny: Boolean
    )

    @Serializable
    data class PermissionListResponse(
        val rules: List<PermissionResponse>,
        val total: Int
    )

    @Serializable
    data class ErrorResponse(
        val error: String
    )

    /**
     * GET /api/admin/permissions - List all permission rules
     */
    suspend fun handleList(call: ApplicationCall) {
        val username = call.request.queryParameters["username"]

        val rules = if (username != null) {
            PermissionRepository.getPermissions(username)
        } else {
            PermissionRepository.findAll()
        }

        val response = rules.map { rule ->
            PermissionResponse(
                id = rule.id,
                username = rule.username,
                path = rule.path,
                canList = rule.canList,
                canRead = rule.canRead,
                canWrite = rule.canWrite,
                canDelete = rule.canDelete,
                canMkcol = rule.canMkcol,
                deny = rule.deny
            )
        }

        call.respond(PermissionListResponse(rules = response, total = response.size))
    }

    /**
     * GET /api/admin/permissions/user/{username} - Get permissions for a user
     */
    suspend fun handleGetByUser(call: ApplicationCall, username: String) {
        if (!UserRepository.exists(username)) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found: $username"))
            return
        }

        val rules = PermissionRepository.getPermissions(username)
        val response = rules.map { rule ->
            PermissionResponse(
                id = rule.id,
                username = rule.username,
                path = rule.path,
                canList = rule.canList,
                canRead = rule.canRead,
                canWrite = rule.canWrite,
                canDelete = rule.canDelete,
                canMkcol = rule.canMkcol,
                deny = rule.deny
            )
        }

        call.respond(PermissionListResponse(rules = response, total = response.size))
    }

    /**
     * GET /api/admin/permissions/{id} - Get permission rule by ID
     */
    suspend fun handleGet(call: ApplicationCall, id: Int) {
        val rule = PermissionRepository.findById(id)
        if (rule == null) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Permission rule not found: $id"))
            return
        }

        call.respond(
            PermissionResponse(
                id = rule.id,
                username = rule.username,
                path = rule.path,
                canList = rule.canList,
                canRead = rule.canRead,
                canWrite = rule.canWrite,
                canDelete = rule.canDelete,
                canMkcol = rule.canMkcol,
                deny = rule.deny
            )
        )
    }

    /**
     * POST /api/admin/permissions - Create a new permission rule
     */
    suspend fun handleCreate(call: ApplicationCall) {
        val request = try {
            call.receive<CreatePermissionRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            return
        }

        // Validate user exists
        if (!UserRepository.exists(request.username)) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found: ${request.username}"))
            return
        }

        // Validate path
        if (!isValidPath(request.path)) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid path. Must start with /"))
            return
        }

        // Check for duplicate
        if (PermissionRepository.existsByUserAndPath(request.username, request.path)) {
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse("Permission rule already exists for ${request.username} on ${request.path}")
            )
            return
        }

        val rule = PermissionRepository.create(
            username = request.username,
            path = request.path,
            canList = request.canList,
            canRead = request.canRead,
            canWrite = request.canWrite,
            canDelete = request.canDelete,
            canMkcol = request.canMkcol,
            deny = request.deny
        )

        call.respond(
            HttpStatusCode.Created,
            PermissionResponse(
                id = rule.id,
                username = rule.username,
                path = rule.path,
                canList = rule.canList,
                canRead = rule.canRead,
                canWrite = rule.canWrite,
                canDelete = rule.canDelete,
                canMkcol = rule.canMkcol,
                deny = rule.deny
            )
        )
    }

    /**
     * PUT /api/admin/permissions/{id} - Update a permission rule
     */
    suspend fun handleUpdate(call: ApplicationCall, id: Int) {
        val request = try {
            call.receive<UpdatePermissionRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            return
        }

        val existingRule = PermissionRepository.findById(id)
        if (existingRule == null) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Permission rule not found: $id"))
            return
        }

        val updated = PermissionRepository.update(
            id = id,
            canList = request.canList,
            canRead = request.canRead,
            canWrite = request.canWrite,
            canDelete = request.canDelete,
            canMkcol = request.canMkcol,
            deny = request.deny
        )

        if (!updated) {
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to update permission rule"))
            return
        }

        val rule = PermissionRepository.findById(id)!!
        call.respond(
            PermissionResponse(
                id = rule.id,
                username = rule.username,
                path = rule.path,
                canList = rule.canList,
                canRead = rule.canRead,
                canWrite = rule.canWrite,
                canDelete = rule.canDelete,
                canMkcol = rule.canMkcol,
                deny = rule.deny
            )
        )
    }

    /**
     * DELETE /api/admin/permissions/{id} - Delete a permission rule
     */
    suspend fun handleDelete(call: ApplicationCall, id: Int) {
        val existingRule = PermissionRepository.findById(id)
        if (existingRule == null) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Permission rule not found: $id"))
            return
        }

        val deleted = PermissionRepository.delete(id)
        if (!deleted) {
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to delete permission rule"))
            return
        }

        call.respond(HttpStatusCode.NoContent)
    }

    private fun isValidPath(path: String): Boolean {
        if (!path.startsWith("/")) return false
        if (path.contains("..")) return false
        return true
    }
}
