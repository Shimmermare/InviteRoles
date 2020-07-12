package com.shimmermare.inviteroles.entity

import com.shimmermare.inviteroles.toBooleanExplicit
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "settings")
data class GuildSettings(
    @Id
    @Column(name = "guild_id")
    val guildId: Long,
    @Column(name = NOTIFICATIONS_SETTING, nullable = false)
    val notifications: Boolean = true,
    @Column(name = NOTIFICATION_CHANNEL_SETTING, nullable = true)
    val notificationChannel: Long? = null,
    @Column(name = LANG_SETTING, nullable = false)
    val language: String = "en_US"
) {

    fun getAsStringByName(name: String): String = when (name) {
        NOTIFICATIONS_SETTING -> notifications.toString()
        NOTIFICATION_CHANNEL_SETTING -> notificationChannel.toString()
        LANG_SETTING -> language
        else -> throw IllegalArgumentException("Unknown setting: $name")
    }

    fun setFromStringByName(name: String, value: String): GuildSettings {
        return when (name) {
            NOTIFICATIONS_SETTING -> this.copy(notifications = value.toBooleanExplicit())
            NOTIFICATION_CHANNEL_SETTING -> this.copy(notificationChannel = value.toLong())
            LANG_SETTING -> this.copy(language = value)
            else -> throw IllegalArgumentException("Unknown setting: $name")
        }
    }

    fun getAsStringMap(): Map<String, String> {
        return mapOf(
            NOTIFICATIONS_SETTING to getAsStringByName(NOTIFICATIONS_SETTING),
            NOTIFICATION_CHANNEL_SETTING to getAsStringByName(NOTIFICATION_CHANNEL_SETTING),
            LANG_SETTING to getAsStringByName(LANG_SETTING)
        )
    }

    companion object {
        const val NOTIFICATIONS_SETTING = "notifications"
        const val NOTIFICATION_CHANNEL_SETTING = "notification_channel"
        const val LANG_SETTING = "language"
    }
}