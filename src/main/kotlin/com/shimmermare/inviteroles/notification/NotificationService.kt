package com.shimmermare.inviteroles.notification

import com.shimmermare.inviteroles.logger
import com.shimmermare.inviteroles.message.MessageConfiguration
import com.shimmermare.inviteroles.settings.GuildSettingsService
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import org.springframework.stereotype.Service
import java.util.function.Consumer

@Service
class NotificationService(
        private val settingsService: GuildSettingsService,
        private val messageConfiguration: MessageConfiguration
) {
    private val log = logger()

    fun sendSuccess(guild: Guild, title: String, message: String) {
        val embed = messageConfiguration.createSuccessMessage(guild, title, message)
        send(guild, embed)
    }

    fun sendInfo(guild: Guild, title: String, message: String) {
        val embed = messageConfiguration.createInfoMessage(guild, title, message)
        send(guild, embed)
    }

    fun sendError(guild: Guild, title: String, message: String) {
        val embed = messageConfiguration.createErrorMessage(guild, title, message)
        send(guild, embed)
    }

    private fun send(guild: Guild, notification: MessageEmbed) {
        val settings = settingsService.getOrCreateSettings(guild)
        if (settings.notifications && settings.notificationChannel != null) {
            val channel = guild.getTextChannelById(settings.notificationChannel)
            if (channel == null) {
                log.debug(
                        "Not sending notification to {} because channel {} doesn't exists: {}",
                        guild, settings.notificationChannel, notification.description
                )
                return
            }

            val onSuccess = Consumer<Message> {
                log.debug("Notification sent to {}: {}", guild, notification.description)
            }
            val onFailure = Consumer<Throwable> { throwable ->
                log.info("Failed to send notification to {}: {}", guild, notification.description, throwable)
            }
            channel.sendMessage(notification).queue(onSuccess, onFailure)
        } else {
            log.debug(
                    "Not sending notification to {} because the guild has disabled notifications: {}",
                    guild, notification.description
            )
        }
    }
}