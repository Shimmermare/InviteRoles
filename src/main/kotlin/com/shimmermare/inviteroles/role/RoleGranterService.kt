package com.shimmermare.inviteroles.role

import com.shimmermare.inviteroles.i18n.InternalizationService
import com.shimmermare.inviteroles.invite.TrackedInvite
import com.shimmermare.inviteroles.invite.TrackedInviteService
import com.shimmermare.inviteroles.logger
import com.shimmermare.inviteroles.notification.NotificationService
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.exceptions.PermissionException
import org.springframework.stereotype.Service
import java.util.function.Consumer

@Service
class RoleGranterService(
        private val i18n: InternalizationService,
        private val trackedInviteService: TrackedInviteService,
        private val notificationService: NotificationService
) {
    private val log = logger()

    fun grantRolesIfNeeded(member: Member, inviteCode: String) {
        val guild = member.guild
        val invite = trackedInviteService.getInvite(inviteCode)
        if (invite == null || invite.roles.isEmpty()) {
            log.debug(
                    "Member {} joined guild {} with invite {} but this invite doesn't have any roles assigned",
                    member, guild, inviteCode
            )
            return
        }

        val roles = invite.roles.map { id ->
            val role = guild.getRoleById(id)
            if (role == null) {
                log.debug(
                        "A role with id {} from invite {} doesn't exists in guild {}",
                        id, inviteCode, guild
                )
            }
            return@map role
        }.filterNotNull()

        grantRoles(member, invite, roles)
    }

    private fun grantRoles(member: Member, invite: TrackedInvite, roles: List<Role>) {
        roles.forEach { role -> grantRole(member, invite, role) }
    }

    private fun grantRole(member: Member, invite: TrackedInvite, role: Role) {
        val guild = member.guild
        val reason = i18n.apply(guild, "role_granting.reason", "invite" to invite.inviteCode)

        val onSuccess = Consumer<Void> {
            log.info(
                    "Granted member {} of a guild {} role {} for joining with {} invite",
                    member, guild, role, invite.inviteCode
            )
        }
        val onFailure = Consumer<Throwable> { throwable ->
            log.info(
                    "Failed to grant member {} of guild {} role {} for joining with {} invite",
                    member, guild, role, invite.inviteCode
            )
            when (throwable) {
                is PermissionException -> {
                    val title = i18n.apply(guild, "role_granting.notification.insufficient_permissions.title")
                    val message = i18n.apply(
                            guild, "role_granting.notification.insufficient_permissions.message",
                            "role" to "${role.name} (${role.id})",
                            "member" to "${member.effectiveName} (${member.id})"
                    )
                    notificationService.sendError(guild, title, message)
                }
            }
        }

        guild.addRoleToMember(member, role).reason(reason).queue(onSuccess, onFailure)
    }
}