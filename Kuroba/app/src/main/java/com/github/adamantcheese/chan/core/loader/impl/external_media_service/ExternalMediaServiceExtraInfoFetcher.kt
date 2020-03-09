package com.github.adamantcheese.chan.core.loader.impl.external_media_service

import android.graphics.Bitmap
import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.chan.core.loader.impl.post_comment.ExtraLinkInfo
import io.reactivex.Flowable
import okhttp3.Response

/**
 * Base interface for link extra info fetcher. For now only [YoutubeMediaServiceExtraInfoFetcher] is
 * supported.
 * */
internal interface ExternalMediaServiceExtraInfoFetcher {
    /**
     * Each fetcher must have it's own type
     * */
    val fetcherType: FetcherType

    fun isEnabled(): Boolean

    /**
     * Icon to prepend the link with
     * */
    fun getIconBitmap(): Bitmap

    fun getFromCache(postUid: String, url: String): Flowable<ModularResult<ExtraLinkInfo?>>

    fun storeIntoCache(
            postUid: String,
            loadableUid: String,
            url: String,
            extraLinkInfo: ExtraLinkInfo
    ): Flowable<ModularResult<Unit>>

    /**
     * Whether a link belongs to this fetcher
     * */
    fun linkMatchesToService(link: String): Boolean

    /**
     * An url where the GET request will be sent to retrieve the url metadata (for now only video
     * title video duration are supported)
     * */
    fun formatRequestUrl(link: String): String

    /**
     * Extract video title and duration from the response body
     * */
    fun extractExtraLinkInfo(response: Response): ExtraLinkInfo
}