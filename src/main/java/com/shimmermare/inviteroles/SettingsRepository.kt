package com.shimmermare.inviteroles

import net.dv8tion.jda.api.entities.Guild
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.DriverManager
import java.sql.SQLException

/**
 * Create a new connection each time to avoid shenanigans. This is SQLite so it's dirt cheap.
 */
class SettingsRepository(dbPath: String) {
    private val log: Logger = LoggerFactory.getLogger(SettingsRepository::class.java)
    private val url = "jdbc:sqlite:$dbPath"

    fun initTables() {
        val sql = ("CREATE TABLE IF NOT EXISTS settings (\n"
                + "guild bigint NOT NULL PRIMARY KEY, \n"
                + "warnings bit NOT NULL\n"
                + ");")

        try {
            DriverManager.getConnection(url).use { connection ->
                connection.createStatement().use { statement ->
                    if (statement.execute(sql)) {
                        log.debug("Settings table didn't exist and was created")
                    } else {
                        log.debug("Settings table already exists")
                    }
                }
            }
        } catch (e: SQLException) {
            log.error("Failed to initialize table", e)
            throw IllegalStateException("Failed to initialize table", e)
        }
    }

    fun find(guild: BotGuild): BotGuildSettings? = find(guild.id)

    fun find(guild: Guild): BotGuildSettings? = find(guild.idLong)

    fun find(guildId: Long): BotGuildSettings? {
        val sql = "SELECT * FROM settings WHERE guild = ?;"

        try {
            DriverManager.getConnection(url).use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setLong(1, guildId)

                    val result = statement.executeQuery()
                    return if (result.next()) {
                        val settings = BotGuildSettings(
                            warnings = result.getBoolean(2)
                        )
                        log.debug("Found settings for guild {}", guildId)
                        settings
                    } else {
                        log.debug("Settings for guild {} doesn't exist", guildId)
                        null
                    }
                }
            }
        } catch (e: SQLException) {
            log.error("Failed to find settings for guild {}", guildId, e)
            throw IllegalStateException("Failed to find guild settings", e)
        }
    }

    fun set(guild: BotGuild, settings: BotGuildSettings) = set(guild.id, settings)

    fun set(guild: Guild, settings: BotGuildSettings) = set(guild.idLong, settings)

    fun set(guildId: Long, settings: BotGuildSettings) {
        val sql = "INSERT OR REPLACE INTO settings VALUES(?, ?);"

        try {
            DriverManager.getConnection(url).use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setLong(1, guildId)
                    statement.setBoolean(2, settings.warnings)
                    statement.execute()
                    log.debug("Settings for guild {} were set", guildId)
                }
            }
        } catch (e: SQLException) {
            log.error("Failed to set settings for guild {}", guildId, e)
            throw IllegalStateException("Failed to set guild settings", e)
        }
    }

    fun delete(guild: BotGuild) = delete(guild.id)

    fun delete(guildId: Long) {
        val sql = "DELETE FROM settings WHERE guild = ?;"

        try {
            DriverManager.getConnection(url).use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setLong(1, guildId)

                    statement.execute()
                    log.debug("Settings for guild {} were deleted", guildId)
                }
            }
        } catch (e: SQLException) {
            log.error("Failed to delete settings for guild {}", guildId, e)
            throw IllegalStateException("Failed to delete guild settings", e)
        }
    }
}
