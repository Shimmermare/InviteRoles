package com.shimmermare.inviteroles.repository

import com.shimmermare.inviteroles.entity.GuildSettings
import javax.persistence.EntityManager

class GuildSettingsRepositoryImpl(
    private val em: EntityManager
) : GuildSettingsRepository {
    override fun get(guildId: Long): GuildSettings? {
        return em.find(GuildSettings::class.java, guildId)
    }

    override fun set(settings: GuildSettings) {
        if (em.contains(settings)) {
            em.merge(settings)
        } else {
            em.persist(settings)
        }
    }

    override fun delete(guildId: Long) {
        val entity = get(guildId)
        if (entity != null) {
            em.remove(entity)
            em.flush()
        }
    }
}