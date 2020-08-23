package com.shimmermare.inviteroles.command

import com.mojang.brigadier.context.CommandContext
import com.shimmermare.inviteroles.BasicConfiguration
import com.shimmermare.inviteroles.i18n.InternalizationService
import com.shimmermare.inviteroles.logger
import com.shimmermare.inviteroles.message.MessageConfiguration
import org.springframework.stereotype.Component

@Component
class AboutCommandProcessor(
        private val basicConfiguration: BasicConfiguration,
        private val messageConfiguration: MessageConfiguration,
        private val i18n: InternalizationService
) {
    private val log = logger()

    /**
     * /ir
     */
    fun processAbout(context: CommandContext<CommandSource>): Int {
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
}