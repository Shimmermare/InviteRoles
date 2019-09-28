package com.shimmermare.inviteroles;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * A class to watch server invites and count differences.
 */
public final class InviteTracker
{
    private static final Logger LOGGER = LoggerFactory.getLogger(InviteTracker.class);

    private final Guild guild;
    private final Map<String, Integer> inviteUses = new HashMap<>();

    private Map<String, Integer> deltaUses = Collections.emptyMap();

    /**
     * InviteTracker constructor.
     *
     * @param guild tracked server.
     */
    public InviteTracker(Guild guild)
    {
        this.guild = guild;
    }

    /**
     * Update tracker: pull invites from server and find per invite uses delta from previous update.
     */
    public void update()
    {
        List<Invite> invites = guild.retrieveInvites().complete();
        findUsesDelta(invites);
        updateMap(invites);
        LOGGER.debug("Tracker of server {} updated, delta: {}", guild.getIdLong(), deltaUses);
    }

    /**
     * Async version of {@link #update()}.
     */
    public void updateAsync()
    {
        guild.retrieveInvites().queue(invites ->
        {
            findUsesDelta(invites);
            updateMap(invites);
            LOGGER.debug("Tracker of server {} updated asynchronously, delta: {}", guild.getIdLong(), deltaUses);
        });
    }

    private void findUsesDelta(Collection<Invite> invites)
    {
        Map<String, Integer> deltaUsesMap = new HashMap<>();
        for (Invite invite : invites)
        {
            int uses = inviteUses.getOrDefault(invite.getCode(), 0);
            int deltaUses = invite.getUses() - uses;
            if (deltaUses < 0)
            {
                LOGGER.error("Server {} invite {} uses delta is negative ({}). This is shouldn't be possible.", guild.getIdLong(), invite.getCode(), deltaUses);
                continue;
            }
            deltaUsesMap.put(invite.getCode(), deltaUses);
        }
        deltaUses = Collections.unmodifiableMap(deltaUsesMap);
    }

    private void updateMap(Collection<Invite> invites)
    {
        inviteUses.clear();
        for (Invite invite : invites)
        {
            inviteUses.put(invite.getCode(), invite.getUses());
        }
    }

    /**
     * Get difference in invite use numbers between last two updates of the tracker.
     * Note: method will return incorrect numbers if {@link #update()} wasn't
     * called at least twice after object initialization.
     *
     * @return difference in invite use numbers between last two updates of the tracker.
     */
    public Map<String, Integer> getUsesDelta()
    {
        return deltaUses;
    }
}
