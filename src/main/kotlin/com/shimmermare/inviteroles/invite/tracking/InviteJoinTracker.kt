package com.shimmermare.inviteroles.invite.tracking

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


/**
 * Tracks which invite was used when a user joined the guild.
 *
 * There's three cases:
 *
 * Invite A was used with 99.9% chance:
 *   1)
 *     A: 0 -> 1
 *     B: 0 -> 0
 *   2) There's a possibility that no invite was used.
 *     A: none -> 1
 *     B: 0 -> 0
 *   3) There's a possibility that no invite was used.
 *     A: 9 -> none
 *     B: 0 -> 0
 *
 * Can't confidently say what invite was used:
 *   1)
 *     A: 0 -> 1
 *     B: 0 -> 1
 *   2)
 *     A: none -> 1
 *     B: none -> 1
 *   3)
 *     A: 9 -> none
 *     B: 9 -> none
 *   4)
 *     A: 9 -> none
 *     B: none -> 1
 *
 * No invite was used - user joined through guild discovery or some other dark magic.
 */
class InviteJoinTracker(
        private val guild: Guild,
        /**
         * Called when a member joins the server and tracker
         * can with 99.99% confidence say which invite was used.
         */
        val onJoinSure: (member: Member, invite: String) -> Any,
        /**
         * Called when a member joins the server and tracker
         * can't reliably determine used invite.
         */
        val onJoinUnsure: (member: Member, invites: List<String>) -> Any
) : ListenerAdapter() {
    /**
     * Lock it to not take any chance.
     */
    private val lock = ReentrantLock()

    private var invites: Map<String, Int> = lock.withLock { retrieveInvites() }

    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        // Tracker is working on per-guild basis.
        if (event.guild != guild) return;

        lock.withLock {
            val currentInvites = retrieveInvites()
            val delta = findDelta(invites, currentInvites)
            invites = currentInvites

            if (delta.used.isNotEmpty()) {
                if (delta.used.size == 1 && delta.removed.isEmpty()) {
                    onJoinSure.invoke(event.member, delta.used.keys.first())
                } else {
                    onJoinUnsure.invoke(event.member, delta.used.keys.toList() + delta.removed)
                }
            } else if (delta.removed.isNotEmpty()) {
                // Not 100%, but close enough
                if (delta.removed.size == 1) {
                    onJoinSure.invoke(event.member, delta.removed.first())
                } else {
                    onJoinUnsure.invoke(event.member, delta.removed)
                }
            }
            return@withLock // To avoid using if above as an expression
        }
    }

    private fun findDelta(before: Map<String, Int>, after: Map<String, Int>): Delta {
        val removed = ArrayList<String>()
        val used = HashMap<String, Int>()

        before.forEach { (code, _) ->
            if (!after.containsKey(code)) {
                removed.add(code)
            }
        }
        after.forEach { (code, uses) ->
            if (before.containsKey(code)) {
                val deltaUses = uses - (before[code] ?: 0) // Stupid warning
                if (deltaUses != 0) used[code] = deltaUses
            } else {
                if (uses != 0) used[code] = uses
            }
        }

        return Delta(removed, used)
    }

    /**
     * Retrieve invites !!!synchronously!!!.
     */
    private fun retrieveInvites(): Map<String, Int> {
        return guild.retrieveInvites().complete().asSequence().map { it.code to it.uses }.toMap()
    }

    private data class Delta(
            val removed: List<String>,
            val used: Map<String, Int>
    )
}