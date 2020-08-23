package com.shimmermare.inviteroles.i18n

import com.shimmermare.inviteroles.BasicConfiguration
import com.shimmermare.inviteroles.logger
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.core.io.support.ResourcePatternUtils
import java.io.IOException
import java.io.InputStreamReader
import java.util.*


class LocaleLoader(
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