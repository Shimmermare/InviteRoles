package com.shimmermare.inviteroles.invite.tracking

import com.shimmermare.inviteroles.i18n.InternalizationService
import com.shimmermare.inviteroles.logger
import com.shimmermare.inviteroles.notification.NotificationService
import com.shimmermare.inviteroles.role.RoleGranterService
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap


@Service
class InviteJoinTrackingService(
        private val jda: JDA,
        private val notificationService: NotificationService,
        private val i18n: InternalizationService,
        private val roleGranterService: RoleGranterService
) : ListenerAdapter() {
    private val log = logger()

    private val trackers = ConcurrentHashMap<Guild, InviteJoinTracker>()

    @EventListener
    fun onApplicationEvent(event: ContextRefreshedEvent) {
        val joinedGuilds = jda.guilds
        log.info("Setting up invite join tracking for {} guilds", joinedGuilds.size)
        joinedGuilds.forEach { createTracker(it) }

        log.info("Starting listening to Discord events")
        jda.addEventListener(this)
    }

    @EventListener
    fun onApplicationEvent(event: ContextClosedEvent) {
        log.info("Stopping listening to Discord events")
        jda.removeEventListener(this)
    }

    override fun onGuildJoin(event: GuildJoinEvent) {
        createTracker(event.guild)
        log.info("Guild {} joined, creating invite join tracker", event.guild)
    }

    override fun onGuildLeave(event: GuildLeaveEvent) {
        if (removeTracker(event.guild)) {
            log.info("Guild {} left, removing invite join tracker", event.guild)
        }
    }

    private fun createTracker(guild: Guild): InviteJoinTracker {
        val tracker = InviteJoinTracker(guild,
                onJoinSure = { member, invite ->
                    log.debug(
                            "Determined that member {} joined guild {} with invite {}",
                            member, member.guild, invite
                    )
                    roleGranterService.grantRolesIfNeeded(member, invite)
                },
                onJoinUnsure = { member, invites ->
                    log.debug(
                            "Can't determine the exact invite used when member {} joined guild {}: candidates are: {}",
                            member, member.guild, invites
                    )
                    val title = i18n.apply(guild, "join_tracking.notification.ambiguous_join_event.title")
                    val message = i18n.apply(
                            guild, "join_tracking.notification.ambiguous_join_event.message",
                            "member" to "${member.effectiveName} (${member.id})",
                            "candidates" to invites.joinToString()
                    )
                    notificationService.sendInfo(guild, title, message)
                }
        )

        jda.addEventListener(tracker)
        trackers[guild] = tracker
        return tracker
    }

    private fun removeTracker(guild: Guild): Boolean {
        trackers.remove(guild)?.let { tracker ->
            jda.removeEventListener(tracker)
            return true
        }
        return false
    }
}