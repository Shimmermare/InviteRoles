package com.shimmermare.inviteroles.service

import com.shimmermare.inviteroles.configuration.BasicConfiguration
import com.shimmermare.inviteroles.logger
import net.dv8tion.jda.api.entities.Guild
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.core.io.support.ResourcePatternUtils
import org.springframework.stereotype.Service
import java.io.IOException
import java.io.InputStreamReader
import java.util.*
import javax.annotation.PostConstruct
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

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

class Locale(
    val code: String,
    private val textMap: Map<String, String>
) {
    fun apply(label: String): String? = textMap[label]

    fun apply(label: String, args: Map<String, Any>): String? {
        var text = textMap[label] ?: return null
        args.forEach { text = text.replace("%${it.key}%", it.value.toString()) }
        return text
    }
}

private class LocaleLoader(
    private val resourceLoader: ResourceLoader,
    private val basicConfiguration: BasicConfiguration
) {
    private val log = logger()

    fun load(): List<Locale> {
        val localeResources = findAllLocaleResources()

        val localeTextMaps = localeResources.map { (locale, resources) ->
            val textMaps: List<Map<String, String>> = resources.mapNotNull { resource ->
                try {
                    return@mapNotNull loadResourceToTextMap(resource)
                } catch (e: Exception) {
                    log.error("Failed to load locale {} resource {}", locale, resource)
                    return@mapNotNull null
                }
            }
            locale to textMaps
        }

        return localeTextMaps.map { (locale, textMaps) -> createLocale(locale, textMaps) }
    }

    /**
     * Searches for locale files in this order:
     * 1. Default locales specified by [BasicConfiguration.defaultLocalesSearchPattern].
     * 2. User locales specified by [BasicConfiguration.userLocalesSearchPattern].
     *
     * Found files for a locale will be returned in search order.
     */
    private fun findAllLocaleResources(): Map<String, List<Resource>> {
        val resources = HashMap<String, MutableList<Resource>>()

        try {
            findLocaleResources(basicConfiguration.defaultLocalesSearchPattern).forEach {
                resources.computeIfAbsent(it.key) { ArrayList() }.add(it.value)
            }
        } catch (e: Exception) {
            log.error(
                "Failed to search for default locale resources by pattern '{}'",
                basicConfiguration.defaultLocalesSearchPattern, e
            )
        }

        try {
            findLocaleResources(basicConfiguration.userLocalesSearchPattern).forEach {
                resources.computeIfAbsent(it.key) { ArrayList() }.add(it.value)
            }
        } catch (e: Exception) {
            log.error(
                "Failed to search for user locale resources by pattern '{}'",
                basicConfiguration.userLocalesSearchPattern, e
            )
        }

        return resources
    }

    private fun findLocaleResources(searchPattern: String): Map<String, Resource> {
        val resources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader)
            .getResources(searchPattern)
        if (resources.isEmpty()) {
            log.warn("Can't find any locale resources by pattern '{}'", searchPattern)
            return emptyMap()
        }
        val resourcesByLocale = resources.map { it.file.nameWithoutExtension to it }.toMap()
        log.info("Found {} locale resources by pattern '{}': {}", resources.size, searchPattern, resourcesByLocale.keys)
        return resourcesByLocale
    }

    @Throws(IOException::class)
    private fun loadResourceToTextMap(resource: Resource): Map<String, String> {
        val props = Properties()
        props.load(InputStreamReader(resource.inputStream, Charsets.UTF_8))
        return props.asSequence().map { (k, v) -> (k as String) to (v as String) }.toMap()
    }

    private fun createLocale(locale: String, textMaps: List<Map<String, String>>): Locale {
        val combinedTextMap = HashMap<String, String>()
        textMaps.forEach { textMap ->
            textMap.forEach {
                combinedTextMap[it.key] = it.value
            }
        }
        log.debug("Combined {} text maps for locale {}", textMaps.size, locale)
        return Locale(locale, combinedTextMap)
    }
}