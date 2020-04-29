package com.shimmermare.inviteroles

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.events.guild.invite.GuildInviteDeleteEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent
import net.dv8tion.jda.api.events.role.RoleDeleteEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val LOGGER: Logger = LoggerFactory.getLogger(EventListener::class.java)

class EventListener(private val bot: InviteRoles) : ListenerAdapter() {
    override fun onGuildJoin(event: GuildJoinEvent) {
        val guild = event.guild

        if (bot.onGuildJoin(guild)) {
            LOGGER.info("Joined guild {} ({}) again. Welcome back!", guild.name, guild.id)
        } else {
            LOGGER.info("Joined guild {} ({}) for the first time. Hi!", guild.name, guild.id)
        }

        val props = bot.properties
        val welcome = EmbedBuilder()
            .setTitle("**Hello! I'm InviteRoles bot!**", props.getProperty("github_page"))
            .setDescription("You can use me to automatically grant roles based on the invite user joined with.")
            .addField("Check GitHub page for commands:", props.getProperty("github_page"), false)
            .setAuthor(
                props.getProperty("author.name"),
                props.getProperty("author.url"),
                props.getProperty("author.icon_url")
            )
            .setThumbnail(props.getProperty("avatar_url"))
            .build()
        guild.systemChannel?.sendMessage(welcome)
    }

    override fun onGuildLeave(event: GuildLeaveEvent) {
        bot.removeGuild(event.guild.idLong)
        LOGGER.info("Left guild {} ({}). Bye!", event.guild.name, event.guild.id)
    }

    override fun onGuildInviteDelete(event: GuildInviteDeleteEvent) {
        val guild = bot.getGuildOrThrow(event.guild)
        guild.onInviteDelete(event.code)
    }

    override fun onRoleDelete(event: RoleDeleteEvent) {
        val guild = bot.getGuildOrThrow(event.guild)
        guild.onRoleDelete(event.role)
    }

    override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) {
        val guild = bot.getGuildOrThrow(event.guild)
        guild.onMessageReceived(event.message)
    }

    override fun onPrivateMessageReceived(event: PrivateMessageReceivedEvent) {
        val author = event.author
        if (author.isBot || author.isFake) return
        event.channel.sendMessage(
            "**Thank you for feedback!** " +
                    "\n• If you found a bug or have a suggestion, " +
                    "please create an issue at GitHub page: ${bot.properties.getProperty("github_page")}" +
                    "\n• If you want to contact creator, add him in Discord: " +
                    bot.properties.getProperty("author.discord_code")
        ).queue()
    }

    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        val guild = bot.getGuildOrThrow(event.guild)
        guild.onMemberJoin(event.member)
    }
}