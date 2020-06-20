package com.shimmermare.inviteroles.service

import com.shimmermare.inviteroles.configuration.BasicConfiguration
import com.shimmermare.inviteroles.logger
import net.dv8tion.jda.api.entities.Guild
import org.springframework.core.io.ResourceLoader
import org.springframework.core.io.support.PropertiesLoaderUtils
import org.springframework.core.io.support.ResourcePatternUtils
import org.springframework.stereotype.Service
import java.util.*
import javax.annotation.PostConstruct
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
    private val log = logger<InternalizationService>()

    private val locales: MutableMap<String, Locale> = HashMap()

    val availableLocales: List<String>
        get() = locales.keys.toList()

    @PostConstruct
    fun loadLocales() {
        // Find files
        val localeFiles = ResourcePatternUtils.getResourcePatternResolver(resourceLoader)
            .getResources(basicConfiguration.localesFolderPattern)
        if (localeFiles.isEmpty()) {
            log.error("Can't find any locale files by {} pattern", basicConfiguration.localesFolderPattern)
            return
        }

        // Map to <lang code, resource>
        val localeFilesByName = localeFiles.map { it.file.nameWithoutExtension to it }.toMap()
        log.info("Found {} locale files: {}", localeFiles.size, localeFilesByName.keys)

        // Load properties from files
        val localePropertiesByName = HashMap<String, Properties>()
        localeFilesByName.forEach { (name, resource) ->
            try {
                localePropertiesByName[name] = PropertiesLoaderUtils.loadProperties(resource)
            } catch (e: Exception) {
                log.error("Can't load {} locale file", name, e)
            }
        }

        localePropertiesByName.forEach { (name, properties) ->
            @Suppress("UNCHECKED_CAST")
            locales[name] = Locale(name, properties.toMap() as Map<String, String>)
        }
    }

    fun locale(code: String): Locale? = locales[code]

    fun apply(guild: Guild, label: String): String =
        apply(guildSettingsService.getOrCreateSettings(guild).language, label)

    fun apply(localeCode: String, label: String): String =
        getLocaleOrDefault(localeCode)?.apply(label) ?: label

    fun apply(guild: Guild, label: String, vararg args: Pair<String, Any>): String =
        apply(guildSettingsService.getOrCreateSettings(guild).language, label, *args)

    fun apply(localeCode: String, label: String, vararg args: Pair<String, Any>): String =
        apply(localeCode, label, args.toMap())

    fun apply(guild: Guild, label: String, args: Map<String, Any>): String =
        apply(guildSettingsService.getOrCreateSettings(guild).language, label, args)

    fun apply(localeCode: String, label: String, args: Map<String, Any>): String =
        getLocaleOrDefault(localeCode)?.apply(label, args) ?: label

    private fun getLocaleOrDefault(code: String): Locale? =
        locales[normalizeCase(code)] ?: locales[basicConfiguration.defaultLocaleCode]

    private fun normalizeCase(code: String): String {
        val split = code.split('_', limit = 2)
        return when (split.size) {
            1 -> split[0].toLowerCase()
            2 -> split[0].toLowerCase() + "_" + split[1].toUpperCase()
            else -> throw IllegalArgumentException("$code is not a valid ISO language code")
        }
    }
}

class Locale(
    val code: String,
    private val textMap: Map<String, String>
) {
    fun apply(label: String): String = textMap[label] ?: "!$label!"

    fun apply(label: String, args: Map<String, Any>): String {
        var text = textMap[label] ?: return "!$label!"
        args.forEach { text = text.replace("%${it.key}%", it.value.toString()) }
        return text
    }
}

