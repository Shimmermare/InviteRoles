package com.shimmermare.inviteroles.repository

import com.shimmermare.inviteroles.entity.TrackedInvite
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface TrackedInviteRepository {
    @Transactional(readOnly = true)
    fun get(inviteCode: String): TrackedInvite?

    @Transactional(readOnly = true)
    fun getAllOfGuild(guildId: Long): List<TrackedInvite>

    /**
     * Doesn't actually save it if invite has no roles.
     */
    @Transactional
    fun set(invite: TrackedInvite)

    @Transactional
    fun delete(inviteCode: String)

    @Transactional
    fun deleteAllOfGuild(guildId: Long)
}