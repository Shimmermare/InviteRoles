package com.shimmermare.inviteroles.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType.string
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.shimmermare.inviteroles.BasicConfiguration
import com.shimmermare.inviteroles.command.InviteArgumentType.invite
import com.shimmermare.inviteroles.command.RoleArgumentType.role
import com.shimmermare.inviteroles.i18n.InternalizationService
import com.shimmermare.inviteroles.logger
import com.shimmermare.inviteroles.message.MessageConfiguration
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service


/**
 * Chat command processing service.
 *
 * Brigadier requires to return magic int - return 1 if you processed the command.
 */
@Service
class CommandService(
        private val jda: JDA,
        private val basicConfiguration: BasicConfiguration,
        private val messageConfiguration: MessageConfiguration,
        private val i18n: InternalizationService,
        private val aboutCommandProcessor: AboutCommandProcessor,
        private val settingsCommandProcessor: SettingsCommandProcessor,
        private val inviteCommandProcessor: InviteCommandProcessor
) : ListenerAdapter() {
    private val log = logger()
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
                                literal("reset").executes(settingsCommandProcessor::processReset)
                        )
                        .then(
                                argument("setting", string())
                                        .then(argument("value", string()).executes(settingsCommandProcessor::processEdit))
                                        .executes(settingsCommandProcessor::processInfo)
                        )
                        .executes(settingsCommandProcessor::processGeneral)
                )
                .then(literal("invites")
                        .requires { it.member.hasPermission(Permission.MANAGE_ROLES) }
                        .then(
                                argument("invite-code", invite())
                                        .then(literal("clear").executes(inviteCommandProcessor::processClear))
                                        .then(
                                                literal("add").then(
                                                        argument("role", role()).executes(inviteCommandProcessor::processAddRole)
                                                )
                                        )
                                        .then(
                                                literal("remove").then(
                                                        argument("role", role()).executes(inviteCommandProcessor::processRemoveRole)
                                                )
                                        )
                                        .executes(inviteCommandProcessor::processInfo)
                        )
                        .executes(inviteCommandProcessor::processGeneral)
                )
                .executes(aboutCommandProcessor::processAbout)
        )

        // Short alias
        dispatcher.register(
                literal("ir")
                        .redirect(root)
                        .executes(aboutCommandProcessor::processAbout) // It's not working if you specify this only in root
        )
    }
}
