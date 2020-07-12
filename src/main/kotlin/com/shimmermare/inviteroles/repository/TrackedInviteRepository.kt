package com.shimmermare.inviteroles.repository

import com.shimmermare.inviteroles.entity.TrackedInvite
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface TrackedInviteRepository : CrudRepository<TrackedInvite, String> {
    @Transactional(readOnly = true)
    @Query("select invite from TrackedInvite as invite where invite.guildId = :guildId")
    fun getAllOfGuild(@Param("guildId") guildId: Long): List<TrackedInvite>
}