package com.shimmermare.inviteroles.configuration

import com.shimmermare.inviteroles.repository.GuildSettingsRepositoryImpl
import com.shimmermare.inviteroles.repository.TrackedInviteRepositoryImpl
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.persistence.EntityManager

@Configuration
class RepositoryConfiguration {
    @Bean
    fun guildSettingsRepository(entityManager: EntityManager) = GuildSettingsRepositoryImpl(entityManager)

    @Bean
    fun trackedInviteRepository(entityManager: EntityManager) = TrackedInviteRepositoryImpl(entityManager)
}