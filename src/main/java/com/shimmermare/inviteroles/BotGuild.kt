package com.shimmermare.inviteroles

import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.Role
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Joined guild wrapper.
 *
 * Synchronized by instance. Performance is not a concern because if a guild has hundreds of users per second joining
 * - this bot won't work anyway because of invite detection method.
 *
 * NOTE: please don't use [invites] directly - use the facade functions such as [getInvite], [addInvite] and so on.
 */
class BotGuild(
    private val bot: InviteRoles,
    val guild: Guild,
    settings: BotGuildSettings = BotGuildSettings(),
    invites: Collection<BotGuildInvite> = emptyList()
) {
    private val log: Logger = LoggerFactory.getLogger(BotGuild::class.java.name + ".Guild:" + guild.id)

    private val tracker: InviteTracker = InviteTracker(guild)

    /**
     * Setting the settings will cause repository update.
     */
    @Volatile
    var settings: BotGuildSettings = settings
        @Synchronized get
        @Synchronized set(value) {
            if (field != value) {
                bot.settingsRepository.set(guild, settings)
                field = value
            }
        }

    private val invites: MutableMap<String, BotGuildInvite> = ConcurrentHashMap(invites.map { it.code to it }.toMap())

    val id = guild.idLong

    fun onInviteDelete(code: String): BotGuildInvite? {
        val invite = removeInvite(code)
        return if (invite == null) {
            log.debug("Can't remove invite {} because it doesn't exist", code)
            null
        } else {
            log.debug("Invite {} removed", invite)
            invite
        }
    }

    fun onRoleDelete(role: Role): Set<BotGuildInvite> {
        val affectedInvites = getInvitesWithRole(role)
        return if (affectedInvites.isEmpty()) {
            log.debug("No invites were affected by role {} removal", role.id)
            emptySet()
        } else {
            log.debug("{} invites were removed because their role {} was removed", affectedInvites.size, role.id)
            affectedInvites.forEach { removeInvite(it) }
            affectedInvites
        }
    }

    fun onMessageReceived(message: Message) {
        val author = message.author
        if (author.isBot || author.isFake) {
            // log.debug("Message {} skipped: author is not a real user", message.id)
            return
        }

        val content = message.contentRaw.trimStart()
        val prefix = bot.properties.getProperty("command_prefix")
        if (content.length < 2 || !content.startsWith(prefix)) {
            // log.debug("Message {} skipped: not a command", message.id)
            return
        }


        val channel = message.textChannel
        val source = CommandSource(bot, this, channel, message.member!!)

        val parseResults = bot.commandDispatcher.parse(content.substring(prefix.length).trimStart(), source)
        if (parseResults.context.nodes.isEmpty()) {
            log.debug("Message {} skipped: unknown command '{}'", message.id, content)
            return
        }

        try {
            val result = bot.commandDispatcher.execute(parseResults)
            log.debug(
                "User {} executed command '{}' in channel {} in message {} with result {}",
                author.id, content, channel.idLong, message.idLong, result
            )
        } catch (e: CommandSyntaxException) {
            channel.sendMessage("Command syntax error! " + e.message).queue()
            log.debug(
                "User {} executed command '{}' in channel {} in message {} but it failed from bad syntax",
                author.id, content, channel.idLong, message.idLong, e
            )
        }
    }

    fun onMemberJoin(member: Member) {
        tracker.update()

        if (member.user.isBot || member.user.isFake) {
            log.debug("Joined member {} is fake or bot", member.id)
            return
        }

        val newUses = tracker.newUses
        when {
            newUses.size > 1 -> {
                log.debug("Multiple users ({}) joined between invite tracker updates", newUses)
                if (settings.warnings) {
                    val warning = EmbedBuilder().buildWarning(
                        "**Two or more users joined the server at the exact same time!**\n" +
                                "Unfortunately bot can't detect used invite in this case, " +
                                "so no invite roles will be granted and you should do this manually."
                    )
                    guild.systemChannel?.sendMessage(warning)
                }
            }
            newUses.isNotEmpty() -> {
                val inviteCode = newUses.keys.first()
                val invite = getInvite(inviteCode)
                if (invite == null) {
                    log.debug("Can't grant roles to {}: invite {} doesn't have any roles set", member.id, inviteCode)
                    return
                }

                val role = guild.getRoleById(invite.roleId)
                if (role == null) {
                    log.debug("Can't grant roles to {}: invite {} role {} not exists", inviteCode, invite.roleId)
                    return
                }

                guild.addRoleToMember(member, role).reason("Joined by invite ${inviteCode.censorLast()}").queue()
                log.debug("Granted role {} to {} for joining with invite {}", role.id, member.id, inviteCode)
            }
            else -> {
                log.error("Somehow tracker didn't found new invite uses when a new member joined")
            }
        }
    }

    @Synchronized
    fun getInvite(code: String): BotGuildInvite? = invites[code]

    /**
     * Will cause repository update.
     */
    @Synchronized
    fun addInvite(invite: BotGuildInvite) {
        val old = invites[invite.code]
        if (old != null || old != invite) {
            bot.invitesRepository.set(invite)
        }
    }

    /**
     * Will cause repository update.
     */
    @Synchronized
    fun removeInvite(invite: BotGuildInvite) = removeInvite(invite.code)

    /**
     * Will cause repository update.
     */
    @Synchronized
    fun removeInvite(code: String): BotGuildInvite? {
        val invite = invites.remove(code)
        if (invite != null) {
            bot.invitesRepository.delete(code)
        }
        return invite
    }

    @Synchronized
    fun getInvites(): Set<BotGuildInvite> = HashSet(invites.values)

    @Synchronized
    fun getInvitesWithRole(role: Role): Set<BotGuildInvite> = getInvitesWithRole(role.idLong)

    @Synchronized
    fun getInvitesWithRole(roleId: Long): Set<BotGuildInvite> {
        return invites.asSequence().filter { it.value.roleId == roleId }.map { it.value }.toSet()
    }
}

data class BotGuildSettings(
    val warnings: Boolean = true
)

data class BotGuildInvite(
    val code: String,
    val guildId: Long,
    val roleId: Long
)