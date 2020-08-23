package com.shimmermare.inviteroles.i18n

import com.shimmermare.inviteroles.BasicConfiguration
import com.shimmermare.inviteroles.logger
import com.shimmermare.inviteroles.settings.GuildSettingsService
import net.dv8tion.jda.api.entities.Guild
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service
import java.util.*
import javax.annotation.PostConstruct

/**
 * Why custom? Because we don't want Spring MVC as dependency.
 */
@Service
class InternalizationService(
        private val resourceLoader: ResourceLoader,
        private val basicConfiguration: BasicConfiguration,
        private val guildSettingsService: GuildSettingsService
) {
    private val log = logger()

    private val locales: MutableMap<String, Locale> = HashMap()

    val availableLocales: List<String>
        get() = locales.keys.toList()

    val defaultLocale: Locale
        get() = locales[basicConfiguration.defaultLocaleCode]
                ?: throw IllegalStateException("No default locale: " + basicConfiguration.defaultLocaleCode)

    @PostConstruct
    fun loadLocales() {
        val localeLoader = LocaleLoader(resourceLoader, basicConfiguration)
        localeLoader.load().forEach {
            locales[it.code] = it
        }
    }

    fun locale(code: String): Locale? = locales[code]

    fun applyDefault(label: String): String = apply(DEFAULT_LOCALE, label)

    fun apply(guild: Guild, label: String): String = apply(guild.language, label)

    fun apply(localeCode: String, label: String): String =
            getLocaleSafe(localeCode)?.apply(label) ?: defaultLocale.apply(label) ?: "!$label!"

    fun applyDefault(label: String, vararg args: Pair<String, Any>): String = apply(DEFAULT_LOCALE, label, *args)

    fun apply(guild: Guild, label: String, vararg args: Pair<String, Any>): String = apply(guild.language, label, *args)

    fun apply(localeCode: String, label: String, vararg args: Pair<String, Any>): String =
            apply(localeCode, label, args.toMap())

    fun apply(guild: Guild, label: String, args: Map<String, Any>): String = apply(guild.language, label, args)

    fun apply(localeCode: String, label: String, args: Map<String, Any>): String =
            getLocaleSafe(localeCode)?.apply(label, args) ?: defaultLocale.apply(label, args) ?: "!$label!"

    private fun getLocaleSafe(code: String): Locale? = locales[code] ?: locales[normalizeCase(code)]

    private fun normalizeCase(code: String): String {
        val split = code.split('_', limit = 2)
        return when (split.size) {
            1 -> split[0].toLowerCase()
            2 -> split[0].toLowerCase() + "_" + split[1].toUpperCase()
            else -> throw IllegalArgumentException("$code is not a valid ISO language code")
        }
    }

    private val Guild.language
        get() = guildSettingsService.getOrCreateSettings(this).language

    companion object {
        const val DEFAULT_LOCALE = "en_US"
    }
}