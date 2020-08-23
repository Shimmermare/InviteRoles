package com.shimmermare.inviteroles.i18n

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