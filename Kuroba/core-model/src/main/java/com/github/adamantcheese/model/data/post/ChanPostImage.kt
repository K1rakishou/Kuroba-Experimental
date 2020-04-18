package com.github.adamantcheese.model.data.post

import okhttp3.HttpUrl

class ChanPostImage(
        val serverFilename: String,
        val thumbnailUrl: HttpUrl? = null,
        val spoilerThumbnailUrl: HttpUrl? = null,
        val imageUrl: HttpUrl,
        val filename: String? = null,
        val extension: String? = null,
        val imageWidth: Int = 0,
        val imageHeight: Int = 0,
        val spoiler: Boolean = false,
        val isInlined: Boolean = false,
        val size: Long = 0L,
        val fileHash: String? = null,
        val type: ChanPostImageType? = null
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChanPostImage) return false

        if (imageUrl != other.imageUrl) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = imageUrl.hashCode()
        result = 31 * result + (type?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "ChanPostImage(serverFilename='$serverFilename', " +
                "imageUrl=$imageUrl," +
                "imageWidth=$imageWidth, " +
                "imageHeight=$imageHeight, " +
                "size=$size, " +
                "fileHash=$fileHash)"
    }
}