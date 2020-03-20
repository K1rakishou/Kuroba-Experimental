package com.github.adamantcheese.model.data.video_service

import org.joda.time.Period

/**
 * A base class for media service link extra content with content that should be common for every
 * media service (like youtube/soundcloud/etc). Inherit from this class to add new service-dependant
 * content (like, maybe, thumbnail url? For now it's not supported and there are no plans to add it.
 * But maybe in the future?)
 * */
open class MediaServiceLinkExtraContent(
        // May be anything. For youtube it's the youtube's videoId but for different services it
        // may as well be the whole URL if the service doesn't have a unique id
        val videoId: String,
        val mediaServiceType: MediaServiceType,
        val videoTitle: String?,
        val videoDuration: Period?
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaServiceLinkExtraContent) return false

        if (videoId != other.videoId) return false
        if (mediaServiceType != other.mediaServiceType) return false
        if (videoTitle != other.videoTitle) return false
        if (videoDuration != other.videoDuration) return false

        return true
    }

    override fun hashCode(): Int {
        var result = videoId.hashCode()
        result = 31 * result + mediaServiceType.hashCode()
        result = 31 * result + (videoTitle?.hashCode() ?: 0)
        result = 31 * result + (videoDuration?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "MediaServiceLinkExtraContent(videoId='$videoId', mediaServiceType=$mediaServiceType, " +
                "videoTitle=$videoTitle, videoDuration=$videoDuration)"
    }

}