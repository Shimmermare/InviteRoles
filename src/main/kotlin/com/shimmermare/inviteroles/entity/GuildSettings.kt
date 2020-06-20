package com.shimmermare.inviteroles.entity

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

const val NOTIFICATIONS_SETTING = "notifications"
const val NOTIFICATION_CHANNEL_SETTING = "notification_channel"
const val LANG_SETTING = "language"

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
)