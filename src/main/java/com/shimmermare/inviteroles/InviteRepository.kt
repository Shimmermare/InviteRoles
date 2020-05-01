package com.shimmermare.inviteroles

import net.dv8tion.jda.api.entities.Guild
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.DriverManager
import java.sql.SQLException

/**
 * Create a new connection each time to avoid shenanigans. This is SQLite so it's dirt cheap.
 */
class InviteRepository(dbPath: String) {
    private val log: Logger = LoggerFactory.getLogger(InviteRepository::class.java)
    private val url = "jdbc:sqlite:$dbPath"

    fun initTable() {
        val sql = ("CREATE TABLE IF NOT EXISTS invites (\n"
                + "code text NOT NULL PRIMARY KEY, \n"
                + "guild bigint NOT NULL, \n"
                + "role bigint NOT NULL\n"
                + ");")

        try {
            DriverManager.getConnection(url).use { connection ->
                connection.createStatement().use { statement ->
                    if (statement.execute(sql)) {
                        log.debug("Invite table didn't exist and was created")
                    } else {
                        log.debug("Invite table already exists")
                    }
                }
            }
        } catch (e: SQLException) {
            log.error("Failed to initialize table", e)
            throw IllegalStateException("Failed to initialize table", e)
        }
    }

    fun find(inviteCode: String): BotGuildInvite? {
        val sql = "SELECT * FROM invites WHERE code = ?;"

        try {
            DriverManager.getConnection(url).use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, inviteCode)

                    val result = statement.executeQuery()
                    return if (result.next()) {
                        val invite = BotGuildInvite(inviteCode, result.getLong(2), result.getLong(3))
                        log.debug("Found invite {}", invite)
                        invite
                    } else {
                        log.debug("Invite {} doesn't exist", inviteCode)
                        null
                    }
                }
            }
        } catch (e: SQLException) {
            log.error("Failed to find invite {}", inviteCode, e)
            throw IllegalStateException("Failed to find invite", e)
        }
    }

    fun set(invite: BotGuildInvite) {
        val sql = "INSERT OR REPLACE INTO invites VALUES(?, ?, ?);"

        try {
            DriverManager.getConnection(url).use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, invite.code)
                    statement.setLong(2, invite.guildId)
                    statement.setLong(3, invite.roleId)

                    statement.execute()
                    log.debug("Invite {} was set", invite)
                }
            }
        } catch (e: SQLException) {
            log.error("Failed to set invite {}", invite, e)
            throw IllegalStateException("Failed to set invite", e)
        }
    }

    fun delete(invite: BotGuildInvite) = delete(invite.code)

    fun delete(inviteCode: String) {
        val sql = "DELETE FROM invites WHERE code = ?;"

        try {
            DriverManager.getConnection(url).use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, inviteCode)
                    statement.execute()
                    log.debug("Invite {} was deleted", inviteCode)
                }
            }
        } catch (e: SQLException) {
            log.error("Failed to delete invite {}", inviteCode, e)
            throw IllegalStateException("Failed to delete invite", e)
        }
    }

    fun findAllOfGuild(guild: Guild): Set<BotGuildInvite> = findAllOfGuild(guild.idLong)

    fun findAllOfGuild(guildId: Long): Set<BotGuildInvite> {
        val sql = "SELECT * FROM invites WHERE guild = ?;"

        try {
            DriverManager.getConnection(url).use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setLong(1, guildId)

                    val invites: MutableSet<BotGuildInvite> = HashSet()
                    val result = statement.executeQuery()
                    while (result.next()) {
                        val invite = BotGuildInvite(result.getString(1), result.getLong(2), result.getLong(3))
                        invites.add(invite)
                    }
                    log.debug("Found {} invites for guild {}", invites.size, guildId)
                    return invites
                }
            }
        } catch (e: SQLException) {
            log.error("Failed to find invites for guild {}", guildId, e)
            throw IllegalStateException("Failed to find invites for guild", e)
        }
    }

    fun setAllOfGuild(invites: Collection<BotGuildInvite>) {
        val sql = "INSERT OR REPLACE INTO invites VALUES(?, ?, ?);"

        try {
            DriverManager.getConnection(url).use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    invites.forEach { invite ->
                        statement.setString(1, invite.code)
                        statement.setLong(2, invite.guildId)
                        statement.setLong(3, invite.roleId)
                        statement.addBatch()
                    }

                    statement.executeBatch()
                    log.debug("{} invites were set: {} ", invites.size, invites)
                }
            }
        } catch (e: SQLException) {
            log.error("Failed to set {} invites: {}", invites.size, invites, e)
            throw IllegalStateException("Failed to set invite", e)
        }
    }

    fun deleteAllOfGuild(guildId: Long) {
        val sql = "DELETE FROM invites WHERE guild = ?;"

        try {
            DriverManager.getConnection(url).use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setLong(1, guildId)
                    statement.execute()
                    log.debug("Invites for guild {} were deleted", guildId)
                }
            }
        } catch (e: SQLException) {
            log.error("Failed to delete invites for guild {}", guildId, e)
            throw IllegalStateException("Failed to delete invites fro guild", e)
        }
    }
}