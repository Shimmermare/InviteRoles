package com.shimmermare.inviteroles

import net.dv8tion.jda.api.entities.Guild

class InviteTracker(private val guild: Guild) {
    private var previous: Map<String, Int> = retrieveInvites()

    /**
     * New invite uses since the last tracker update.
     * Guaranteed to not have 0 or negative values.
     */
    var newUses: Map<String, Int> = emptyMap()
        private set

    fun update() {
        val invites = retrieveInvites()
        // Deleted invites doesn't count, i.e. no negative delta
        newUses = invites
            .map { it.key to it.value - previous.getOrDefault(it.key, 0) }
            .filter { it.second > 0 }
            .toMap()
        previous = invites
    }

    /**
     * Locking!
     */
    private fun retrieveInvites(): Map<String, Int> {
        return guild.retrieveInvites().complete().map { it.code to it.uses }.toMap()
    }
}

