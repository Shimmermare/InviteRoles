package com.shimmermare.inviteroles

import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.Role
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class BotGuild(
    private val bot: InviteRoles,
    val guild: Guild,
    val settings: BotGuildSettings,
    val invites: BotGuildInvites
) {
    private val log: Logger = LoggerFactory.getLogger(BotGuild::class.java.name + ".Guild:" + guild.id)

    private val tracker: InviteTracker = InviteTracker(guild)

    val id = guild.idLong

    fun onInviteDelete(code: String): BotGuildInvite? {
        val invite = invites.remove(code)
        return if (invite == null) {
            log.debug("Can't remove invite {} because it doesn't exist", code)
            null
        } else {
            log.debug("Invite {} removed", invite)
            invite
        }
    }

    fun onRoleDelete(role: Role): Set<BotGuildInvite> {
        val roleId = role.idLong
        val affectedInvites = invites.findWithRole(roleId)
        return if (affectedInvites.isEmpty()) {
            log.debug("No invites were affected by role {} removal", roleId)
            emptySet()
        } else {
            log.debug("{} invites were removed because their role {} was removed", affectedInvites.size, roleId)
            affectedInvites.forEach(invites::remove)
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
                // TODO send undefined invite warning
            }
            newUses.isNotEmpty() -> {
                // TODO grant roles
            }
            else -> {
                log.error("Somehow tracker didn't found new invite uses when a new member joined")
            }
        }
    }
}

/**
 * Gotta make sure that these two are thread-safe.
 */

class BotGuildSettings(val guildId: Long) {
    constructor(guild: Guild) : this(guild.idLong)

    constructor(guildId: Long, warnings: Boolean = true) : this(guildId) {
        this.backingWarnings = warnings
    }

    // Use backing property to avoid setting on 'updated' right after init
    @Volatile
    private var backingWarnings = true
    var warnings: Boolean
        @Synchronized get() {
            return backingWarnings
        }
        @Synchronized set(value) {
            backingWarnings = value
            updated = true
        }

    @Volatile
    var updated = false
        @Synchronized get
        @Synchronized set
}


class BotGuildInvites(val guildId: Long) {
    constructor(guild: Guild) : this(guild.idLong)

    constructor(guildId: Long, from: Map<String, BotGuildInvite>) : this(guildId) {
        map.putAll(from)
    }

    private val map: MutableMap<String, BotGuildInvite> = HashMap()

    // Content of the map on previous update
    private var previousMap: Map<String, BotGuildInvite> = HashMap(map)

    var updated = false
        @Synchronized get
        @Synchronized set(value) {
            if (field && !value) previousMap = HashMap(map)
            field = value
        }

    val size: Int
        @Synchronized get() = map.size

    @Synchronized
    fun contains(code: String): Boolean = map.containsKey(code)

    @Synchronized
    fun get(code: String): BotGuildInvite? = map[code]

    @Synchronized
    fun put(invite: BotGuildInvite) {
        map[invite.code] = invite
        updated = true
    }

    @Synchronized
    fun remove(invite: BotGuildInvite) {
        if (map.remove(invite.code) != null) updated = true
    }

    @Synchronized
    fun remove(code: String): BotGuildInvite? {
        val invite = map.remove(code)
        if (invite != null) updated = true
        return invite
    }

    @Synchronized
    fun findWithRole(role: Role) = findWithRole(role.idLong)

    @Synchronized
    fun findWithRole(roleId: Long) = map.filter { (_, i) -> i.roleId == roleId }.map { (_, i) -> i }.toSet()

    @Synchronized
    fun getAll(): Set<BotGuildInvite> = HashSet(map.values)

    @Synchronized
    fun findDeltaSinceLastUpdate(): Delta {
        val added: MutableSet<BotGuildInvite> = HashSet()
        val updated: MutableSet<BotGuildInvite> = HashSet()
        val removed: MutableSet<BotGuildInvite> = HashSet()
        map.forEach { (code, invite) ->
            val invitePrevious = previousMap[code]
            if (invitePrevious == null) {
                added.add(invite)
            } else if (invite != invitePrevious) {
                updated.add(invite)
            }
        }
        previousMap.forEach { (code, invite) ->
            if (!map.containsKey(code)) {
                removed.add(invite)
            }
        }
        return Delta(added, updated, removed)
    }

    data class Delta(val added: Set<BotGuildInvite>, val updated: Set<BotGuildInvite>, val removed: Set<BotGuildInvite>)
}

data class BotGuildInvite(val code: String, val guildId: Long, val roleId: Long)