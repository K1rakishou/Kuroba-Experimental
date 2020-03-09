package com.github.adamantcheese.chan.core.loader.impl.post_comment

import com.github.adamantcheese.chan.core.loader.impl.external_media_service.FetcherType

internal class LinkInfoRequest(
        val originalUrl: String,
        val fetcherType: FetcherType,
        val oldPostLinkableSpans: MutableList<CommentPostLinkableSpan>
)