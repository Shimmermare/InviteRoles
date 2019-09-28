package com.shimmermare.inviteroles;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A class to watch server invites and count differences.
 */
public final class InviteTracker
{
    private static final Logger LOGGER = LoggerFactory.getLogger(InviteTracker.class);

    //to lock tracker during update
    private final Object lock = new Object();

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
        guild.retrieveInvites().queue(invites ->
        {
            synchronized (lock)
            {
                Map<String, Integer> deltaUsesMap = new HashMap<>();
                for (Invite invite : invites)
                {
                    int uses = inviteUses.getOrDefault(invite.getCode(), 0);
                    int deltaUses = invite.getUses() - uses;
                    if (deltaUses < 0)
                    {
                        LOGGER.error("Invite uses delta is negative ({}). This is shouldn't be possible.", deltaUses);
                        continue;
                    }
                    deltaUsesMap.put(invite.getCode(), deltaUses);
                }
                deltaUses = Collections.unmodifiableMap(deltaUsesMap);

                //Repopulating map is safer and faster than comparing.
                inviteUses.clear();
                for (Invite invite : invites)
                {
                    inviteUses.put(invite.getCode(), invite.getUses());
                }

                LOGGER.debug("InviteTracker of server {} updated, delta: {}", guild.getIdLong(), deltaUses);
            }
        });
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
        synchronized (lock)
        {
            return deltaUses;
        }
    }
}
