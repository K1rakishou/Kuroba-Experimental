package com.github.adamantcheese.database.dto.video_service

import org.joda.time.DateTime

/**
 * A base class for media service link extra content with content that should be common for every
 * media service (like youtube/soundcloud/etc). Inherit from this class to add new service-dependant
 * content (like, maybe, thumbnail url? For now it's not supported and there are no plans to add it.
 * But maybe in the future?)
 * */
open class MediaServiceLinkExtraContent(
        val postUid: String,
        val parentLoadableUid: String,
        val mediaServiceType: MediaServiceType,
        val url: String,
        val videoTitle: String?,
        val videoDuration: String?,
        val insertedAt: DateTime
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaServiceLinkExtraContent) return false

        if (postUid != other.postUid) return false
        if (parentLoadableUid != other.parentLoadableUid) return false
        if (mediaServiceType != other.mediaServiceType) return false
        if (url != other.url) return false
        if (videoTitle != other.videoTitle) return false
        if (videoDuration != other.videoDuration) return false
        if (insertedAt != other.insertedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = postUid.hashCode()
        result = 31 * result + parentLoadableUid.hashCode()
        result = 31 * result + mediaServiceType.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + (videoTitle?.hashCode() ?: 0)
        result = 31 * result + (videoDuration?.hashCode() ?: 0)
        result = 31 * result + insertedAt.hashCode()
        return result
    }

    override fun toString(): String {
        return "MediaServiceLinkExtraContent(postUid='$postUid', parentLoadableUid='$parentLoadableUid', " +
                "mediaServiceType=$mediaServiceType, url='$url', videoTitle=$videoTitle, " +
                "videoDuration=$videoDuration, insertedAt=$insertedAt)"
    }

}