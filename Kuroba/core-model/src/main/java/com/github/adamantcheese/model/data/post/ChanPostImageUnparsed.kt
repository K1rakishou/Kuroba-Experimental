package com.github.adamantcheese.model.data.post

import okhttp3.HttpUrl

data class ChanPostImageUnparsed(
        val serverFilename: String,
        val thumbnailUrl: HttpUrl? = null,
        val spoilerThumbnailUrl: HttpUrl? = null,
        val imageUrl: HttpUrl? = null,
        val filename: String? = null,
        val extension: String? = null,
        val imageWidth: Int = 0,
        val imageHeight: Int = 0,
        val spoiler: Boolean = false,
        val isInlined: Boolean = false,
        val fileHash: String? = null,
        val type: ChanPostImageType? = null
)