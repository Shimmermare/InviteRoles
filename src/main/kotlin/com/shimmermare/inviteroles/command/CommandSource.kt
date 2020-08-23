package com.shimmermare.inviteroles.command

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.TextChannel

class CommandSource(val guild: Guild, val channel: TextChannel, val member: Member)