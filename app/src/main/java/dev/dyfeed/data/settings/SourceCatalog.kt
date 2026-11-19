package dev.dyfeed.data.settings

/**
 * Popular Douyin topics, as Douyin itself names them (searches go out in Chinese), each with an
 * English gloss for the chips. Used by the first-run picker, Search's suggestions and Surprise me.
 */
object SourceCatalog {
    val topics: List<Pair<String, String>> = listOf(
        "美食" to "food", "街头小吃" to "street food", "做饭" to "cooking", "搞笑" to "funny",
        "舞蹈" to "dance", "音乐" to "music", "翻唱" to "covers", "旅行" to "travel", "风景" to "scenery",
        "城市" to "cities", "重庆" to "Chongqing", "上海" to "Shanghai", "北京" to "Beijing", "成都" to "Chengdu",
        "穿搭" to "outfits", "汉服" to "hanfu", "美妆" to "makeup", "萌宠" to "pets", "猫" to "cats", "狗" to "dogs",
        "熊猫" to "pandas", "动漫" to "anime", "游戏" to "games", "科技" to "tech", "数码" to "gadgets",
        "汽车" to "cars", "健身" to "fitness", "篮球" to "basketball", "足球" to "football", "武术" to "martial arts",
        "手工" to "crafts", "绘画" to "art", "书法" to "calligraphy", "摄影" to "photography", "电影" to "movies",
        "短剧" to "short dramas", "历史" to "history", "知识" to "learning", "中文学习" to "learn Chinese",
        "农村生活" to "country life", "建筑" to "architecture", "治愈" to "relaxing", "解压" to "satisfying",
        "变装" to "transformations", "情侣" to "couples", "vlog" to "vlogs", "街拍" to "street style",
    )

    /** Just the search terms. */
    val terms: List<String> = topics.map { it.first }

    private val gloss: Map<String, String> = topics.toMap()

    /** "美食 · food" for a known topic, the topic itself otherwise. */
    fun label(topic: String): String = gloss[topic]?.let { "$topic · $it" } ?: topic
}
