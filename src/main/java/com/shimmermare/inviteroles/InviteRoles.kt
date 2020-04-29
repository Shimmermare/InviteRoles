package com.shimmermare.inviteroles

import com.mojang.brigadier.CommandDispatcher
import joptsimple.OptionParser
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Color
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.HashMap
import kotlin.system.exitProcess

private val LOGGER: Logger = LoggerFactory.getLogger(InviteRoles::class.java)

class InviteRoles(val properties: Properties, private val jda: JDA, dbPath: String) {
    private val settingsRepository = SettingsRepository(dbPath)
    private val invitesRepository = InviteRepository(dbPath)

    private val repositoryUpdater = Executors.newSingleThreadScheduledExecutor()
    private val guilds = ConcurrentHashMap<Long, BotGuild>()

    val commandDispatcher = createCommandDispatcher()

    fun run() {
        settingsRepository.initTables()
        invitesRepository.initTable()

        initGuilds()

        jda.addEventListener(EventListener(this))

        repositoryUpdater.scheduleAtFixedRate({
            updateRepositories()
        }, 10L, 10L, TimeUnit.SECONDS)
    }

    private fun initGuilds() {
        jda.guilds
            .filterNotNull()
            // Get settings from repository
            .map { g -> g to settingsRepository.find(g.idLong) }
            // Check for null settings: it shouldn't be possible in normal conditions
            .map { gs ->
                if (gs.second == null) {
                    LOGGER.warn(
                        "Guild {} ({}) doesn't have settings in repository, possible DB corruption",
                        gs.first.name, gs.first.idLong
                    )
                    gs.first to BotGuildSettings(gs.first)
                } else {
                    gs
                }
            }
            // Get invites from repository
            .map { gs -> Triple(gs.first, gs.second, invitesRepository.findAllOfGuild(gs.first.idLong)) }
            // Add all guilds to tracker
            .forEach { gsi -> addGuild(gsi.first, gsi.second as BotGuildSettings, gsi.third) }
    }

    private fun createCommandDispatcher(): CommandDispatcher<CommandSource> {
        val dispatcher = CommandDispatcher<CommandSource>()
        Commands.register(dispatcher)
        return dispatcher
    }

    private fun updateRepositories() {
        guilds.forEach { updateRepositoriesForGuild(it.value) }
    }

    private fun updateRepositoriesForGuild(guild: BotGuild) {
        val settings = guild.settings
        if (settings.updated) {
            LOGGER.debug("Updating settings of guild {} in repository", guild.id)
            settingsRepository.set(settings)
            settings.updated = false
        }

        val invites = guild.invites
        if (invites.updated) {
            val delta = invites.findDeltaSinceLastUpdate()
            LOGGER.debug("Updating invites of guild {} in repository, delta: {}", guild.id, delta)
            delta.added.forEach(invitesRepository::set)
            delta.updated.forEach(invitesRepository::set)
            delta.removed.forEach(invitesRepository::delete)
            invites.updated = false
        }
    }

    /**
     * Returns true if bot was used in this guild already
     */
    fun onGuildJoin(guild: Guild): Boolean {
        val settingsFromRepo = settingsRepository.find(guild)
        return if (settingsFromRepo != null) {
            val invites = invitesRepository.findAllOfGuild(guild)
            addGuild(guild, settingsFromRepo, invites)
            true
        } else {
            val settings = BotGuildSettings(guild)
            settingsRepository.set(settings)
            val invites = BotGuildInvites(guild)
            addGuild(guild, settings, invites)
            false
        }
    }

    fun getGuild(id: Long): BotGuild? {
        return guilds[id]
    }

    fun getGuild(guild: Guild): BotGuild? {
        return guilds[guild.idLong]
    }

    @Throws(IllegalStateException::class)
    fun getGuildOrThrow(guild: Guild): BotGuild {
        return guilds[guild.idLong] ?: throw IllegalStateException("Guild ${guild.id} is not wrapped by the bot")
    }

    fun addGuild(guild: Guild, settings: BotGuildSettings, invites: BotGuildInvites): BotGuild {
        val instance = BotGuild(this, guild, settings, invites)
        guilds[guild.idLong] = instance
        return instance
    }

    fun removeGuild(id: Long): BotGuild? {
        val guild = guilds.remove(id)
        if (guild != null) {
            updateRepositoriesForGuild(guild)
        }
        return guild
    }

    fun getGuilds(): Map<Long, BotGuild> {
        return HashMap(guilds)
    }

    @Synchronized
    fun stop() {
        LOGGER.info("Shutting down...")

        repositoryUpdater.shutdown()
        updateRepositories()
        jda.shutdown()
        exitProcess(0)
    }
}

fun main(args: Array<String>) {
    // Parse args using Jopt-Simple - check it out, it's a cool little lib!
    val optionParser = OptionParser()
    val tokenSpec = optionParser.accepts("token").withRequiredArg().ofType(String::class.java).required()
    val dbPathSpec = optionParser.accepts("db").withRequiredArg().ofType(String::class.java).required()

    val optionSet = optionParser.parse(*args)
    val token = tokenSpec.value(optionSet)
    val dbPath = dbPathSpec.value(optionSet)

    val properties = loadProperties()
    val jda = initJDA(token)
    val bot = InviteRoles(properties, jda, dbPath)
    LOGGER.info("Starting InviteRoles Discord Bot v${properties.getProperty("version")}.")
    bot.run()

    // Wait for console commands in main thread; bot itself runs in separate threads
    // Call bot.stop() to exit program
    while (true) {
        when (val line: String? = readLine()) {
            "stop" -> {
                LOGGER.info("Received stop console command")
                bot.stop()
            }
            else -> {
                println("Unknown console command. Enter 'stop' to stop the bot.")
                LOGGER.info("Received unknown console command: $line")
            }
        }
    }
}

private fun loadProperties(): Properties {
    val properties = Properties()

    try {
        val input = InviteRoles::class.java.getResourceAsStream("/.properties")
        input.use(properties::load)
        LOGGER.debug("Properties loaded from file")
    } catch (e: Exception) {
        LOGGER.error("Unable to load application properties", e)
    }

    return properties
}

private fun initJDA(token: String): JDA {
    val jda = JDABuilder
        .create(
            token,
            GatewayIntent.GUILD_MEMBERS,
            GatewayIntent.GUILD_INVITES,
            GatewayIntent.GUILD_MESSAGES,
            GatewayIntent.DIRECT_MESSAGES
        )
        .disableCache(CacheFlag.ACTIVITY)
        .disableCache(CacheFlag.CLIENT_STATUS)
        .disableCache(CacheFlag.VOICE_STATE)
        .disableCache(CacheFlag.EMOTE)
        .build()
    jda.awaitReady()

    val selfUser: User = jda.selfUser
    LOGGER.info("Logged in as ${selfUser.name} (${selfUser.id}).")

    return jda
}

/**
 * Replace last 3 characters with `•`.
 */
fun hideInvite(code: String): String {
    return code.substring(0, code.length - 3) + "•••"
}

fun EmbedBuilder.buildWarning(warning: String?): MessageEmbed {
    return EmbedBuilder()
        .setColor(Color.RED)
        .setAuthor("Warning!")
        // Ugh, hardcoded url
        .setThumbnail("https://cdn.discordapp.com/attachments/630175733708357642/630175767426367508/Exclamation_yellow_128.png")
        .setDescription(warning)
        .setFooter("InviteRole by Shimmermare")
        .build()
}