package com.shimmermare.inviteroles.test

import com.shimmermare.inviteroles.BotGuildSettings
import com.shimmermare.inviteroles.SettingsRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.DriverManager

object SettingsRepositoryTest {
    @Test
    fun initsTable() {
        openInMemory { connection, repository ->
            // This will throw if there's no table
            connection.createStatement().use { statement ->
                statement.execute("SELECT * FROM settings LIMIT 1;")
            }
        }
    }

    @Test
    fun finds() {
        val guildId = 123456789L
        openInMemory { connection, repository ->
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO settings VALUES (${guildId}, true);")
            }
            assertNull(repository.find(987654321L))
            assertEquals(BotGuildSettings(warnings = true), repository.find(guildId))
        }
    }

    @Test
    fun setsNew() {
        val guildId = 123456789L
        openInMemory { connection, repository ->
            val settings = BotGuildSettings(true)
            repository.set(guildId, settings)

            connection.createStatement().use { statement ->
                val result = statement.executeQuery("SELECT * FROM settings WHERE guild = $guildId;")
                assertTrue { result.next() }
                assertEquals(guildId, result.getLong(1))
                assertEquals(settings.warnings, result.getBoolean(2))
            }
        }
    }

    @Test
    fun setsExisting() {
        val guildId = 123456789L
        openInMemory { connection, repository ->
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
        }
    }

    @Test
    fun deletes() {
        val guildId = 123456789L
        openInMemory { connection, repository ->
            val settings = BotGuildSettings(true)

            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO settings VALUES ($guildId, ${settings.warnings});")
            }

            repository.delete(guildId)

            connection.createStatement().use { statement ->
                val result = statement.executeQuery("SELECT * FROM settings WHERE guild = $guildId;")
                assertFalse(result.next())
            }
        }
    }

    private inline fun openInMemory(block: (Connection, SettingsRepository) -> Unit) {
        val db = "file::memory:?cache=shared"
        val connection = DriverManager.getConnection("jdbc:sqlite:$db")
        val repository = SettingsRepository(db)
        repository.initTable()
        block.invoke(connection, repository)
        connection.close()
    }
}