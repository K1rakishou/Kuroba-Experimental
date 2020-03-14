package com.github.adamantcheese.chan.core.loader.impl.post_comment

import com.github.adamantcheese.database.data.video_service.MediaServiceType

internal class LinkInfoRequest(
        val originalUrl: String,
        val mediaServiceType: MediaServiceType,
        val oldPostLinkableSpans: MutableList<CommentPostLinkableSpan>
)