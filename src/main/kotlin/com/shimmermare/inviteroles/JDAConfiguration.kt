package com.shimmermare.inviteroles

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import javax.security.auth.login.LoginException

@Configuration
class JDAConfiguration(
        private val environment: Environment
) {
    private val log = logger<JDAConfiguration>()

    @Bean
    fun jda(): JDA {
        val token = environment.getRequiredProperty("discord.token")
        val builder = createBuilder(token)
        try {
            log.info("Logging in Discord...")
            val jda = builder.build()
            jda.awaitReady()
            log.info("Logged in as ${jda.selfUser.name} (${jda.selfUser.id}).")
            return jda
        } catch (e: LoginException) {
            log.error("Failed to login to Discord!", e)
            throw e
        }
    }

    private fun createBuilder(token: String): JDABuilder {
        return JDABuilder
                .create(
                        token,
                        // FYI: Discord will limit bot to only 100 guilds because of the line below.
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.GUILD_INVITES,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.DIRECT_MESSAGES
                )
                .disableCache(CacheFlag.ACTIVITY)
                .disableCache(CacheFlag.CLIENT_STATUS)
                .disableCache(CacheFlag.VOICE_STATE)
                .disableCache(CacheFlag.EMOTE)
    }
}
