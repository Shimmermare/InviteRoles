package com.shimmermare.inviteroles.service

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType.string
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.shimmermare.inviteroles.asChannelMention
import com.shimmermare.inviteroles.configuration.BasicConfiguration
import com.shimmermare.inviteroles.configuration.MessageConfiguration
import com.shimmermare.inviteroles.entity.*
import com.shimmermare.inviteroles.hasInvite
import com.shimmermare.inviteroles.logger
import com.shimmermare.inviteroles.service.InviteArgumentType.invite
import com.shimmermare.inviteroles.service.RoleArgumentType.role
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.utils.MiscUtil
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.util.*
import java.util.function.Function
import kotlin.collections.HashMap

class CommandSource(val guild: Guild, val channel: TextChannel, val member: Member)

/**
 * Chat command processing service.
 *
 * Brigadier requires to return magic int - return 1 if you processed the command.
 */
@Service
class CommandService(
    private val basicConfiguration: BasicConfiguration,
    private val messageConfiguration: MessageConfiguration,
    private val jda: JDA,
    private val settingsService: GuildSettingsService,
    private val inviteService: InviteService,
    private val i18n: InternalizationService
) : ListenerAdapter() {
    private val log = logger<CommandService>()
    private val dispatcher = CommandDispatcher<CommandSource>()

    init {
        registerCommands()
    }

    @EventListener
    fun onApplicationEvent(event: ContextRefreshedEvent) {
        jda.addEventListener(this)
        log.info("Starting listening to Discord events")
    }

    @EventListener
    fun onApplicationEvent(event: ContextClosedEvent) {
        jda.removeEventListener(this)
        log.info("Stopping listening to Discord events")
    }

    override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) {
        val guild = event.guild
        val message = event.message
        val author = message.author
        if (author.isBot || author.isFake || event.member == null) {
            log.debug("(Guild: {}) Message {} skipped: author is not a real user", guild, message)
            return
        }

        val content = message.contentRaw.trimStart()
        val prefix = basicConfiguration.commandPrefix
        if (content.length < 2 || !content.startsWith(prefix)) {
            log.debug("(Guild: {}) Message {} skipped: not a command", guild, message)
            return
        }

        val channel = message.textChannel
        val source = CommandSource(guild, channel, message.member!!)

        val parseResults = dispatcher.parse(content.substring(prefix.length).trimStart(), source)
        if (parseResults.context.nodes.isEmpty()) {
            log.debug("(Guild: {}) Message {} skipped: unknown command '{}'", guild, message, content)
            return
        }

        try {
            val result = dispatcher.execute(parseResults)
            if (result <= 0) {
                val githubUrl = basicConfiguration.githubUrl
                val title = i18n.apply(guild, "command.parsing.unknown_command.title")
                val desc = i18n.apply(
                    guild, "command.parsing.unknown_command.description",
                    "github_url" to githubUrl
                )
                val errMessage = messageConfiguration.createErrorMessage(guild, title, desc)
                source.channel.sendMessage(errMessage).queue()
                log.info("(Guild: {}) Message {} skipped: response is not 1", guild, message, content)
            }
            log.info(
                "(Guild: {}) User {} executed command '{}' in channel {} in message {} with result {}",
                guild, author, content, channel, message, result
            )
        } catch (e: CommandSyntaxException) {
            val githubUrl = basicConfiguration.githubUrl
            val title = i18n.apply(guild, "command.parsing.syntax_error.title")
            val desc = i18n.apply(
                guild, "command.parsing.syntax_error.description",
                "pos" to e.cursor, "github_url" to githubUrl
            )
            val errMessage = messageConfiguration.createErrorMessage(guild, title, desc)
            source.channel.sendMessage(errMessage).queue()
            log.info(
                "(Guild: {}) User {} executed command '{}' in channel {} in message {} but it failed from bad syntax",
                guild, author, content, channel, message, e
            )
        }
    }

    private fun registerCommands() {
        val root = dispatcher.register(literal("inviteroles")
            .then(literal("settings")
                .requires { it.member.hasPermission(Permission.ADMINISTRATOR) }
                .then(
                    argument("setting", string())
                        .then(argument("value", string()).executes(::editSetting))
                        .executes(::settingInfo)
                )
                .executes(::settingsInfo)
            )
            .then(literal("invites")
                .requires { it.member.hasPermission(Permission.MANAGE_ROLES) }
                .then(
                    argument("invite-code", invite())
                        .then(literal("clear").executes(::inviteClear))
                        .then(
                            literal("add").then(
                                argument("role", role()).executes(::inviteAddRole)
                            )
                        )
                        .then(
                            literal("remove").then(
                                argument("role", role()).executes(::inviteRemoveRole)
                            )
                        )
                        .executes(::inviteInfo)
                )
                .executes(::invitesInfo)
            )
            .then(
                literal("invites")
                    .executes(::invitesInfo)
            )
            .executes(::about)
        )

        // Short alias
        dispatcher.register(
            literal("ir")
                .redirect(root)
                .executes(::about) // It's not working if you specify this only in root
        )
    }

    /**
     * Command endpoint
     * /ir
     */
    private fun about(context: CommandContext<CommandSource>): Int {
        val source = context.source
        val guild = source.guild

        val title = i18n.apply(guild, "command.about.title")
        val desc = i18n.apply(
            guild, "command.about.description",
            "version" to basicConfiguration.version,
            "author" to basicConfiguration.author.name,
            "github_url" to basicConfiguration.githubUrl
        )
        val message = messageConfiguration.createInfoMessage(guild, title, desc)
        source.channel.sendMessage(message).queue()
        log.info("(guild: {}, user: {}): About command", guild, source.member)
        return 1
    }

    /**
     * Command endpoint
     * /ir settings
     */
    private fun settingsInfo(context: CommandContext<CommandSource>): Int {
        val source = context.source
        val guild = source.guild
        val settings = settingsService.getOrCreateSettings(guild)

        val fields = mapOf(
            NOTIFICATIONS_SETTING to settings.notifications.toString(),
            NOTIFICATION_CHANNEL_SETTING to settings.notificationChannel.toString(),
            LANG_SETTING to settings.language
        )

        val message = messageConfiguration.createInfoMessage(
            guild, title = i18n.apply(guild, "command.settings.info_all.title"), fields = fields
        )
        source.channel.sendMessage(message).queue()
        log.info("(guild: {}, user: {}): Requested current settings", guild, source.member)
        return 1
    }

    /**
     * Command endpoint
     * /ir settings <setting>
     */
    private fun settingInfo(context: CommandContext<CommandSource>): Int {
        val source = context.source
        val guild = source.guild
        val setting = context.getArgument("setting", String::class.java)

        val title = i18n.apply(guild, "command.settings.info.title")

        val info = settingsService.getSettingByString(guild, setting)
        if (info == null) {
            val desc = i18n.apply(guild, "command.settings.info.error.not_found.description", "setting" to setting)
            val message = messageConfiguration.createErrorMessage(guild, title, desc)
            source.channel.sendMessage(message).queue()
            log.info("(guild: {}, user: {}): Requested unknown setting {}", guild, source.member, setting)
            return 1
        }

        val settings = settingsService.getOrCreateSettings(guild)
        val settingValue = getSettingByName(settings, setting)
        val desc = i18n.apply(
            guild, "command.settings.info.success.description",
            "setting" to setting, "value" to settingValue
        )
        val message = messageConfiguration.createInfoMessage(guild, title, desc)
        source.channel.sendMessage(message).queue()
        log.info("(guild: {}, user: {}): Requested setting {}", guild, source.member, setting)
        return 1
    }

    private fun getSettingByName(settings: GuildSettings, setting: String): String {
        return settingsService.getSettingByString(settings, setting)
            ?: throw IllegalArgumentException("Unknown property")
    }

    /**
     * Command endpoint
     * /ir settings <setting> <value>
     */
    private fun editSetting(context: CommandContext<CommandSource>): Int {
        val source = context.source
        val setting = context.getArgument("setting", String::class.java)
        val value = context.getArgument("value", String::class.java)

        try {
            tryEditSettings(source, setting, value)
        } catch (e: Exception) {
            e.handleSettingEditException(source, setting, value)
        }
        return 1
    }

    private fun tryEditSettings(source: CommandSource, setting: String, value: String) {
        val guild = source.guild

        val settings = settingsService.setSettingFromString(guild, setting, value)
        val newValue = settingsService.getSettingByString(settings, setting).toString()

        val message = messageConfiguration.createSuccessMessage(
            guild,
            title = i18n.apply(guild, "command.settings.edit.success.title"),
            description = i18n.apply(
                guild,
                "command.settings.edit.success.description",
                "setting" to newValue /* no nulls */,
                "value" to value
            )
        )
        source.channel.sendMessage(message).queue()
        log.info(
            "(guild: {}, user: {}): Changed setting {} to {}",
            guild, source.member, setting, newValue
        )
    }

    private fun Exception.handleSettingEditException(source: CommandSource, setting: String, value: String) {
        val guild = source.guild
        when (this) {
            is java.lang.IllegalArgumentException -> {
                val title = i18n.apply(guild, "command.settings.edit.error.title")
                val descText = i18n.apply(
                    guild, "command.settings.edit.error.invalid_value.description",
                    "setting" to setting, "value" to value
                )
                val message = messageConfiguration.createErrorMessage(guild, title, descText)
                source.channel.sendMessage(message).queue()
                log.info(
                    "(guild: {}, user: {}): Tried to change setting {} to {}",
                    guild, source.member, setting, value, this
                )
            }
            else -> throw this
        }
    }

    /**
     * Command endpoint
     * /ir invites
     */
    private fun invitesInfo(context: CommandContext<CommandSource>): Int {
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
        return inviteService.getInvitesOfGuild(guild)
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
        val name = i18n.apply(guild, "command.invites.info.invite.name", "name" to invite.inviteCode)
        val activeRoles = invite.roles.mapNotNull { guild.getRoleById(it) }
        val rolesText = activeRoles.joinToString(
            separator = ", ",
            transform = { it.name }
        )
        val value = i18n.apply(guild, "command.invites.info.invite.value", "roles" to rolesText)
        return name to value
    }

    /**
     * Command endpoint
     * /ir invites <invite code>
     */
    private fun inviteInfo(context: CommandContext<CommandSource>): Int {
        val source = context.source
        val guild = source.guild

        val inviteCode = context.getArgument("invite-code", String::class.java)

        val title = i18n.apply(guild, "command.invites.info.title")

        val guildInvites = guild.retrieveInvites().complete()
        val invite = inviteService.getInvite(inviteCode)
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
     * Command endpoint
     * /ir invite <invite code> clear
     */
    private fun inviteClear(context: CommandContext<CommandSource>): Int {
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

        inviteService.modifyInvite(inviteCode, ::handleClear)
        return 1
    }

    /**
     * Command endpoint
     * /ir invite <invite code> add <role>
     */
    private fun inviteAddRole(context: CommandContext<CommandSource>): Int {
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

        inviteService.modifyInvite(inviteCode, ::handleAddRole)
        val desc = i18n.apply(
            guild, "command.invites.add_role.success.description",
            "role" to role.name, "invite" to inviteCode
        )
        val message = messageConfiguration.createSuccessMessage(guild, title, desc)
        source.channel.sendMessage(message).queue()
        return 1
    }

    /**
     * Command endpoint
     * /ir invite <invite code> remove <role>
     */
    private fun inviteRemoveRole(context: CommandContext<CommandSource>): Int {
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

        inviteService.modifyInvite(inviteCode, ::handleRemoveRole)
        return 1
    }
}

// Workaround for weird static import behaviour
private fun literal(name: String): LiteralArgumentBuilder<CommandSource> {
    return LiteralArgumentBuilder.literal(name)
}

private fun <T> argument(name: String, type: ArgumentType<T>): RequiredArgumentBuilder<CommandSource, T> {
    return RequiredArgumentBuilder.argument(name, type)
}

/**
 * A little interface to get around java type erasure.
 *
 * @see RoleArgumentType
 */
private interface RoleRetriever : Function<Guild, Role?>

/**
 * Despite the name, doesn't represent [Role] by itself
 * but rather provides a function to get it from server.
 *
 * Argument may be role mention aka "<@&snowflake_id>" or role name (case insensitive).
 * In latter case the most recently created will be used.
 */
private object RoleArgumentType : ArgumentType<RoleRetriever> {
    fun role(): RoleArgumentType {
        return RoleArgumentType
    }

    @Throws(CommandSyntaxException::class)
    override fun parse(reader: StringReader): RoleRetriever {
        val startPos = reader.cursor
        return if (reader.read() == '<' && reader.read() == '@' && reader.read() == '&') {
            val idStr = reader.readStringUntil('>')
            // Better parse now so exception won't be thrown later
            val id = MiscUtil.parseSnowflake(idStr)

            // TODO: replace with proper kotlin alternative
            object : RoleRetriever {
                override fun apply(guild: Guild): Role? = guild.getRoleById(id)
            }
        } else {
            reader.cursor = startPos // Don't forget to reset cursor
            val arg = reader.readString()

            // Holy shit this is ugly as hell
            // TODO: replace with proper kotlin alternative
            object : RoleRetriever {
                override fun apply(guild: Guild): Role? {
                    return guild.getRolesByName(arg, true)
                        .maxWith(Comparator.comparing { r: Role -> r.timeCreated })
                }
            }
        }
    }
}

private object InviteArgumentType : ArgumentType<String> {
    fun invite(): InviteArgumentType {
        return InviteArgumentType
    }

    @Throws(CommandSyntaxException::class)
    override fun parse(reader: StringReader): String {
        // Read until the end or whitespace
        val sb = StringBuilder()
        var c: Char
        while (reader.canRead()) {
            c = reader.read()
            if (c.isWhitespace()) {
                reader.cursor-- // Brigadier expects whitespace after the arg
                break
            }
            sb.append(c)
        }
        var arg = sb.toString()

        // Parse if URL and do nothing if code
        val lastSlash = arg.lastIndexOf('/')
        if (lastSlash >= 0 && lastSlash < arg.length - 1) {
            arg = arg.substring(lastSlash + 1)
        }
        return arg
    }
}