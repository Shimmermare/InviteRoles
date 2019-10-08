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

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ServerInstance
{
    public static final Logger LOGGER = LoggerFactory.getLogger(ServerInstance.class);

    private final InviteRoles bot;
    private final Guild server;
    private final ServerSettings serverSettings;
    private final InviteTracker inviteTracker;

    private volatile boolean updatedFlag = true;

    public ServerInstance(InviteRoles bot, Guild server, ServerSettings serverSettings)
    {
        this.bot = bot;
        this.server = server;
        this.inviteTracker = new InviteTracker(server);
        this.serverSettings = serverSettings;
    }

    public void checkInviteRoles()
    {
        Set<String> inviteCodes = server.retrieveInvites().complete().stream()
                .map(Invite::getCode).collect(Collectors.toSet());
        Map<String, Long> inviteRoles = serverSettings.getInviteRoles();
        for (Map.Entry<String, Long> inviteRole : new HashMap<>(inviteRoles).entrySet())
        {
            String code = inviteRole.getKey();
            long role = inviteRole.getValue();
            if (!inviteCodes.contains(code))
            {
                serverSettings.removeInviteRole(code);
                LOGGER.debug("Invite role {}/{} removed because invite no longer exists.", code, role);
                continue;
            }
            if (server.getRoleById(role) == null)
            {
                serverSettings.removeInviteRole(code);
                LOGGER.debug("Invite role {}/{} removed because role no longer exists.", code, role);
            }
        }
    }

    public void sendWarning(String text)
    {
        if (!serverSettings.isWarningsEnabled())
        {
            LOGGER.debug("Warnings are disabled on server {}, the warning is ignored.", server.getIdLong());
            return;
        }

        TextChannel channel = server.getSystemChannel();
        if (channel == null)
        {
            LOGGER.debug("Warnings are enabled on server {} " +
                    "but system message channel is disabled, the warning is ignored.", server.getIdLong());
            return;
        }

        channel.sendMessage(Utils.createWarningEmbed(text)).queue(
                success -> LOGGER.debug("Warning was sent to server {}.", server.getIdLong()),
                exception -> LOGGER.info("Failed to send warning to server {}.", server.getIdLong(), exception)
        );
    }

    public void flagUpdated()
    {
        this.updatedFlag = true;
    }

    public void setUpdatedFlag(boolean updatedFlag)
    {
        this.updatedFlag = updatedFlag;
    }

    public boolean isUpdated()
    {
        return updatedFlag;
    }

    public InviteRoles getBot()
    {
        return bot;
    }

    public Guild getServer()
    {
        return server;
    }

    public ServerSettings getServerSettings()
    {
        return serverSettings;
    }

    public InviteTracker getInviteTracker()
    {
        return inviteTracker;
    }
}
