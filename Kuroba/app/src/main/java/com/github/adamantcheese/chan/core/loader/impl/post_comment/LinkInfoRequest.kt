package com.github.adamantcheese.chan.core.loader.impl.post_comment

import com.github.adamantcheese.model.data.video_service.MediaServiceType

internal class LinkInfoRequest(
  val videoId: String,
  val mediaServiceType: MediaServiceType,
  val oldPostLinkableSpans: MutableList<CommentPostLinkableSpan>
)