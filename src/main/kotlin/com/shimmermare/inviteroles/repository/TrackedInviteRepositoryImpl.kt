package com.shimmermare.inviteroles.repository

import com.shimmermare.inviteroles.entity.TrackedInvite
import javax.persistence.EntityManager

class TrackedInviteRepositoryImpl(
    private val em: EntityManager
) : TrackedInviteRepository {
    override fun get(inviteCode: String): TrackedInvite? {
        TODO("Not yet implemented")
    }

    override fun getAllOfGuild(guildId: Long): List<TrackedInvite> {
        TODO("Not yet implemented")
    }

    override fun set(invite: TrackedInvite) {
        TODO("Not yet implemented")
    }

    override fun delete(inviteCode: String) {
        TODO("Not yet implemented")
    }

    override fun deleteAllOfGuild(guildId: Long) {
        TODO("Not yet implemented")
    }
}