package com.github.adamantcheese.database.data.video_service

/**
 * A base class for media service link extra content with content that should be common for every
 * media service (like youtube/soundcloud/etc). Inherit from this class to add new service-dependant
 * content (like, maybe, thumbnail url? For now it's not supported and there are no plans to add it.
 * But maybe in the future?)
 * */
open class MediaServiceLinkExtraContent(
        val videoUrl: String,
        val mediaServiceType: MediaServiceType,
        val videoTitle: String?,
        val videoDuration: String?
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaServiceLinkExtraContent) return false

        if (videoUrl != other.videoUrl) return false
        if (mediaServiceType != other.mediaServiceType) return false
        if (videoTitle != other.videoTitle) return false
        if (videoDuration != other.videoDuration) return false

        return true
    }

    override fun hashCode(): Int {
        var result = videoUrl.hashCode()
        result = 31 * result + mediaServiceType.hashCode()
        result = 31 * result + (videoTitle?.hashCode() ?: 0)
        result = 31 * result + (videoDuration?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "MediaServiceLinkExtraContent(videoUrl='$videoUrl', mediaServiceType=$mediaServiceType, " +
                "videoTitle=$videoTitle, videoDuration=$videoDuration)"
    }

}