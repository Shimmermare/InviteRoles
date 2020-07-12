package com.shimmermare.inviteroles

import net.dv8tion.jda.api.entities.Guild
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

inline fun <reified T : Any> logger(): Logger = LoggerFactory.getLogger(T::class.java)

/**
 * Check if guild has invite with the given code. Blocking.
 */
fun Guild.hasInvite(code: String): Boolean {
    return this.retrieveInvites().complete().any { it.code == code }
}

fun Long.asChannelMention(): String = "<&$this>"

fun String.toBooleanExplicit(): Boolean {
    return when (this.toLowerCase()) {
        "t", "true", "y", "yes", "on", "enabled" -> true
        "f", "false", "n", "no", "off", "disabled" -> false
        else -> throw IllegalArgumentException("$this can't be interpreted as boolean value")
    }
}

fun <T> Optional<T>.asNullable(): T? = this.orElse(null)