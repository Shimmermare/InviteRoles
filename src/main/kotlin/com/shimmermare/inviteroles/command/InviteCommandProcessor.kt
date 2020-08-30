package com.shimmermare.inviteroles.command

import com.mojang.brigadier.context.CommandContext
import com.shimmermare.inviteroles.hasInvite
import com.shimmermare.inviteroles.i18n.InternalizationService
import com.shimmermare.inviteroles.invite.TrackedInvite
import com.shimmermare.inviteroles.invite.TrackedInviteService
import com.shimmermare.inviteroles.logger
import com.shimmermare.inviteroles.message.MessageConfiguration
import net.dv8tion.jda.api.entities.Guild
import org.springframework.stereotype.Component
import java.util.*

@Component
class InviteCommandProcessor(
        private val i18n: InternalizationService,
        private val messageConfiguration: MessageConfiguration,
        private val trackedInviteService: TrackedInviteService
) {
    private val log = logger()

    /**
     * /ir invites
     */
    fun processGeneral(context: CommandContext<CommandSource>): Int {
        val source = context.source
        val guild = source.guild

        val invites = getActiveInvitesWithRoles(guild)
        if (invites.isEmpty()) {
            invitesInfoHandleNoInvites(source)
            return 1
        }
        val fields = invitesInfoBuildFields(guild, invites)

        val title = i18n.apply(guild, "command.invites.info_all.title")
        val descText = i18n.apply(guild, "command.invites.info_all.success.description", "count" to invites.size)
        val message = messageConfiguration.createInfoMessage(guild, title, descText, fields)
        source.channel.sendMessage(message).queue()
        log.info("(guild: {}, user: {}): Requested active invites", guild, source.member)
        return 1
    }

    private fun getActiveInvitesWithRoles(guild: Guild): List<TrackedInvite> {
        val invitesCurrent = guild.retrieveInvites().complete()
                .map { it.code }.toHashSet()
        return trackedInviteService.getInvitesOfGuild(guild)
                .filter { invitesCurrent.contains(it.inviteCode) }
    }

    private fun invitesInfoHandleNoInvites(source: CommandSource) {
        val guild = source.guild
        val title = i18n.apply(guild, "command.invites.info_all.title")
        val noInvitesText = i18n.apply(guild, "command.invites.info_all.error.no_invites.description")
        val message = messageConfiguration.createInfoMessage(guild, title, noInvitesText)
        source.channel.sendMessage(message).queue()
        log.info(
                "(guild: {}, user: {}): Requested current invites but there was none",
                guild, source.member
        )
    }

    private fun invitesInfoBuildFields(guild: Guild, invites: List<TrackedInvite>): Map<String, String> {
        val fields = HashMap<String, String>()
        invites.forEach { invite ->
            val field = inviteInfoBuildField(guild, invite)
            fields[field.first] = field.second
        }
        return fields
    }

    private fun inviteInfoBuildField(guild: Guild, invite: TrackedInvite): Pair<String, String> {
        val name = i18n.apply(guild, "command.invites.info.invite.name", "invite" to invite.inviteCode)
        val activeRoles = invite.roles.mapNotNull { guild.getRoleById(it) }
        val rolesText = activeRoles.joinToString(
                separator = ", ",
                transform = { it.name }
        )
        val value = i18n.apply(guild, "command.invites.info.invite.value", "roles" to rolesText)
        return name to value
    }

    /**
     * /ir invites <invite code>
     */
    fun processInfo(context: CommandContext<CommandSource>): Int {
        val source = context.source
        val guild = source.guild

        val inviteCode = context.getArgument("invite-code", String::class.java)

        val title = i18n.apply(guild, "command.invites.info.title")

        val guildInvites = guild.retrieveInvites().complete()
        val invite = trackedInviteService.getInvite(inviteCode)
        if (invite == null || guildInvites.find { it.code == inviteCode } == null) {
            val desc = i18n.apply(guild, "command.invites.info.error.not_found.description", "invite" to inviteCode)
            val message = messageConfiguration.createErrorMessage(guild, title, desc)
            source.channel.sendMessage(message).queue()
            log.info(
                    "(guild: {}, user: {}): Requested info for invite {} but it's not active",
                    guild, source.member, inviteCode
            )
            return 1
        }

        val field = inviteInfoBuildField(guild, invite)
        val message = messageConfiguration.createInfoMessage(guild, title, fields = mapOf(field))
        source.channel.sendMessage(message).queue()
        log.info(
                "(guild: {}, user: {}): Requested info for invite {}",
                guild, source.member, inviteCode
        )
        return 1
    }

    /**
     * /ir invite <invite code> clear
     */
    fun processClear(context: CommandContext<CommandSource>): Int {
        val source = context.source
        val guild = source.guild

        val inviteCode = context.getArgument("invite-code", String::class.java)

        val title = i18n.apply(guild, "command.invites.clear.title")

        fun handleClear(invite: TrackedInvite?): TrackedInvite? {
            if (invite == null || invite.guildId != guild.idLong) {
                val desc = i18n.apply(
                        guild, "command.invites.clear.error.not_found.description",
                        "invite" to inviteCode
                )
                val message = messageConfiguration.createErrorMessage(guild, title, desc)
                source.channel.sendMessage(message).queue()
                log.info(
                        "(guild: {}, user: {}): Tried to clear invite {} with no roles",
                        guild, source.member, inviteCode
                )
                return invite
            }

            val desc = i18n.apply(guild, "command.invites.clear.success.description", "invite" to inviteCode)
            val message = messageConfiguration.createSuccessMessage(guild, title, desc)
            source.channel.sendMessage(message).queue()
            log.info("(guild: {}, user: {}): Cleared invite {}", guild, source.member, inviteCode)
            return null
        }

        trackedInviteService.modifyInvite(inviteCode, ::handleClear)
        return 1
    }

    /**
     * /ir invite <invite code> add <role>
     */
    fun processAddRole(context: CommandContext<CommandSource>): Int {
        val source = context.source
        val guild = source.guild

        val title = i18n.apply(guild, "command.invites.add_role.title")

        val inviteCode = context.getArgument("invite-code", String::class.java)
        if (!guild.hasInvite(inviteCode)) {
            val desc = i18n.apply(
                    guild, "command.invites.add_role.error.invite_not_found.description",
                    "invite" to inviteCode
            )
            val message = messageConfiguration.createErrorMessage(guild, title, desc)
            source.channel.sendMessage(message).queue()
            log.info(
                    "(guild: {}, user: {}): Tried to add role to non-existing invite {}",
                    guild, source.member, inviteCode
            )
            return 1
        }

        val roleRetriever = context.getArgument("role", RoleRetriever::class.java)
        val role = roleRetriever.apply(guild)
        if (role == null) {
            val desc = i18n.apply(guild, "command.invites.add_role.error.role_not_found.description")
            val message = messageConfiguration.createErrorMessage(guild, title, desc)
            source.channel.sendMessage(message).queue()
            log.info(
                    "(guild: {}, user: {}): Tried to add non-existing role to invite {}",
                    guild, source.member, inviteCode
            )
            return 1
        }

        fun handleAddRole(invite: TrackedInvite?): TrackedInvite? {
            return if (invite == null) {
                log.info(
                        "(guild: {}, user: {}): Added role {} as first role to invite {}",
                        guild, source.member, role, inviteCode
                )
                TrackedInvite(inviteCode, guild.idLong, listOf(role.idLong))
            } else {
                log.info(
                        "(guild: {}, user: {}): Added role {} to invite {}",
                        guild, source.member, role, inviteCode
                )
                invite.copy(roles = invite.roles + role.idLong)
            }
        }

        trackedInviteService.modifyInvite(inviteCode, ::handleAddRole)
        val desc = i18n.apply(
                guild, "command.invites.add_role.success.description",
                "role" to role.name, "invite" to inviteCode
        )
        val message = messageConfiguration.createSuccessMessage(guild, title, desc)
        source.channel.sendMessage(message).queue()
        return 1
    }

    /**
     * /ir invite <invite code> remove <role>
     */
    fun processRemoveRole(context: CommandContext<CommandSource>): Int {
        val source = context.source
        val guild = source.guild

        val title = i18n.apply(guild, "command.invites.remove_role.title")

        val inviteCode = context.getArgument("invite-code", String::class.java)
        if (!guild.hasInvite(inviteCode)) {
            val desc = i18n.apply(
                    guild, "command.invites.remove_role.error.invite_not_found.description",
                    "invite" to inviteCode
            )
            val message = messageConfiguration.createErrorMessage(guild, title, desc)
            source.channel.sendMessage(message).queue()
            log.info(
                    "(guild: {}, user: {}): Tried to remove role from non-existing invite {}",
                    guild, source.member, inviteCode
            )
            return 1
        }

        val roleRetriever = context.getArgument("role", RoleRetriever::class.java)
        val role = roleRetriever.apply(guild)
        if (role == null) {
            val desc = i18n.apply(guild, "command.invites.remove_role.error.role_not_found.description")
            val message = messageConfiguration.createErrorMessage(guild, title, desc)
            source.channel.sendMessage(message).queue()
            log.info(
                    "(guild: {}, user: {}): Tried to remove non-existing role from invite {}",
                    guild, source.member, inviteCode
            )
            return 1
        }

        fun handleRemoveRole(invite: TrackedInvite?): TrackedInvite? {
            return when {
                invite == null -> {
                    val desc = i18n.apply(
                            guild, "command.invites.remove_role.error.role_not_assigned.description",
                            "role" to role.name, "invite" to inviteCode
                    )
                    val message = messageConfiguration.createErrorMessage(guild, title, desc)
                    source.channel.sendMessage(message).queue()
                    log.info(
                            "(guild: {}, user: {}): Tried to remove role {} from invite {} but it has no roles assigned",
                            guild, source.member, role, inviteCode
                    )
                    null
                }
                invite.roles.contains(role.idLong) -> {
                    val desc = i18n.apply(
                            guild, "command.invites.remove_role.success.description",
                            "role" to role.name, "invite" to inviteCode
                    )
                    val message = messageConfiguration.createSuccessMessage(guild, title, desc)
                    source.channel.sendMessage(message).queue()
                    log.info(
                            "(guild: {}, user: {}): Removed role {} from invite {}",
                            guild, source.member, role, inviteCode
                    )
                    val roles = invite.roles.toMutableList()
                    roles.remove(role.idLong)
                    invite.copy(roles = roles)
                }
                else -> {
                    val desc = i18n.apply(
                            guild, "command.invites.remove_role.error.role_not_assigned.description",
                            "role" to role.name, "invite" to inviteCode
                    )
                    val message = messageConfiguration.createErrorMessage(guild, title, desc)
                    source.channel.sendMessage(message).queue()
                    log.info(
                            "(guild: {}, user: {}): Removed role {} from invite {}",
                            guild, source.member, role, inviteCode
                    )
                    invite
                }
            }
        }

        trackedInviteService.modifyInvite(inviteCode, ::handleRemoveRole)
        return 1
    }
}