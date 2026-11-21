package dev.dyfeed.domain.filter

import dev.dyfeed.domain.model.MediaItem

/**
 * AI-generated pictures and clips (Stable Diffusion, NovelAI, Midjourney…), recognised by the
 * tags boards and posters give them, by AI-only boards and communities, and by titles that say so.
 * Used by the "Hide AI-generated art" setting; nothing else depends on it.
 */
object AiArt {
    /** Boards that only carry AI art. */
    val HOSTS = setOf("aibooru.online", "e6ai.net")

    private val TAGS = setOf(
        "ai", "ai_generated", "ai-generated", "aigenerated", "ai_generated_image", "ai_art", "aiart", "ai-art", "ai_artwork",
        "ai_assisted", "ai-assisted", "ai_content", "ai_composition", "ai_hentai", "aihentai", "ai_porn", "aiporn", "ai_nsfw",
        "nsfwai", "nsfw_ai", "ai_girl", "aigirl", "ai_girls", "aigirls", "ai_babe", "ai_waifu", "ai_model", "aimodel", "ai_generated_art",
        "stable_diffusion", "stablediffusion", "unstable_diffusion", "unstablediffusion", "sdxl", "novelai", "novel_ai", "nai_diffusion",
        "midjourney", "dall-e", "dalle", "dall_e", "comfyui", "pony_diffusion", "ponydiffusion", "generative_ai", "generativeai", "aigenart",
        "flux_ai", "ai_animation", "ai_video", "aivideo",
    )

    /** Subreddits and similar communities given to AI art. */
    private val ORIGIN_WORDS = listOf("aigenerated", "aiporn", "ai_porn", "aihentai", "ai_hentai", "ainsfw", "nsfwai", "aiart", "unstable_diffusion", "unstablediffusion", "stablediffusion", "aibooru", "e6ai", "aigirls", "ai_nsfw")

    private val TITLE = Regex(
        """(?i)(?:\bai[\s_-]*(?:generated|art|artwork|porn|hentai|girls?|nsfw|assisted|image|waifu)\b|#ai\b|\bstable\s*diffusion\b|\bmidjourney\b|\bnovel\s*ai\b|\bdall[\s·-]?e\b|\bsdxl\b|\bcomfyui\b)""",
    )

    /** Douyin's words for it: AI创作, AI绘画, AIGC, AI生成, AI视频, 数字人 (AI presenters)… */
    private val CHINESE = Regex(
        """(?i)(?:(?:ai|ＡＩ|人工智能)\s*(?:生成|创作|绘画|绘图|作图|画图|动画|视频|写真|短剧|漫剧|漫画|合成|换脸|配音|歌手|翻唱|模特|美女|特效|变装|复活|制作|做的|作品|音乐|歌曲|唱|演唱|主播|虚拟|绘本|电影|动漫|风格|图片|照片|还原|修复|扩图|舞蹈|跳舞|老照片)|aigc|数字人|虚拟人|虚拟主播|ai小说|ai漫|即梦|可灵|海螺ai|通义万相|文心一格|豆包ai|剪映ai|midjourney|sora|vidu|内容由ai生成|由ai生成|疑似ai|ai辅助|漫剧|动态漫|小说推文|推文漫|ai解说)""",
    )
    private val CHINESE_TAGS = setOf(
        "ai", "aigc", "ai创作", "ai绘画", "ai生成", "ai视频", "ai动画", "数字人", "ai短剧", "ai漫剧", "ai写真", "ai翻唱", "ai作图", "ai特效",
        "人工智能", "ai音乐", "ai歌曲", "ai制作", "ai作品", "ai动漫", "ai电影", "ai漫画", "ai绘本", "ai还原", "ai修复", "ai老照片", "ai舞蹈",
        "虚拟人", "虚拟主播", "即梦", "即梦ai", "可灵", "可灵ai", "海螺ai", "豆包", "剪映ai", "通义万相", "sora", "midjourney", "aiart", "ai艺术",
        "ai原创", "ai小说", "ai故事", "ai配音", "aiai",
        // Douyin's "manga dramas" and novel read-alongs: nearly all made with AI images and voices now.
        "漫剧", "动态漫", "小说推文", "推文", "漫画解说",
    )

    /** "AI" standing on its own in a Chinese caption, name or tag ("用AI做的", "#AI", "AI美女"): Douyin's AI posts nearly always say it. */
    private val LONE_AI = Regex("""(?i)(?<![a-z])(?:ai|ＡＩ)(?![a-z])""")

    fun isAi(item: MediaItem): Boolean {
        // Douyin's own label: the post says it was made with AI.
        if (item.extras[AI_LABEL] == "1") return true
        if (item.tags.any { normalize(it) in CHINESE_TAGS }) return true
        if (item.title?.let { CHINESE.containsMatchIn(it) } == true) return true
        // Douyin posts: "AI" on its own in the caption, a tag or the creator's name. (Hidden by
        // default and wanted gone altogether: a post merely about AI goes too, which is the trade.)
        if (item.sourceId == "douyin") {
            val text = listOfNotNull(item.title, item.creator) + item.tags
            if (text.any { LONE_AI.containsMatchIn(it) || CHINESE.containsMatchIn(it) }) return true
        }
        val origin = dev.dyfeed.domain.reco.Spread.origin(item).lowercase()
        if (HOSTS.any { origin.contains(it) }) return true
        val community = origin.removePrefix("r/")
        if (ORIGIN_WORDS.any { community.contains(it) }) return true
        if (item.tags.any { normalize(it) in TAGS }) return true
        return item.title?.let { TITLE.containsMatchIn(it) } == true
    }

    /** Extra key set by the Douyin parser when a post carries Douyin's AI-made label. */
    const val AI_LABEL = "ai"

    private fun normalize(tag: String) = tag.trim().removePrefix("#").lowercase().replace(' ', '_')
}
