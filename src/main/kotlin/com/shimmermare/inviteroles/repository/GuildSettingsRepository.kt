package com.shimmermare.inviteroles.repository

import com.shimmermare.inviteroles.entity.GuildSettings
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface GuildSettingsRepository {
    @Transactional(readOnly = true)
    fun get(guildId: Long): GuildSettings?

    @Transactional
    fun set(settings: GuildSettings)

    @Transactional
    fun delete(guildId: Long)
}