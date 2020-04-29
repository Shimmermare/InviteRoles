package com.shimmermare.inviteroles

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.utils.MiscUtil
import org.slf4j.LoggerFactory
import java.awt.Color
import java.util.*
import java.util.function.Function

class CommandSource(val bot: InviteRoles, val guild: BotGuild, val channel: TextChannel, val member: Member)

/**
 * Bot commands.
 *
 * Brigadier requires to return magic int after command is finished.
 * Convention for this is:
 * Right half of low 16 bits is sub-command id.
 * Left half or high 16 bits is result of sub-command.
 */
object Commands {
    private val LOGGER = LoggerFactory.getLogger(Commands::class.java)

    @JvmStatic
    fun register(commandDispatcher: CommandDispatcher<CommandSource>) {
        commandDispatcher.register(literal("inviteroles")
            .then(literal("warnings")
                .requires { s -> s.member.hasPermission(Permission.ADMINISTRATOR) }
                .then(literal("on").executes { c -> warningsSet(c, true) })
                .then(literal("enable").executes { c -> warningsSet(c, true) })
                .then(literal("off").executes { c -> warningsSet(c, false) })
                .then(literal("disable").executes { c -> warningsSet(c, false) })
                .executes(::warningsStatus)
            )
            .then(argument("invite-code", InviteArgumentType.invite())
                .requires { s -> s.member.hasPermission(Permission.MANAGE_ROLES) }
                .then(literal("remove").executes(::inviteRemove))
                .then(argument("role", RoleArgumentType.role()).executes(::inviteSet))
                .executes(::inviteNoArg)
            )
            .executes(::executeNoArg)
        )
    }

    private fun warningsSet(context: CommandContext<CommandSource>, enabled: Boolean): Int {
        val source = context.source
        source.guild.settings.warnings = enabled
        source.channel.sendMessage("Warnings now `${if (enabled) "enabled" else "disabled"}`.").queue()
        LOGGER.debug("(guild: {}, user: {}): Warning status set to {}", source.guild.id, source.member.idLong, enabled)
        return 5
    }

    private fun warningsStatus(context: CommandContext<CommandSource>): Int {
        val source = context.source

        source.channel.sendMessage("Warnings are `${if (source.guild.settings.warnings) "enabled" else "disabled"}`.")
            .queue()
        LOGGER.debug("Warnings status on server {} requested by user {}", source.guild.id, source.member.idLong)
        return 4
    }

    private fun inviteRemove(context: CommandContext<CommandSource>): Int {
        val source = context.source
        val guild = source.guild
        val channel = source.channel
        val inviteCode = context.getArgument("invite-code", String::class.java)

        val removed = guild.invites.remove(inviteCode)
        if (removed == null) {
            channel.sendMessage("Invite `${hideInvite(inviteCode)}` is not used for roles.")
                .queue()
            LOGGER.debug(
                "(guild: {}, user: {}): Tried to remove non-existent invite {}",
                source.member.idLong, guild.id, inviteCode
            )
            return 1 shl 16 or 3
        }

        channel.sendMessage("Roles were cleared from invite `${hideInvite(inviteCode)}`.").queue()
        LOGGER.debug("(guild: {}, user: {}): Removed invite {}", guild.id, source.member.idLong, inviteCode)
        return 3
    }

    private fun inviteSet(context: CommandContext<CommandSource>): Int {
        val source = context.source
        val guild = source.guild
        val channel = source.channel
        val member = source.member

        val inviteCode = context.getArgument("invite-code", String::class.java)
        val roleRetriever = context.getArgument("role", RoleRetriever::class.java)
        val role = roleRetriever.apply(guild.guild)

        if (role == null) {
            channel.sendMessage("Role doesn't exist! Use \"quotes\" in case role name contains whitespaces.").queue()
            LOGGER.debug(
                "(guild: {}, user: {}): Tried to set non-existent role for invite {}",
                guild.id, member.idLong, inviteCode
            )
            return 3 shl 16 or 2
        }

        if (!doesInviteExists(guild, inviteCode)) {
            channel.sendMessage("Invite `${hideInvite(inviteCode)}` doesn't exist!").queue()
            LOGGER.debug(
                "(guild: {}, user: {}): Tried to set role {} for non-existent invite {}",
                guild.id, member.idLong, role.idLong, inviteCode
            )
            return 4 shl 16 or 2
        }

        if (!guild.guild.selfMember.canInteract(role)) {
            channel.sendMessage("Bot doesn't have permissions to interact with `${role.name}` role.").queue()
            LOGGER.debug(
                "(guild: {}, user: {}): Tried to set role {} which bot doesn't have permissions for.",
                guild.id, member.idLong, role.idLong
            )
            return 5 shl 16 or 2
        }

        guild.invites.put(BotGuildInvite(inviteCode, guild.id, role.idLong))
        channel.sendMessage("Role `${role.name}` is set for invite `${hideInvite(inviteCode)}`.").queue()
        LOGGER.debug(
            "(guild: {}, user: {}): Role {} is set to invite {}", guild.id, member.idLong, inviteCode, role.idLong
        )
        return 2
    }

    private fun inviteNoArg(context: CommandContext<CommandSource>): Int {
        val source = context.source
        val member = source.member
        val channel = source.channel
        val guild = source.guild

        val inviteCode = context.getArgument("invite-code", String::class.java)

        val invite = guild.invites.get(inviteCode)
        if (invite == null) {
            channel.sendMessage("No role set for invite `${hideInvite(inviteCode)}`.").queue()
            return 1 shl 16 or 7
        }


        val role = guild.guild.getRoleById(invite.roleId)
        if (role == null) {
            channel.sendMessage("Role that is set for invite `${hideInvite(inviteCode)}` doesn't exist.")
                .queue()
            LOGGER.error(
                "(guild: {}, user: {}): Unexpectedly role {} for invite {} doesn't exist",
                guild.id, member.id, invite.roleId, inviteCode
            )
            return 2 shl 16 or 7
        }

        channel.sendMessage("Role for invite `${hideInvite(inviteCode)}` is `${role.name}`.").queue()
        LOGGER.debug("(guild: {}, user: {}): Requested role for invite {}", guild.id, member.id, inviteCode)
        return 7
    }

    private fun executeNoArg(context: CommandContext<CommandSource>): Int {
        val source = context.source
        val member = source.member
        val channel = source.channel
        val guild = source.guild

        val builder = StringBuilder()
        builder.append("Warnings: ").append(if (guild.settings.warnings) "enabled" else "disabled").append('.')

        val invites = guild.invites.getAll()
        if (invites.isEmpty()) {
            builder.append("\nNo invites used.")
        } else {
            invites.forEach { invite ->
                val role = guild.guild.getRoleById(invite.roleId)
                builder
                    .append("\n• ")
                    .append(hideInvite(invite.code))
                    .append(" / ")
                    .append(role?.name ?: "deleted role")
            }
        }

        val embedBuilder = EmbedBuilder()
            .setAuthor("Current Settings")
            .setColor(Color.MAGENTA)
            .setDescription(builder)
            .setFooter("InviteRole by Shimmermare")
        channel.sendMessage(embedBuilder.build()).queue()
        LOGGER.debug("(guild: {}, user: {}): Requested settings", guild.id, member.idLong)
        return 1
    }

    // Blocking but since it's in command I think it's okay
    private fun doesInviteExists(guild: BotGuild, code: String): Boolean {
        return guild.guild.retrieveInvites().complete().any { i -> i.code == code }
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
}