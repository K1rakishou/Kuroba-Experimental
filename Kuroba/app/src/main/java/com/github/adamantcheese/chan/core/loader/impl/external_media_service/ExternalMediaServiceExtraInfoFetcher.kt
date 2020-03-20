package com.github.adamantcheese.chan.core.loader.impl.external_media_service

import com.github.adamantcheese.chan.core.loader.impl.post_comment.LinkInfoRequest
import com.github.adamantcheese.chan.core.loader.impl.post_comment.SpanUpdateBatch
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.data.video_service.MediaServiceType
import io.reactivex.Single

/**
 * Base interface for link extra info fetcher.
 * */
internal interface ExternalMediaServiceExtraInfoFetcher {
    /**
     * Each fetcher must have it's own type
     * */
    val mediaServiceType: MediaServiceType

    fun isEnabled(): Boolean

    fun fetch(requestUrl: String, linkInfoRequest: LinkInfoRequest): Single<ModularResult<SpanUpdateBatch>>

    /**
     * Whether this fetcher can parse the link
     * */
    fun linkMatchesToService(link: String): Boolean

    /**
     * May be either a unique ID representing this extra info, or, if a media service links do not
     * have a unique id, the whole link
     * */
    fun extractLinkUniqueIdentifier(link: String): String

    /**
     * An url where the GET request will be sent to retrieve the url metadata
     * */
    fun formatRequestUrl(link: String): String
}