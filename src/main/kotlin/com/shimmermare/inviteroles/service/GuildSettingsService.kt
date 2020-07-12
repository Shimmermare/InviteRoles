package com.shimmermare.inviteroles.service

import com.shimmermare.inviteroles.asNullable
import com.shimmermare.inviteroles.entity.GuildSettings
import com.shimmermare.inviteroles.repository.GuildSettingsRepository
import net.dv8tion.jda.api.entities.Guild
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GuildSettingsService(
    private val repository: GuildSettingsRepository
) {
    @Transactional
    fun getOrCreateSettings(guildId: Long): GuildSettings {
        var settings = repository.findById(guildId).asNullable()
        if (settings == null) {
            settings = GuildSettings(guildId)
            repository.save(settings)
        }
        return settings
    }

    @Transactional
    fun getOrCreateSettings(guild: Guild): GuildSettings = getOrCreateSettings(guild.idLong)

    @Transactional
    fun modifySettings(guildId: Long, block: (GuildSettings) -> GuildSettings): GuildSettings {
        val old = getOrCreateSettings(guildId)
        val new = block.invoke(old)
        if (guildId != new.guildId) {
            throw IllegalStateException("Guild settings guild id change is not allowed")
        }
        repository.save(new)
        return new
    }

    @Transactional
    fun modifySettings(guild: Guild, block: (GuildSettings) -> GuildSettings) =
        modifySettings(guild.idLong, block)
}