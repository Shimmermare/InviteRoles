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

import com.mojang.brigadier.CommandDispatcher;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class InviteRoles
{
    private static final Logger LOGGER = LoggerFactory.getLogger(InviteRoles.class);

    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    static
    {
        executorService.execute(() -> Thread.currentThread().setName("DBUpdater"));
    }

    private final String token;
    private final String dbPath;

    private String version;

    private JDA jda;
    private DatabaseService databaseService;
    private CommandDispatcher<CommandSource> commandDispatcher;
    private Map<Long, ServerInstance> joinedServers = new ConcurrentHashMap<>();

    public InviteRoles(String token, String dbPath)
    {
        this.token = token;
        this.dbPath = dbPath;
    }

    private void readProperties()
    {
        LOGGER.debug("Reading application properties from resource");
        Properties properties = new Properties();
        try (InputStream is = InviteRoles.class.getResourceAsStream("/.properties"))
        {
            properties.load(is);
        }
        catch (Exception e)
        {
            LOGGER.error("Unable to load application properties", e);
        }

        this.version = properties.getProperty("version", "unknown");
    }

    private void run()
    {
        readProperties();

        LOGGER.info("Starting InviteRoles Discord Bot version {}. I'm alive!", this.version);

        commandDispatcher = new CommandDispatcher<>();
        Command.register(commandDispatcher);

        JDABuilder jdaBuilder = new JDABuilder(token);

        try
        {
            jda = jdaBuilder
                    .addEventListeners(new EventListener(this))
                    .build();
        }
        catch (LoginException e)
        {
            LOGGER.error("Unable to login to Discord", e);
            System.exit(-1);
        }

        try
        {
            jda.awaitReady();
        }
        catch (InterruptedException e)
        {
            LOGGER.error("Failed to await JDA ready", e);
            System.exit(-1);
        }

        User selfUser = jda.getSelfUser();
        LOGGER.info("Successfully logged in as {} ({}).", selfUser.getName(), selfUser.getIdLong());

        databaseService = DatabaseService.connect(dbPath);
        if (databaseService == null)
        {
            System.exit(-1);
        }
        databaseService.checkTables();

        checkGuilds();

        executorService.scheduleAtFixedRate(() ->
        {
            for (ServerInstance instance : joinedServers.values())
            {
                if (instance.isUpdated())
                {
                    long server = instance.getServer().getIdLong();
                    databaseService.setServerSettings(server, instance.getServerSettings());
                    instance.setUpdatedFlag(false);
                    LOGGER.debug("Database info of server {} updated by scheduler.", server);
                }
            }
        }, 5L, 5L, TimeUnit.MINUTES);
    }

    private void checkGuilds()
    {
        List<Guild> servers = jda.getGuilds();
        int totalMembers = 0;
        for (Guild server : servers)
        {
            totalMembers += server.getMembers().size();

            ServerSettings settings = databaseService.getServerSettings(server.getIdLong());
            if (settings == null)
            {
                newServerInstance(server);
            }
            else
            {
                ServerInstance instance = newServerInstance(server, settings);
                instance.checkInviteRoles();
            }
        }
        LOGGER.info("Currently joined {} servers with a total of {} members.", servers.size(), totalMembers);
    }

    public void forceUpdateDatabase()
    {
        for (ServerInstance instance : joinedServers.values())
        {
            long server = instance.getServer().getIdLong();
            databaseService.setServerSettings(server, instance.getServerSettings());
            instance.setUpdatedFlag(false);
        }
    }

    public void shutdown()
    {
        jda.shutdown();

        forceUpdateDatabase();
        databaseService.close();

        System.exit(0);
    }

    public Map<Long, ServerInstance> getJoinedServers()
    {
        return Collections.unmodifiableMap(joinedServers);
    }

    public ServerInstance getServerInstance(long server)
    {
        return joinedServers.get(server);
    }

    public ServerInstance newServerInstance(Guild server)
    {
        return newServerInstance(server, new ServerSettings());
    }

    public ServerInstance newServerInstance(Guild server, ServerSettings settings)
    {
        ServerInstance instance = new ServerInstance(this, server, settings);
        joinedServers.put(server.getIdLong(), instance);
        return instance;
    }

    public ServerInstance removeServerInstance(long server)
    {
        ServerInstance instance = joinedServers.remove(server);
        if (instance != null)
        {
            databaseService.deleteServerSettings(server);
        }
        return instance;
    }

    public CommandDispatcher<CommandSource> getCommandDispatcher()
    {
        return commandDispatcher;
    }

    public String getDbPath()
    {
        return dbPath;
    }

    public String getVersion()
    {
        return version;
    }

    public JDA getJda()
    {
        return jda;
    }

    public static void main(String[] args)
    {
        OptionParser optionParser = new OptionParser();
        OptionSpec<String> tokenSpec = optionParser.accepts("token").withRequiredArg().ofType(String.class).required();
        OptionSpec<String> dbPathSpec = optionParser.accepts("db").withRequiredArg().ofType(String.class).required();

        OptionSet optionSet = optionParser.parse(args);
        String token = tokenSpec.value(optionSet);
        String dbPath = dbPathSpec.value(optionSet);

        InviteRoles inviteRoles = new InviteRoles(token, dbPath);
        inviteRoles.run();

        try
        {
            System.in.read();
            LOGGER.info("Application terminated from console. Goodbye!");
            inviteRoles.shutdown();
            System.exit(0);
        }
        catch (IOException e)
        {
            LOGGER.error("Unable to read from console", e);
        }
    }
}
