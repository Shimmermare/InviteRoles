package com.shimmermare.inviteroles.privatemessages

import com.shimmermare.inviteroles.BasicConfiguration
import com.shimmermare.inviteroles.i18n.InternalizationService
import com.shimmermare.inviteroles.logger
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

/**
 * Handle bot's direct messages.
 *
 * For now only reply with generic thank you and send github links.
 */
@Service
class PrivateMessagingService(
        private val jda: JDA,
        private val basicConfiguration: BasicConfiguration,
        private val i18n: InternalizationService
) : ListenerAdapter() {
    private val log = logger()

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

    override fun onPrivateMessageReceived(event: PrivateMessageReceivedEvent) {
        val author = event.author
        log.info("Received a private message from {}: {}", author, event.message.contentRaw)
        if (author.isBot || author.isFake) return

        val response = i18n.applyDefault("message.private.thank_you", "github" to basicConfiguration.githubUrl)
        event.channel.sendMessage(response).queue()
    }
}