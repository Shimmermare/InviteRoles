package com.shimmermare.inviteroles.invite

import javax.persistence.*

@Entity
@Table(name = "invites")
data class TrackedInvite(
        @Id
        @Column(name = "invite_code")
        val inviteCode: String,
        @Column(name = "guild_id")
        val guildId: Long,
        @ElementCollection
        @CollectionTable(
                name = "invites_roles",
                joinColumns = [JoinColumn(name = "invite_code", referencedColumnName = "invite_code")]
        )
        @Column(name = "roles", nullable = false)
        val roles: List<Long> = emptyList()
)