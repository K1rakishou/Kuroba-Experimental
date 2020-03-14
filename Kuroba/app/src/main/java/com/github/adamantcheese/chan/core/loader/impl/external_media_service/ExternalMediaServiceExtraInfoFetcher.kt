package com.github.adamantcheese.chan.core.loader.impl.external_media_service

import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.chan.core.loader.impl.post_comment.LinkInfoRequest
import com.github.adamantcheese.chan.core.loader.impl.post_comment.SpanUpdateBatch
import com.github.adamantcheese.database.data.video_service.MediaServiceType
import io.reactivex.Flowable

/**
 * Base interface for link extra info fetcher. For now only [YoutubeMediaServiceExtraInfoFetcher] is
 * supported.
 * */
internal interface ExternalMediaServiceExtraInfoFetcher {
    /**
     * Each fetcher must have it's own type
     * */
    val mediaServiceType: MediaServiceType

    fun isEnabled(): Boolean

    fun fetch(
            loadableUid: String,
            postUid: String,
            requestUrl: String,
            linkInfoRequest: LinkInfoRequest
    ): Flowable<ModularResult<SpanUpdateBatch>>

    /**
     * Whether this fetcher can parse the link
     * */
    fun linkMatchesToService(link: String): Boolean

    /**
     * An url where the GET request will be sent to retrieve the url metadata (for now only video
     * title video duration are supported)
     * */
    fun formatRequestUrl(link: String): String
}