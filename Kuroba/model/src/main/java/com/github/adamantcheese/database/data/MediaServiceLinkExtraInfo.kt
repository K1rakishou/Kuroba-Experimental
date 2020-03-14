package com.github.adamantcheese.database.data

data class MediaServiceLinkExtraInfo(
        val videoTitle: String?,
        val videoDuration: String?
) {
    companion object {
        fun empty(): MediaServiceLinkExtraInfo {
            return MediaServiceLinkExtraInfo(null, null)
        }
    }
}