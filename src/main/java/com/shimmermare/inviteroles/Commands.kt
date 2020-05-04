package com.shimmermare.inviteroles

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.BoolArgumentType.bool
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.utils.MiscUtil
import org.slf4j.LoggerFactory
import java.util.*
import java.util.function.Function

class CommandSource(val bot: InviteRoles, val guild: BotGuild, val channel: TextChannel, val member: Member)

/**
 * Bot commands.
 *
 * Brigadier requires to return magic int - return 1 if you processed the command somehow.
 */
object Commands {
    private val LOGGER = LoggerFactory.getLogger(Commands::class.java)

    fun register(commandDispatcher: CommandDispatcher<CommandSource>) {
        val root = commandDispatcher.register(literal("inviteroles")
            .then(literal("settings")
                .requires { it.member.hasPermission(Permission.ADMINISTRATOR) }
                .then(
                    // TODO: generalize settings command to <setting, value> pair.
                    literal("warnings")
                        .then(
                            argument("value", bool())
                                .executes(::warningsSet)
                        )
                )
                .executes(::printSettings)
            )
            .then(literal("invite")
                .requires { s -> s.member.hasPermission(Permission.MANAGE_ROLES) }
                .then(
                    argument("invite-code", InviteArgumentType.invite())
                        .then(literal("remove").executes(::inviteRemove))
                        .then(argument("role", RoleArgumentType.role()).executes(::inviteSet))
                )
                .executes(::printInvites)
            )
            .then(
                literal("invites")
                    .executes(::printInvites)
            )
        )

        // Short alias
        commandDispatcher.register(
            literal("ir")
                .redirect(root)
        )
    }

    private fun printSettings(context: CommandContext<CommandSource>): Int {
        val source = context.source
        val guild = source.guild
        val settings = guild.settings

        val fields = listOf(
            MessageEmbed.Field("Warnings", settings.warnings.toString(), false)
        )
        val message = source.bot.createInfoMessage("**Current settings**", fields = fields)
        source.channel.sendMessage(message).queue()
        LOGGER.debug("(guild: {}, user: {}): Printed current settings", guild.id, source.member.idLong)
        return 1
    }

    private fun warningsSet(context: CommandContext<CommandSource>): Int {
        val source = context.source
        val guild = source.guild
        val warnings = context.getArgument("value", Boolean::class.java)
        guild.settings = guild.settings.copy(warnings = warnings)
        source.channel.sendMessage("Warnings set to **`$warnings`**.").queue()
        LOGGER.debug("(guild: {}, user: {}): Warning status set to {}", guild.id, source.member.idLong, warnings)
        return 1
    }

    private fun printInvites(context: CommandContext<CommandSource>): Int {
        val source = context.source
        val guild = source.guild

        val fields = guild.getInvites().map { invite ->
            MessageEmbed.Field(
                invite.code.censorLast(),
                "**${guild.guild.getRoleById(invite.roleId)?.name}**",
                false
            )
        }
        val message = if (fields.isEmpty()) {
            source.bot.createInfoMessage("**No active invites with roles**")
        } else {
            source.bot.createInfoMessage("**Invites with active roles**", fields = fields)
        }
        source.channel.sendMessage(message).queue()
        LOGGER.debug("(guild: {}, user: {}): Printed active invites", guild.id, source.member.idLong)
        return 1
    }

    private fun inviteRemove(context: CommandContext<CommandSource>): Int {
        val source = context.source
        val guild = source.guild
        val channel = source.channel
        val inviteCode = context.getArgument("invite-code", String::class.java)

        val removed = guild.removeInvite(inviteCode)
        if (removed == null) {
            channel.sendMessage("Invite `${inviteCode.censorLast()}` is not used for roles.")
                .queue()
            LOGGER.debug(
                "(guild: {}, user: {}): Tried to remove non-existent invite {}",
                source.member.idLong, guild.id, inviteCode
            )
            return 1
        }

        channel.sendMessage("Roles were cleared from invite `${inviteCode.censorLast()}`.").queue()
        LOGGER.debug("(guild: {}, user: {}): Removed invite {}", guild.id, source.member.idLong, inviteCode)
        return 1
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
            return 1
        }

        if (!doesInviteExists(guild, inviteCode)) {
            channel.sendMessage("Invite `${inviteCode.censorLast()}` doesn't exist!").queue()
            LOGGER.debug(
                "(guild: {}, user: {}): Tried to set role {} for non-existent invite {}",
                guild.id, member.idLong, role.idLong, inviteCode
            )
            return 1
        }

        if (!guild.guild.selfMember.canInteract(role)) {
            channel.sendMessage("Bot doesn't have permissions to interact with `${role.name}` role.").queue()
            LOGGER.debug(
                "(guild: {}, user: {}): Tried to set role {} which bot doesn't have permissions for.",
                guild.id, member.idLong, role.idLong
            )
            return 1
        }

        guild.addInvite(BotGuildInvite(inviteCode, guild.id, role.idLong))
        channel.sendMessage("Role `${role.name}` is set for invite `${inviteCode.censorLast()}`.").queue()
        LOGGER.debug(
            "(guild: {}, user: {}): Role {} is set to invite {}", guild.id, member.idLong, inviteCode, role.idLong
        )
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