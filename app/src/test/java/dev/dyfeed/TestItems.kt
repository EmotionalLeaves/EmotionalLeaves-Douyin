package dev.dyfeed

import dev.dyfeed.domain.model.MediaItem
import dev.dyfeed.domain.model.MediaType
import java.time.Instant

fun item(
    id: String,
    source: String = "s1",
    title: String? = "Title $id",
    creator: String? = null,
    tags: List<String> = emptyList(),
    duration: Int? = null,
    type: MediaType = MediaType.VIDEO,
    published: Instant? = null,
) = MediaItem(
    id = id,
    sourceId = source,
    type = type,
    title = title,
    creator = creator,
    tags = tags,
    durationSec = duration,
    thumbnailUrl = null,
    pageUrl = "https://example.invalid/$id",
    publishedAt = published,
)
