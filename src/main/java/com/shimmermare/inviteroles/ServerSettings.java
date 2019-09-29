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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The class representing settings of a server.
 */
public final class ServerSettings
{
    private Map<String, Long> inviteRoles;
    //-1 - disabled, 0 - system message channel
    private long logChannel;

    public ServerSettings()
    {
        this.inviteRoles = new HashMap<>();
    }

    /**
     * Get unmodifiable map of all server's invite roles. Role represented by snowflake id.
     *
     * @return unmodifiable map of server's invite roles.
     */
    public Map<String, Long> getInviteRoles()
    {
        return Collections.unmodifiableMap(inviteRoles);
    }

    /**
     * Get invite role snowflake id by invite code.
     *
     * @param inviteCode invite code.
     * @return invite role snowflake id or {@code 0} if none.
     */
    public long getInviteRole(String inviteCode)
    {
        return inviteRoles.getOrDefault(inviteCode, 0L);
    }

    /**
     * Set invite role.
     *
     * @param inviteCode invite code.
     * @param role       role snowflake id.
     * @return previous role id associated with given invite code.
     * @throws IllegalArgumentException if {@code inviteCode} is null or empty
     * @throws IllegalArgumentException if {@code role} snowflake id format is invalid
     */
    public Long setInviteRole(String inviteCode, long role)
    {
        if (inviteCode == null || inviteCode.isEmpty())
        {
            throw new IllegalArgumentException("Invite code can't be null or empty");
        }
        if (role <= 0)
        {
            throw new IllegalArgumentException("Role snowflake id is invalid (" + role + ")");
        }
        return inviteRoles.put(inviteCode, role);
    }

    /**
     * Remove invite role by invite code.
     *
     * @param inviteCode invite code.
     * @return previous invite role snowflake id or {@code 0} if none.
     */
    public long removeInviteRole(String inviteCode)
    {
        return Optional.ofNullable(inviteRoles.remove(inviteCode)).orElse(0L);
    }

    /**
     * Get current log channel snowflake id.
     *
     * @return current log channel snowflake id, or {@code -1} if log channel disabled,
     * or {@code 0} if log channel is system message channel (default value).
     */
    public long getLogChannel()
    {
        return logChannel;
    }

    /**
     * Set current log channel snowflake id.
     *
     * @param logChannel log channel snowflake id,
     *                   or {@code -1} if log channel disabled,
     *                   or {@code 0} if log channel is system message channel (default value).
     */
    public void setLogChannel(long logChannel)
    {
        this.logChannel = logChannel;
    }
}
