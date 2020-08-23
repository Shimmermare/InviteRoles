package com.shimmermare.inviteroles.command

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.utils.MiscUtil
import java.util.*
import java.util.function.Function


// Workaround for weird static import behaviour
fun literal(name: String): LiteralArgumentBuilder<CommandSource> {
    return LiteralArgumentBuilder.literal(name)
}

fun <T> argument(name: String, type: ArgumentType<T>): RequiredArgumentBuilder<CommandSource, T> {
    return RequiredArgumentBuilder.argument(name, type)
}

/**
 * A little interface to get around java type erasure.
 *
 * @see RoleArgumentType
 */
interface RoleRetriever : Function<Guild, Role?>

/**
 * Despite the name, doesn't represent [Role] by itself
 * but rather provides a function to get it from server.
 *
 * Argument may be role mention aka "<@&snowflake_id>" or role name (case insensitive).
 * In latter case the most recently created will be used.
 */
object RoleArgumentType : ArgumentType<RoleRetriever> {
    fun role(): RoleArgumentType {
        return RoleArgumentType
    }

    @Throws(CommandSyntaxException::class)
    override fun parse(reader: StringReader): RoleRetriever {
        val startPos = reader.cursor
        return if (reader.read() == '<' && reader.read() == '@' && reader.read() == '&') {
            val idStr = reader.readStringUntil('>')
            // Better parse now so exception won't be thrown later
            val id = MiscUtil.parseSnowflake(idStr)

            // TODO: replace with proper kotlin alternative
            object : RoleRetriever {
                override fun apply(guild: Guild): Role? = guild.getRoleById(id)
            }
        } else {
            reader.cursor = startPos // Don't forget to reset cursor
            val arg = reader.readString()

            // Holy shit this is ugly as hell
            // TODO: replace with proper kotlin alternative
            object : RoleRetriever {
                override fun apply(guild: Guild): Role? {
                    return guild.getRolesByName(arg, true)
                            .maxWith(Comparator.comparing { r: Role -> r.timeCreated })
                }
            }
        }
    }
}

object InviteArgumentType : ArgumentType<String> {
    fun invite(): InviteArgumentType {
        return InviteArgumentType
    }

    @Throws(CommandSyntaxException::class)
    override fun parse(reader: StringReader): String {
        // Read until the end or whitespace
        val sb = StringBuilder()
        var c: Char
        while (reader.canRead()) {
            c = reader.read()
            if (c.isWhitespace()) {
                reader.cursor-- // Brigadier expects whitespace after the arg
                break
            }
            sb.append(c)
        }
        var arg = sb.toString()

        // Parse if URL and do nothing if code
        val lastSlash = arg.lastIndexOf('/')
        if (lastSlash >= 0 && lastSlash < arg.length - 1) {
            arg = arg.substring(lastSlash + 1)
        }
        return arg
    }
}