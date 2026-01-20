package me.kkywalk2.admin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import me.kkywalk2.db.repositories.ShareLinkRepository

/**
 * Handler for share link management API (admin)
 */
object ShareAdminHandler {

    @Serializable
    data class ShareResponse(
        val id: Int,
        val token: String,
        val path: String,
        val resourceType: String,
        val createdBy: String,
        val createdAt: String,
        val expiresAt: String?,
        val hasPassword: Boolean,
        val maxAccessCount: Int?,
        val accessCount: Int,
        val canRead: Boolean,
        val canWrite: Boolean,
        val url: String
    )

    @Serializable
    data class ShareListResponse(
        val shares: List<ShareResponse>,
        val total: Int,
        val active: Long,
        val expired: Long
    )

    @Serializable
    data class DeleteExpiredResponse(
        val deleted: Int,
        val message: String
    )

    @Serializable
    data class ErrorResponse(
        val error: String
    )

    /**
     * GET /api/admin/shares - List all share links
     */
    suspend fun handleList(call: ApplicationCall) {
        val shares = ShareLinkRepository.findAll().map { share ->
            toShareResponse(call, share)
        }

        call.respond(
            ShareListResponse(
                shares = shares,
                total = shares.size,
                active = ShareLinkRepository.countActive(),
                expired = ShareLinkRepository.countExpired()
            )
        )
    }

    /**
     * GET /api/admin/shares/{id} - Get share link details
     */
    suspend fun handleGet(call: ApplicationCall, id: Int) {
        val share = ShareLinkRepository.findById(id)
        if (share == null) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Share link not found: $id"))
            return
        }

        call.respond(toShareResponse(call, share))
    }

    /**
     * DELETE /api/admin/shares/{id} - Delete a share link
     */
    suspend fun handleDelete(call: ApplicationCall, id: Int) {
        val share = ShareLinkRepository.findById(id)
        if (share == null) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Share link not found: $id"))
            return
        }

        val deleted = ShareLinkRepository.deleteById(id)
        if (!deleted) {
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to delete share link"))
            return
        }

        call.respond(HttpStatusCode.NoContent)
    }

    /**
     * DELETE /api/admin/shares/expired - Delete all expired share links
     */
    suspend fun handleDeleteExpired(call: ApplicationCall) {
        val deletedCount = ShareLinkRepository.deleteExpired()

        call.respond(
            DeleteExpiredResponse(
                deleted = deletedCount,
                message = "$deletedCount expired share links deleted"
            )
        )
    }

    private fun toShareResponse(call: ApplicationCall, share: ShareLinkRepository.ShareLink): ShareResponse {
        val baseUrl = "${call.request.local.scheme}://${call.request.local.serverHost}:${call.request.local.serverPort}"
        return ShareResponse(
            id = share.id,
            token = share.token,
            path = share.resourcePath,
            resourceType = share.resourceType,
            createdBy = share.createdBy,
            createdAt = share.createdAt.toString(),
            expiresAt = share.expiresAt?.toString(),
            hasPassword = share.password != null,
            maxAccessCount = share.maxAccessCount,
            accessCount = share.accessCount,
            canRead = share.canRead,
            canWrite = share.canWrite,
            url = "$baseUrl/s/${share.token}"
        )
    }
}
