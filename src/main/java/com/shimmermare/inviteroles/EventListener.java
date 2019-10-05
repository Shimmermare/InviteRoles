/*
 * MIT License
 *
 * Copyright (c) 2019 Shimmermare
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.shimmermare.inviteroles;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

import static com.shimmermare.inviteroles.Utils.censorInviteCode;

public class EventListener extends ListenerAdapter
{
    public static final Logger LOGGER = LoggerFactory.getLogger(EventListener.class);

    private final InviteRoles bot;

    public EventListener(InviteRoles bot)
    {
        this.bot = bot;
    }

    @Override
    public void onGuildMemberJoin(@Nonnull GuildMemberJoinEvent event)
    {
        Guild guild = event.getGuild();
        Member member = event.getMember();

        ServerInstance instance = bot.getServerInstance(guild.getIdLong());
        if (instance == null)
        {
            LOGGER.error("ServerInstance of server {} shouldn't but it is null.", guild.getIdLong());
            return;
        }
        ServerSettings settings = instance.getServerSettings();

        InviteTracker inviteTracker = instance.getInviteTracker();
        inviteTracker.update();
        Map<String, Integer> inviteUsesDelta = inviteTracker.getUsesDelta();
        if (inviteUsesDelta.isEmpty())
        {
            if (!member.getUser().isBot())
            {
                LOGGER.error("No invite delta found after new user {} joined server {}. This is shouldn't be possible.",
                        member.getIdLong(), guild.getIdLong());
            }
            else
            {
                LOGGER.debug("Another bot {} joined server {}, no invite delta as expected",
                        member.getIdLong(), guild.getIdLong());
            }
            return;
        }
        else if (inviteUsesDelta.size() > 1)
        {
            LOGGER.info("Two or more users joined server {} between invite tracker updates.", guild.getIdLong());
            instance.sendLogMessage("Two or more users joined server at the exact same time! " +
                    "Unfortunately Discord doesn't tell which invite was used by whom, " +
                    "so no invite roles will be granted and you should do this manually.");
            return;
        }

        String inviteCode = inviteUsesDelta.keySet().stream().findFirst().orElseThrow(RuntimeException::new);
        long roleId = settings.getInviteRole(inviteCode);
        Role role = guild.getRoleById(roleId);
        if (role == null)
        {
            settings.removeInviteRole(inviteCode);
            LOGGER.info("Invite role {} for invite {} to server {} doesn't exists.",
                    roleId, inviteCode, guild.getIdLong());
            instance.sendLogMessage("Role with id **" + roleId + "** that was set for " +
                    "invite code **" + censorInviteCode(inviteCode) + "** doesn't exists!");
            return;
        }

        guild.addRoleToMember(member, role).reason("Invite role from invite " + censorInviteCode(inviteCode)).queue(
                success -> LOGGER.info("Granted invite role {} to member {} that used invite {} on server {}.",
                        roleId, member.getIdLong(), inviteCode, guild.getIdLong()),
                exception ->
                {
                    String msg = "Unable to grant role **" + role.getName() + "** from invite **"
                            + censorInviteCode(inviteCode) + "** to **"
                            + member.getEffectiveName() + "**!";
                    if (exception instanceof InsufficientPermissionException || exception instanceof HierarchyException)
                    {
                        LOGGER.info("Failed to grant invite role {} to member {} that used invite {} on server {}.",
                                roleId, member.getIdLong(), inviteCode, guild.getIdLong(), exception);
                        msg += " Insufficient permissions.";
                    }
                    else
                    {
                        LOGGER.error("Failed to grant invite role {} to member {} that used invite {} on server {}.",
                                roleId, member.getIdLong(), inviteCode, guild.getIdLong(), exception);
                        msg += " Internal error.";
                    }
                    instance.sendLogMessage(msg);
                }
        );
    }

    @Override
    public void onGuildJoin(@Nonnull GuildJoinEvent event)
    {
        Guild guild = event.getGuild();
        bot.newServerInstance(guild);
        LOGGER.info("The bot has joined server {}. Hello!", guild.getIdLong());
    }

    @Override
    public void onGuildLeave(@Nonnull GuildLeaveEvent event)
    {
        Guild guild = event.getGuild();
        bot.removeServerInstance(guild.getIdLong());
        LOGGER.info("The bot has left server {}. Bye!", guild.getIdLong());
    }

    @Override
    public void onRoleDelete(@Nonnull RoleDeleteEvent event)
    {
        Guild guild = event.getGuild();
        ServerInstance instance = bot.getServerInstance(guild.getIdLong());
        ServerSettings settings = instance.getServerSettings();

        for (Map.Entry<String, Long> entry : new HashMap<>(settings.getInviteRoles()).entrySet())
        {
            if (event.getRole().getIdLong() == entry.getValue())
            {
                settings.removeInviteRole(entry.getKey());
                LOGGER.debug("Invite role {}/{} on server {} is removed because role itself was removed.",
                        entry.getKey(), entry.getValue(), guild.getIdLong());
                break;
            }
        }
    }

    @Override
    public void onPrivateMessageReceived(@Nonnull PrivateMessageReceivedEvent event)
    {
        /*
        TODO mark that user sent feedback, send thank you message (but not for every incoming message)
        introduce bot maintainer feature: all feedback messages get reposted to my DM
         */
    }
}
