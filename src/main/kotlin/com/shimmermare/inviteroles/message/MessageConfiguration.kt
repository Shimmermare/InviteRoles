package com.shimmermare.inviteroles.message

import com.shimmermare.inviteroles.BasicConfiguration
import com.shimmermare.inviteroles.i18n.InternalizationService
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.MessageEmbed
import org.springframework.context.annotation.Configuration
import java.awt.Color

@Configuration
class MessageConfiguration(
        private val basicConfiguration: BasicConfiguration,
        private val i18n: InternalizationService
) {
    private fun getFooterMessage(guild: Guild): String {
        return i18n.apply(
                guild, "message.footer",
                "version" to basicConfiguration.version,
                "author" to basicConfiguration.author.name
        )
    }

    fun createInfoMessage(
            guild: Guild,
            title: String,
            description: String = "",
            fields: Map<String, String> = emptyMap()
    ): MessageEmbed {
        val builder = EmbedBuilder()
                .setColor(Color.CYAN)
                .setFooter(getFooterMessage(guild), basicConfiguration.author.avatarUrl)
                .setTitle(title)
                .setDescription(description)
        fields.forEach { builder.addField(it.key, it.value, false) }
        return builder.build()
    }

    fun createSuccessMessage(
            guild: Guild,
            title: String,
            description: String = "",
            fields: Map<String, String> = emptyMap()
    ): MessageEmbed {
        val builder = EmbedBuilder()
                .setColor(Color.GREEN)
                .setFooter(getFooterMessage(guild), basicConfiguration.author.avatarUrl)
                .setTitle(title)
                .setDescription(description)
        fields.forEach { builder.addField(it.key, it.value, false) }
        return builder.build()
    }

    fun createErrorMessage(
            guild: Guild,
            title: String,
            description: String = "",
            fields: Map<String, String> = emptyMap()
    ): MessageEmbed {
        val builder = EmbedBuilder()
                .setColor(Color.RED)
                .setFooter(getFooterMessage(guild), basicConfiguration.author.avatarUrl)
                .setTitle(title)
                .setDescription(description)
        fields.forEach { builder.addField(it.key, it.value, false) }
        return builder.build()
    }
}