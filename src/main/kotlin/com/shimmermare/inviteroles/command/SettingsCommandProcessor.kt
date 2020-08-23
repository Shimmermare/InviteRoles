package com.shimmermare.inviteroles.command

import com.mojang.brigadier.context.CommandContext
import com.shimmermare.inviteroles.i18n.InternalizationService
import com.shimmermare.inviteroles.logger
import com.shimmermare.inviteroles.message.MessageConfiguration
import com.shimmermare.inviteroles.settings.GuildSettings
import com.shimmermare.inviteroles.settings.GuildSettingsService
import org.springframework.stereotype.Component

@Component
class SettingsCommandProcessor(
        private val i18n: InternalizationService,
        private val messageConfiguration: MessageConfiguration,
        private val settingsService: GuildSettingsService
) {
    private val log = logger()

    /**
     * /ir settings
     */
    fun processGeneral(context: CommandContext<CommandSource>): Int {
        val source = context.source
        val guild = source.guild
        val settings = settingsService.getOrCreateSettings(guild)

        val title = i18n.apply(guild, "command.settings.info_all.title")
        val fields = settings.getAsStringMap()
        val message = messageConfiguration.createInfoMessage(guild, title, fields = fields)
        source.channel.sendMessage(message).queue()
        log.info("(guild: {}, user: {}): Requested current settings", guild, source.member)
        return 1
    }

    /**
     * /ir settings reset
     */
    fun processReset(context: CommandContext<CommandSource>): Int {
        val source = context.source
        val guild = source.guild
        settingsService.modifySettings(guild) {
            return@modifySettings GuildSettings(guildId = guild.idLong)
        }
        val title = i18n.apply(guild, "command.settings.reset.title")
        val desc = i18n.apply(guild, "command.settings.reset.description")
        val message = messageConfiguration.createSuccessMessage(guild, title, desc)
        source.channel.sendMessage(message).queue()
        log.info("(guild: {}, user: {}): Requested current settings", guild, source.member)
        return 1
    }

    /**
     * /ir settings <setting>
     */
    fun processInfo(context: CommandContext<CommandSource>): Int {
        val source = context.source
        val guild = source.guild
        val setting = context.getArgument("setting", String::class.java)

        val title = i18n.apply(guild, "command.settings.info.title")

        val settings = settingsService.getOrCreateSettings(guild)
        val value: String
        try {
            value = settings.getAsStringByName(setting)
        } catch (e: IllegalArgumentException) {
            val desc = i18n.apply(guild, "command.settings.info.error.not_found.description", "setting" to setting)
            val message = messageConfiguration.createErrorMessage(guild, title, desc)
            source.channel.sendMessage(message).queue()
            log.info("(guild: {}, user: {}): Requested unknown setting {}", guild, source.member, setting)
            return 1
        }

        val desc = i18n.apply(
                guild, "command.settings.info.success.description",
                "setting" to setting, "value" to value
        )
        val message = messageConfiguration.createInfoMessage(guild, title, desc)
        source.channel.sendMessage(message).queue()
        log.info("(guild: {}, user: {}): Requested setting {}", guild, source.member, setting)
        return 1
    }

    /**
     * /ir settings <setting> <value>
     */
    fun processEdit(context: CommandContext<CommandSource>): Int {
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

        val settings = settingsService.modifySettings(guild) {
            it.setFromStringByName(setting, value)
        }
        val newValue = settings.getAsStringByName(setting)

        val message = messageConfiguration.createSuccessMessage(
                guild,
                title = i18n.apply(guild, "command.settings.edit.success.title"),
                description = i18n.apply(
                        guild,
                        "command.settings.edit.success.description",
                        "setting" to setting /* no nulls */,
                        "value" to newValue
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
}