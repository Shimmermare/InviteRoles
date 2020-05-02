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
import javax.security.auth.login.LoginException
import kotlin.collections.HashSet
import kotlin.system.exitProcess

/**
 * Main bot class. Not valid until run() is called.
 */
class InviteRoles(private val token: String, dbPath: String) {
    val settingsRepository = SettingsRepository(dbPath)
    val invitesRepository = InviteRepository(dbPath)

    // Accessible after run
    lateinit var properties: Properties
        private set
    private lateinit var jda: JDA
    lateinit var commandDispatcher: CommandDispatcher<CommandSource>
        private set

    private val guilds = ConcurrentHashMap<Long, BotGuild>()

    /**
     * Run bot with this action sequence:
     * 1. Load app properties from classpath.
     * 2. Init repositories.
     * 3. Create JDA and setup Discord connection.
     * 4. Fetch settings and invites of joined guilds from repositories.
     * 5. Clear removed invites and roles.
     * 6. Init command dispatcher.
     * 7. Register event listener.
     * 8. Wait for console commands.
     *
     * This function will NEVER return. The only way to exit is to enter "stop" console command
     * or to call [stop].
     */
    fun run() {
        loadProperties()

        settingsRepository.initTable()
        invitesRepository.initTable()

        connectToDiscord()
        fetchJoinedGuilds()
        clearRemovedInvitesAndRoles()

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
            .forEach { guild ->
                if (!loadGuild(guild)) {
                    log.warn(
                        "Guild {} ({}) doesn't have settings in repository, possible DB corruption",
                        guild.name, guild.id
                    )
                }
            }
    }

    /**
     * Remove invites that have been removed on server or they don't have any existing role
     *
     * This is useful to catch stuff that was deleted when event listener wasn't active
     */
    private fun clearRemovedInvitesAndRoles() {
        getGuilds().forEach { guild ->
            val existingInvites = guild.guild.retrieveInvites().complete().map { it.code } // TODO coroutine candidate
            guild.getInvites().forEach { invite ->
                if (!existingInvites.contains(invite.code)) {
                    log.debug("Invite {} doesn't exist in guild {} and will be removed", invite.code, guild.id)
                    guild.removeInvite(invite)
                }
                if (guild.guild.getRoleById(invite.roleId) == null) {
                    guild.removeInvite(invite)
                    log.debug(
                        "Role {} of invite {} doesn't exist in guild {} and invite will be removed",
                        invite.roleId,
                        invite.code,
                        guild.id
                    )
                }
            }
        }
    }

    /**
     * Create command dispatcher and register commands
     */
    private fun initCommandDispatcher() {
        commandDispatcher = CommandDispatcher<CommandSource>()
        Commands.register(commandDispatcher)
    }

    /**
     * Returns true if bot was used in this guild already
     */
    @Synchronized
    fun loadGuild(guild: Guild): Boolean {
        val settingsFromRepo = settingsRepository.find(guild)
        val invites = invitesRepository.findAllOfGuild(guild)
        addGuild(guild, settingsFromRepo, invites) // Pass settings if null - they'll be defaulted and saved
        return settingsFromRepo != null
    }

    @Synchronized
    fun getGuild(guild: Guild): BotGuild? {
        return guilds[guild.idLong]
    }

    @Synchronized
    fun getGuild(id: Long): BotGuild? {
        return guilds[id]
    }

    @Synchronized
    @Throws(IllegalStateException::class)
    fun getGuildOrThrow(guild: Guild): BotGuild {
        return guilds[guild.idLong] ?: throw IllegalStateException("Guild ${guild.id} is not wrapped by the bot")
    }

    /**
     * Track new guild.
     *
     * @param settings if null, default will be created and saved to repository.
     */
    @Synchronized
    fun addGuild(
        guild: Guild,
        settings: BotGuildSettings?,
        invites: Collection<BotGuildInvite> = emptyList()
    ): BotGuild {
        val settingsToUse = if (settings == null) {
            val newSettings = BotGuildSettings()
            settingsRepository.set(guild, newSettings)
            newSettings
        } else {
            settings
        }

        val instance = BotGuild(this, guild, settingsToUse, invites)
        guilds[guild.idLong] = instance
        return instance
    }

    @Synchronized
    fun removeGuild(id: Long): BotGuild? {
        return guilds.remove(id)
    }

    @Synchronized
    fun getGuilds(): Set<BotGuild> {
        return HashSet(guilds.values)
    }

    @Synchronized
    fun stop() {
        log.info("Shutting down...")
        jda.shutdown()
        exitProcess(0)
    }

    fun createWarningMessage(warning: String): MessageEmbed {
        val footer = getMessageFooter()
        return EmbedBuilder()
            .setColor(Color.RED)
            .setTitle("Warning!")
            .setThumbnail(properties.getProperty("warning_icon_url"))
            .setDescription(warning)
            .setFooter(footer.first, footer.second)
            .build()
    }

    fun createInfoMessage(
        title: String,
        description: String? = null,
        fields: Collection<MessageEmbed.Field> = emptyList()
    ): MessageEmbed {
        val footer = getMessageFooter()
        val builder = EmbedBuilder()
            .setColor(Color.ORANGE)
            .setTitle(title)
            .setDescription(description)
            .setFooter(footer.first, footer.second)
        fields.forEach { builder.addField(it) }
        return builder.build()
    }

    private fun getMessageFooter(): Pair<String, String> {
        val text = "InviteRoles v${properties.getProperty("version")} by v${properties.getProperty("author.name")}"
        val icon = properties.getProperty("author.icon_url")
        return text to icon
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