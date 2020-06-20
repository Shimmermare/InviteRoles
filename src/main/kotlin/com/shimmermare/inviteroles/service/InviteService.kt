package com.shimmermare.inviteroles.service

import com.shimmermare.inviteroles.entity.TrackedInvite
import com.shimmermare.inviteroles.repository.TrackedInviteRepository
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InviteService(
    private val repository: TrackedInviteRepository
) {
    @Transactional(readOnly = true)
    fun getInvite(inviteCode: String): TrackedInvite? {
        return repository.get(inviteCode)
    }

    @Transactional
    fun getOrCreateInvite(inviteCode: String, guildId: Long): TrackedInvite {
        var invite = getInvite(inviteCode)
        if (invite == null) {
            invite = TrackedInvite(inviteCode, guildId)
            saveInvite(invite)
        }
        return invite
    }

    @Transactional
    fun getInvitesOfGuild(guildId: Long) = repository.getAllOfGuild(guildId)

    @Transactional
    fun getInvitesOfGuild(guild: Guild) = getInvitesOfGuild(guild.idLong)

    @Transactional
    fun saveInvite(invite: TrackedInvite) = repository.set(invite)

    @Transactional
    fun deleteInvite(invite: TrackedInvite) = deleteInvite(invite.inviteCode)

    @Transactional
    fun deleteInvite(inviteCode: String) = repository.delete(inviteCode)

    @Transactional(readOnly = true)
    fun getInvitesWithRole(guild: Guild, role: Role): List<TrackedInvite> {
        return repository.getAllOfGuild(guild.idLong).asSequence()
            .filter { it.roles.contains(role.idLong) }.toList()
    }

    @Transactional
    fun modifyInvite(code: String, block: (TrackedInvite?) -> TrackedInvite?): TrackedInvite? {
        val invite = getInvite(code)
        val newInvite = block.invoke(invite)
        if (invite == null) {
            if (newInvite == null) {
                // Do nothing
            } else {
                saveInvite(newInvite)
            }
        } else {
            if (newInvite == null) {
                deleteInvite(code)
            } else {
                assertChangeLegal(invite, newInvite)
                saveInvite(newInvite)
            }
        }
        return newInvite
    }

    private fun assertChangeLegal(before: TrackedInvite, after: TrackedInvite) {
        if (before.inviteCode != after.inviteCode) {
            throw IllegalStateException("Invite code change is not allowed when modifying invite")
        } else if (before.guildId != after.guildId) {
            throw IllegalStateException("Invite guild id change is not allowed when modifying invite")
        }
    }
}