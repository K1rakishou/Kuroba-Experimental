package com.github.adamantcheese.database.dto

import org.joda.time.DateTime

data class YoutubeLinkExtraContent(
        val postUid: String,
        val parentLoadableUid: String,
        val url: String,
        val videoTitle: String?,
        val videoDuration: String?,
        val insertedAt: DateTime
)