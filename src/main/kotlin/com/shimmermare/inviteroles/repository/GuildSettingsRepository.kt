package com.shimmermare.inviteroles.repository

import com.shimmermare.inviteroles.entity.GuildSettings
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

/**
 * Settings for a guild are not deleted even after the bot left.
 *
 * Simple CRUD is automatically managed by Spring.
 */
@Repository
interface GuildSettingsRepository : CrudRepository<GuildSettings, Long>