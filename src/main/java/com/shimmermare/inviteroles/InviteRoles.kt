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
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.security.auth.login.LoginException
import kotlin.collections.HashMap
import kotlin.system.exitProcess

/**
 * Main bot class. Not valid until run() is called.
 */
class InviteRoles(private val token: String, dbPath: String) {
    private val settingsRepository = SettingsRepository(dbPath)
    private val invitesRepository = InviteRepository(dbPath)
    private val repositoryUpdater = Executors.newSingleThreadScheduledExecutor()

    // Accessible after run
    lateinit var properties: Properties
    private lateinit var jda: JDA
    lateinit var commandDispatcher: CommandDispatcher<CommandSource>

    private val guilds = ConcurrentHashMap<Long, BotGuild>()

    /**
     * Run bot with this action sequence:
     * 1. Load app properties from classpath.
     * 2. Init repositories.
     * 3. Create JDA and setup Discord connection.
     * 4. Fetch settings and invites of joined guilds from repositories.
     * 5. Start repository updater task.
     * 6. Init command dispatcher.
     * 7. Register event listener.
     * 8. Wait for console commands.
     *
     * This function will NEVER return. The only way to exit is to enter "stop" console command
     * or to call [stop].
     */
    fun run() {
        loadProperties()

        settingsRepository.initTables()
        invitesRepository.initTable()

        connectToDiscord()
        fetchJoinedGuilds()

        // Update repositories with new changes once in 10 seconds
        repositoryUpdater.scheduleAtFixedRate({
            updateRepositories()
        }, 10L, 10L, TimeUnit.SECONDS)

        initCommandDispatcher()
        jda.addEventListener(EventListener(this))

        log.info("Starting InviteRoles Discord Bot v${properties.getProperty("version")}.")

        // Wait for console commands in main thread; bot itself runs in separate threads
        // Call bot.stop() to exit program
        while (true) {
            when (val line: String? = readLine()) {
                "stop" -> {
                    log.info("Received stop console command")
                    stop()
                }
                else -> {
                    println("Unknown console command. Enter 'stop' to stop the bot.")
                    log.info("Received unknown console command: $line")
                }
            }
        }
    }

    /**
     * Load properties from .properties on classpath
     */
    @Throws(IOException::class)
    private fun loadProperties() {
        properties = Properties()

        try {
            val input = InviteRoles::class.java.getResourceAsStream("/.properties")
            input.use(properties::load)
            log.debug("Properties loaded from file")
        } catch (e: IOException) {
            log.error("Failed to load application properties", e)
            throw e
        }
    }

    /**
     * Build JDA and setup discord connection
     */
    @Throws(LoginException::class)
    private fun connectToDiscord() {
        try {
            jda = JDABuilder
                .create(
                    token,
                    GatewayIntent.GUILD_MEMBERS, // FYI: Discord will limit bot to only 100 guilds because of this line
                    GatewayIntent.GUILD_INVITES,
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.DIRECT_MESSAGES
                )
                .disableCache(CacheFlag.ACTIVITY)
                .disableCache(CacheFlag.CLIENT_STATUS)
                .disableCache(CacheFlag.VOICE_STATE)
                .disableCache(CacheFlag.EMOTE)
                .build()
            log.info("Logging in Discord...")
        } catch (e: LoginException) {
            log.error("Failed to login to Discord!", e)
            throw e
        }

        jda.awaitReady()
        val selfUser: User = jda.selfUser
        log.info("Logged in as ${selfUser.name} (${selfUser.id}).")
    }

    /**
     * Find settings and invites of the joined guilds in the repository
     */
    private fun fetchJoinedGuilds() {
        jda.guilds
            .also { guilds -> log.info("Joined ${guilds.size} guilds.") }
            .asSequence()
            // Get settings from repository
            .map { g -> g to settingsRepository.find(g.idLong) }
            // Check for null settings: it shouldn't be possible in normal conditions
            .map { gs ->
                if (gs.second == null) {
                    log.warn(
                        "Guild {} ({}) doesn't have settings in repository, possible DB corruption",
                        gs.first.name, gs.first.id
                    )
                    gs.first to BotGuildSettings(gs.first)
                } else {
                    gs
                }
            }
            // Get invites from repository
            .map { gs ->
                val invites = invitesRepository.findAllOfGuild(gs.first.idLong)
                log.info("Guild {} ({}) has {} active invites", gs.first.name, gs.first.id, invites.size)
                Triple(gs.first, gs.second, invites)
            }
            // Add all guilds to tracker
            .forEach { gsi -> addGuild(gsi.first, gsi.second as BotGuildSettings, gsi.third) }
    }

    /**
     * Create command dispatcher and register commands
     */
    private fun initCommandDispatcher() {
        commandDispatcher = CommandDispatcher<CommandSource>()
        Commands.register(commandDispatcher)
    }

    private fun updateRepositories() {
        guilds.forEach { updateRepositoriesForGuild(it.value) }
    }

    /**
     *  Update guild settings and invites in repositories if they were modified
     */
    private fun updateRepositoriesForGuild(guild: BotGuild) {
        val settings = guild.settings
        if (settings.updated) {
            log.debug("Updating settings of guild {} in repository", guild.id)
            settingsRepository.set(settings)
            settings.updated = false
        }

        val invites = guild.invites
        if (invites.updated) {
            val delta = invites.findDeltaSinceLastUpdate()
            log.debug("Updating invites of guild {} in repository, delta: {}", guild.id, delta)
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
        log.info("Shutting down...")

        repositoryUpdater.shutdown()
        updateRepositories()
        jda.shutdown()
        exitProcess(0)
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(InviteRoles::class.java)
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

    val bot = InviteRoles(token, dbPath)
    bot.run()
}

/**
 * Replace last n characters with given character
 */
fun String.censorLast(count: Int = 3, char: Char = '•'): String {
    val chars = CharArray(length)
    for (i in 0 until length) {
        chars[i] = if (length - i <= count) char else this[i]
    }
    return String(chars)
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