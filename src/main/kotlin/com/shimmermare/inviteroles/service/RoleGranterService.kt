package com.shimmermare.inviteroles.service

import com.shimmermare.inviteroles.logger
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import org.springframework.stereotype.Service

@Service
class RoleGranterService(
    val jda: JDA
) {
    private val log = logger<RoleGranterService>()

    fun grantRoles(member: Member, inviteCode: String) {

    }
}