package com.shimmermare.inviteroles

import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import java.io.File

@Configuration
class BasicConfiguration(
        private val environment: Environment
) {
    val version: String
        get() = environment.getProperty("version", "unknown")

    val defaultLocaleCode: String
        get() = environment.getProperty("default_locale_code", "en_US")

    val defaultLocalesSearchPattern: String
        get() = environment.getProperty("default_locales_search_pattern", "classpath*:locales/*.properties")

    val userLocalesSearchPattern: String
        get() = environment.getProperty(
                "user_locales_search_pattern",
                "file:${File("").absolutePath}/locales/*.properties"
        )

    val commandPrefix: String
        get() = environment.getProperty("command_prefix", "/")

    val githubUrl: String
        get() = environment.getProperty("github_url", "https://github.com/shimmermare/InviteRoles")

    val author: BotAuthor
        get() = BotAuthor(
                name = environment.getProperty("author.name", "Shimmermare"),
                twitterUrl = environment.getProperty("author.twitter_url", "n/a"),
                githubUrl = environment.getProperty("author.github_url", "n/a"),
                websiteUrl = environment.getProperty("author.website_url", "n/a"),
                avatarUrl = environment.getProperty(
                        "author.avatar_url",
                        "https://avatars0.githubusercontent.com/u/53906850"
                )
        )
}

data class BotAuthor(
        val name: String,
        val twitterUrl: String,
        val githubUrl: String,
        val websiteUrl: String,
        val avatarUrl: String
)