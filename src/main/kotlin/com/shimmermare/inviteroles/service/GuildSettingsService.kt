package com.shimmermare.inviteroles.service

import com.shimmermare.inviteroles.entity.GuildSettings
import com.shimmermare.inviteroles.entity.LANG_SETTING
import com.shimmermare.inviteroles.entity.NOTIFICATIONS_SETTING
import com.shimmermare.inviteroles.entity.NOTIFICATION_CHANNEL_SETTING
import com.shimmermare.inviteroles.repository.GuildSettingsRepository
import com.shimmermare.inviteroles.toBooleanExplicit
import net.dv8tion.jda.api.entities.Guild
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GuildSettingsService(
    private val repository: GuildSettingsRepository
) {
    @Transactional(readOnly = true)
    fun getSettings(guildId: Long): GuildSettings? {
        return repository.get(guildId)
    }

    @Transactional
    fun getSettings(guild: Guild): GuildSettings? = getSettings(guild.idLong)

    @Transactional
    fun getOrCreateSettings(guildId: Long): GuildSettings {
        var settings = getSettings(guildId)
        if (settings == null) {
            settings = GuildSettings(guildId)
            saveSettings(settings)
        }
        return settings
    }

    @Transactional
    fun getOrCreateSettings(guild: Guild): GuildSettings = getOrCreateSettings(guild.idLong)

    @Transactional
    fun saveSettings(settings: GuildSettings) {
        repository.set(settings)
    }

    @Transactional
    fun deleteSettings(settings: GuildSettings) {
        deleteSettings(settings.guildId)
    }

    @Transactional
    fun deleteSettings(guildId: Long) {
        repository.delete(guildId)
    }

    @Transactional
    fun modifySettings(settings: GuildSettings, block: (GuildSettings) -> GuildSettings): GuildSettings {
        val newSettings = block.invoke(settings)
        saveSettings(newSettings)
        return newSettings
    }

    @Transactional
    fun modifySettings(guildId: Long, block: (GuildSettings) -> GuildSettings) =
        modifySettings(getOrCreateSettings(guildId), block)

    @Transactional
    fun modifySettings(guild: Guild, block: (GuildSettings) -> GuildSettings) =
        modifySettings(guild.idLong, block)

    @Transactional
    fun setSettingFromString(settings: GuildSettings, setting: String, value: String): GuildSettings {
        return modifySettings(settings) {
            when (setting) {
                NOTIFICATIONS_SETTING -> {
                    settings.copy(notifications = value.toBooleanExplicit())
                }
                NOTIFICATION_CHANNEL_SETTING -> {
                    settings.copy(notificationChannel = value.toLong())
                }
                LANG_SETTING -> {
                    settings.copy(language = value) // TODO verify language code
                }
                else -> settings
            }
        }
    }

    @Transactional
    fun setSettingFromString(guildId: Long, setting: String, value: String) =
        setSettingFromString(getOrCreateSettings(guildId), setting, value)

    @Transactional
    fun setSettingFromString(guild: Guild, setting: String, value: String) =
        setSettingFromString(guild.idLong, setting, value)

    @Transactional
    fun getSettingByString(settings: GuildSettings, setting: String): String? {
        return when (setting) {
            NOTIFICATIONS_SETTING -> settings.notifications.toString()
            NOTIFICATION_CHANNEL_SETTING -> settings.notificationChannel.toString()
            LANG_SETTING -> settings.language
            else -> null
        }
    }

    @Transactional
    fun getSettingByString(guildId: Long, setting: String) =
        getSettingByString(getOrCreateSettings(guildId), setting)

    @Transactional
    fun getSettingByString(guild: Guild, setting: String) =
        getSettingByString(guild.idLong, setting)
}