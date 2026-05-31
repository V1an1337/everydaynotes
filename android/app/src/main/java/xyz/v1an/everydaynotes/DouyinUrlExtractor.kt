package xyz.v1an.everydaynotes

object DouyinUrlExtractor {
    private val urlRegex = Regex("""https?://[^\s，。；;:'"<>]+""", RegexOption.IGNORE_CASE)

    fun extract(text: String): String? {
        return urlRegex.find(text)?.value?.trimEnd(':', '/')
    }
}

