package com.shimmermare.inviteroles.test

import com.shimmermare.inviteroles.BotGuildInvite
import com.shimmermare.inviteroles.InviteRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.assertNotNull

object InviteRepositoryTest {
    @Test
    fun initsTable() {
        openInMemory { connection, repository ->
            // This will throw if there's no table
            connection.createStatement().use { statement ->
                statement.execute("SELECT * FROM invites LIMIT 1;")
            }
        }
    }

    @Test
    fun finds() {
        val invite = BotGuildInvite("CQ-CQ-CQ-DX", 12345, 98765)
        openInMemory { connection, repository ->
            connection.insertInvite(invite)
            assertNull(repository.find("random"))
            assertEquals(invite, repository.find(invite.code))
        }
    }

    @Test
    fun setsNew() {
        val invite = BotGuildInvite("CQ-CQ-CQ-DX", 12345, 98765)
        openInMemory { connection, repository ->
            repository.set(invite)
            assertEquals(invite, connection.selectInvite(invite.code))
        }
    }

    @Test
    fun setsExisting() {
        val old = BotGuildInvite("CQ-CQ-CQ-DX", 11111, 22222)
        val new = old.copy(guildId = 12345, roleId = 98765)

        openInMemory { connection, repository ->
            connection.insertInvite(old)
            repository.set(new)
            assertEquals(new, connection.selectInvite(new.code))
        }
    }

    @Test
    fun deletes() {
        val invite = BotGuildInvite("CQ-CQ-CQ-DX", 12345, 98765)
        openInMemory { connection, repository ->
            connection.insertInvite(invite)
            repository.delete(invite)
            assertNull(connection.selectInvite(invite.code))
        }
    }

    @Test
    fun findsAllOfGuild() {
        val invites = listOf(
            BotGuildInvite("AAAAA", 11111, 123),
            BotGuildInvite("BBBBB", 11111, 123),
            BotGuildInvite("CCCCC", 22222, 123),
            BotGuildInvite("DDDDD", 11111, 123)
        )
        openInMemory { connection, repository ->
            invites.forEach { connection.insertInvite(it) }
            val found = repository.findAllOfGuild(11111)
            assertEquals(listOf("AAAAA", "BBBBB", "DDDDD"), found.map(BotGuildInvite::code))
        }
    }

    @Test
    fun setsAllOfGuild() {
        val invites = listOf(
            BotGuildInvite("AAAAA", 11111, 123),
            BotGuildInvite("BBBBB", 22222, 456),
            BotGuildInvite("CCCCC", 33333, 789)
        )
        openInMemory { connection, repository ->
            repository.setAll(invites)

            assertEquals(invites[0], connection.selectInvite("AAAAA"))
            assertEquals(invites[1], connection.selectInvite("BBBBB"))
            assertEquals(invites[2], connection.selectInvite("CCCCC"))
        }
    }

    @Test
    fun deletesAllOfGuild() {
        val invites = listOf(
            BotGuildInvite("AAAAA", 11111, 123),
            BotGuildInvite("BBBBB", 11111, 456),
            BotGuildInvite("CCCCC", 22222, 789)
        )
        openInMemory { connection, repository ->
            repository.setAll(invites)
            repository.deleteAllOfGuild(11111)

            assertNull(connection.selectInvite("AAAAA"))
            assertNull(connection.selectInvite("BBBBB"))
            assertNotNull(connection.selectInvite("CCCCC"))
        }
    }

    private inline fun openInMemory(block: (Connection, InviteRepository) -> Unit) {
        val db = "file::memory:?cache=shared"
        val connection = DriverManager.getConnection("jdbc:sqlite:$db")
        val repository = InviteRepository(db)
        repository.initTable()
        block.invoke(connection, repository)
        connection.close()
    }

    private fun Connection.insertInvite(invite: BotGuildInvite) {
        this.createStatement().use { statement ->
            statement.execute("INSERT INTO invites VALUES('${invite.code}', ${invite.guildId}, ${invite.roleId});")
        }
    }

    private fun Connection.selectInvite(code: String): BotGuildInvite? {
        this.createStatement().use { statement ->
            val result = statement.executeQuery("SELECT * FROM invites WHERE code = '$code';")
            val selected = HashSet<BotGuildInvite>()
            while (result.next()) {
                selected.add(
                    BotGuildInvite(
                        code = result.getString(1),
                        guildId = result.getLong(2),
                        roleId = result.getLong(3)
                    )
                )
            }
            if (selected.isNotEmpty()) {
                assertEquals(selected.size, 1)
            }
            return selected.firstOrNull()
        }
    }
}