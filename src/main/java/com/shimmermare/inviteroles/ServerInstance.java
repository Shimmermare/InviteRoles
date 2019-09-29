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
import net.dv8tion.jda.api.entities.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerInstance
{
    public static final Logger LOGGER = LoggerFactory.getLogger(ServerInstance.class);

    private final InviteRoles bot;
    private final Guild server;
    private final ServerSettings serverSettings;
    private final InviteTracker inviteTracker;

    public ServerInstance(InviteRoles bot, Guild server, ServerSettings serverSettings)
    {
        this.bot = bot;
        this.server = server;
        this.inviteTracker = new InviteTracker(server);
        this.serverSettings = serverSettings;
    }

    public void sendLogMessage(String text)
    {
        long logChannel = serverSettings.getLogChannel();
        if (logChannel == -1)
        {
            LOGGER.debug("Log channel is disabled on server {}, log message is ignored.", server.getIdLong());
            return;
        }

        TextChannel channel;
        if (logChannel > 0)
        {

        }
        if (logChannel == 0)
        {
            channel = server.getSystemChannel();
            if (channel == null)
            {
                LOGGER.debug("Log channel is default on server {}, but no system channel is set. Log message is ignored.",
                        server.getIdLong());
                return;
            }
        }
        else
        {
            channel = server.getTextChannelById(logChannel);
            if (channel == null)
            {
                LOGGER.info("Log channel is {} on server {}, but such channel doesn't exists. " +
                                "Trying to send log message into system channel.",
                        logChannel, server.getIdLong());
                serverSettings.setLogChannel(0);
                sendLogMessage(text);
                return;
            }
        }

        channel.sendMessage(text).queue(
                success -> LOGGER.debug("Log message was sent into channel {} on server {}.",
                        channel.getIdLong(), server.getIdLong()),
                exception ->
                {
                    LOGGER.info("Failed to send log message into channel {} on server {}.",
                            channel.getIdLong(), server.getIdLong(), exception);
                    if (logChannel == 0)
                    {
                        //lol gg
                        return;
                    }

                    TextChannel systemChannel = server.getSystemChannel();
                    if (systemChannel != null)
                    {
                        systemChannel.sendMessage(Utils.createLogEmbed("Can't send message into log channel. Check bot's permissions.")).queue();
                    }
                }
        );
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
