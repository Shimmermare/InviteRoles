package com.shimmermare.inviteroles.test

import com.shimmermare.inviteroles.BotGuildSettings
import com.shimmermare.inviteroles.SettingsRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.sql.DriverManager

object SettingsRepositoryTest {
    private val dbPath = "file::memory:?cache=shared"

    @Test
    fun initsTable() {
        val connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")

        val repository = SettingsRepository(dbPath)
        repository.initTables()

        // This will throw if there's no table
        connection.createStatement().use { statement ->
            statement.execute("SELECT * FROM settings LIMIT 1;")
        }

        connection.close()
    }

    @Test
    fun finds() {
        val guildId = 123456789L

        val connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
        val repository = SettingsRepository(dbPath)
        repository.initTables()

        connection.createStatement().use { statement ->
            statement.execute("INSERT INTO settings VALUES (${guildId}, true);")
        }

        assertNull(repository.find(987654321L))
        assertEquals(BotGuildSettings(warnings = true), repository.find(guildId))

        connection.close()
    }

    @Test
    fun setsNew() {
        val guildId = 123456789L

        val connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
        val repository = SettingsRepository(dbPath)
        repository.initTables()

        val settings = BotGuildSettings(true)
        repository.set(guildId, settings)

        connection.createStatement().use { statement ->
            val result = statement.executeQuery("SELECT * FROM settings WHERE guild = $guildId;")
            assertTrue { result.next() }
            assertEquals(guildId, result.getLong(1))
            assertEquals(settings.warnings, result.getBoolean(2))
        }

        connection.close()
    }

    @Test
    fun setsExisting() {
        val guildId = 123456789L

        val connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
        val repository = SettingsRepository(dbPath)
        repository.initTables()

        val old = BotGuildSettings(true)

        connection.createStatement().use { statement ->
            statement.execute("INSERT INTO settings VALUES ($guildId, ${old.warnings});")
        }

        val new = old.copy(warnings = false)
        repository.set(guildId, new)

        connection.createStatement().use { statement ->
            val result = statement.executeQuery("SELECT * FROM settings WHERE guild = $guildId;")
            assertTrue(result.next())
            assertEquals(guildId, result.getLong(1))
            assertEquals(new.warnings, result.getBoolean(2))
        }

        connection.close()
    }

    @Test
    fun deletes() {
        val guildId = 123456789L

        val connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
        val repository = SettingsRepository(dbPath)
        repository.initTables()

        val settings = BotGuildSettings(true)

        connection.createStatement().use { statement ->
            statement.execute("INSERT INTO settings VALUES ($guildId, ${settings.warnings});")
        }

        repository.delete(guildId)

        connection.createStatement().use { statement ->
            val result = statement.executeQuery("SELECT * FROM settings WHERE guild = $guildId;")
            assertFalse(result.next())
        }

        connection.close()
    }
}