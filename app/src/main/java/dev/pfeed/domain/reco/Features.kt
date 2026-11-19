package dev.pfeed.domain.reco

import dev.pfeed.domain.filter.Blocklist
import dev.pfeed.domain.model.MediaItem

/**
 * What the recommender learns from: every tag on an item (not just the first few), normalised so
 * "Street Food", "street_food" and "#streetfood"-style variants meet, plus keywords from the title
 * (a Douyin caption's short words).
 *
 * Title keywords are prefixed `w:` so they never collide with real tags.
 */
object Features {
    const val MAX = 80
    private const val MAX_TITLE_WORDS = 12

    /** Tags that are on nearly everything and say nothing about taste. */
    private val GENERIC = setOf(
        "nsfw", "sfw", "explicit", "questionable", "safe", "rating:explicit", "rating:questionable", "rating:safe",
        "rating:e", "rating:q", "rating:s", "tagme", "highres", "absurdres", "lowres", "video", "animated", "sound",
        "webm", "mp4", "gif", "jpeg", "png", "image", "photo", "pic", "oc", "original", "nsfw_tag",
    )

    private val STOP = setOf(
        "the", "and", "for", "with", "you", "your", "her", "his", "she", "him", "this", "that", "are", "was", "were",
        "have", "has", "had", "not", "but", "all", "any", "can", "get", "got", "just", "like", "out", "off", "our",
        "who", "what", "when", "how", "why", "from", "into", "onto", "over", "she's", "he's", "i'm", "it's", "its",
        "me", "my", "mine", "they", "them", "their", "would", "could", "should", "will", "one", "two", "some", "more",
        "very", "really", "much", "here", "there", "then", "than", "also", "about", "after", "before", "again", "being",
        "been", "did", "does", "doing", "let", "let's", "make", "made", "want", "wanted", "need", "know", "think",
        "today", "tonight", "now", "new", "first", "time", "day", "back", "too", "see", "look", "looking", "love",
        "f", "m", "oc", "pic", "post", "video", "vid", "gif", "full", "link", "comments", "amp",
    )
    private val SPLIT = Regex("[^\\p{L}\\p{N}'+]+")

    /** True for tags that are on nearly everything (ratings, file formats, "highres"…). */
    fun isGeneric(tag: String): Boolean = normalizeTag(tag) in GENERIC

    fun normalizeTag(tag: String): String = Blocklist.normalize(tag).replace(' ', '_').let { SAME_AS[it] ?: it }

    /**
     * Different words for the same thing, so a taste learned under one carries to the others
     * (a like on "#猫咪" also means more "#猫").
     */
    private val SAME_AS: Map<String, String> = buildMap {
        fun same(canonical: String, vararg others: String) = others.forEach { put(it, canonical) }
        same("美食", "美食分享", "吃货", "美食探店", "探店")
        same("搞笑", "搞笑视频", "幽默", "沙雕")
        same("萌宠", "宠物", "萌宠日常")
        same("猫", "猫咪", "喵星人", "小猫")
        same("狗", "狗狗", "汪星人", "小狗")
        same("舞蹈", "跳舞", "舞蹈教学")
        same("旅行", "旅游", "旅行vlog")
        same("穿搭", "ootd", "日常穿搭")
        same("风景", "风景视频", "美景")
        same("做饭", "家常菜", "美食教程")
    }

    fun of(item: MediaItem): List<String> {
        val tags = item.tags.asSequence()
            .map(::normalizeTag)
            .filter { it.isNotBlank() && it !in GENERIC }
            .distinct()
            .toList()
        // Boards already describe the post in detail (and their "titles" are just the first tags).
        val words = if (tags.size >= 6) emptyList() else titleWords(item.title)
        // Plus where it's from and what it is, so For You learns site and format preferences too
        // (someone who only ever watches videos, or always skips one site, gets less of it).
        return (tags + words).distinct().take(MAX) + meta(item)
    }

    /** "o:{origin}", "type:{video|image|gif|comic}", and for videos of known length "len:{short|medium|long}". */
    fun meta(item: MediaItem): List<String> = listOfNotNull(
        ORIGIN + Spread.origin(item),
        TYPE + when {
            item.isGallery -> "comic"
            else -> item.type.name.lowercase().let { if (it == "embed") "video" else it }
        },
        // Clip or full scene: people usually want one or the other.
        item.durationSec?.takeIf { it > 0 }?.let { d -> LENGTH + when { d < 60 -> "short"; d < 8 * 60 -> "medium"; else -> "long" } },
    )

    fun isMeta(feature: String) = feature.startsWith(ORIGIN) || feature.startsWith(TYPE) || feature.startsWith(LENGTH)

    const val ORIGIN = "o:"
    const val TYPE = "type:"
    const val LENGTH = "len:"

    internal fun titleWords(title: String?): List<String> {
        if (title.isNullOrBlank()) return emptyList()
        return title.lowercase().split(SPLIT).asSequence()
            .map { it.trim('\'', '+') }
            // Chinese has no spaces: a short run is a word, a long one is a whole sentence (noise).
            .filter { w -> if (w.any(::isCjk)) w.length in 2..4 else w.length >= 3 && w !in STOP && !w.all(Char::isDigit) }
            .distinct()
            .take(MAX_TITLE_WORDS)
            .map { "w:$it" }
            .toList()
    }

    private fun isCjk(c: Char): Boolean = Character.UnicodeScript.of(c.code) == Character.UnicodeScript.HAN

    /** Readable label for a feature in the "what it has learned" screen. */
    /** "douyin" → "抖音"; anything else as it is. */
    private fun prettyOrigin(o: String): String = KNOWN_ORIGINS[o.lowercase()] ?: o

    private val KNOWN_ORIGINS = mapOf("douyin" to "抖音", "local" to "this phone")

    fun label(feature: String): String = when {
        feature.startsWith("w:") -> "“${feature.removePrefix("w:")}” in titles"
        feature.startsWith(ORIGIN) -> "Posts from " + feature.removePrefix(ORIGIN).let { o -> if (o.startsWith("douyin:")) "@" + o.removePrefix("douyin:") else prettyOrigin(o.substringAfter(':')) }
        feature.startsWith(TYPE) -> when (feature.removePrefix(TYPE)) {
            "video" -> "Videos"; "image" -> "Pictures"; "gif" -> "GIFs"; "comic" -> "Comics"; else -> feature
        }
        feature.startsWith(LENGTH) -> when (feature.removePrefix(LENGTH)) {
            "short" -> "Short clips (under a minute)"; "medium" -> "Videos of 1–8 minutes"; else -> "Long videos (8 minutes and up)"
        }
        else -> "#${feature.replace('_', ' ')}"
    }
}
