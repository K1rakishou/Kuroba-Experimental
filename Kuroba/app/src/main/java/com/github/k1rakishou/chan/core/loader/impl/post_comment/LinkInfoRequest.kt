package com.github.k1rakishou.chan.core.loader.impl.post_comment

import com.github.k1rakishou.model.data.video_service.MediaServiceType

internal class LinkInfoRequest(
  val videoId: String,
  val mediaServiceType: MediaServiceType,
  val oldPostLinkableSpans: MutableList<CommentPostLinkableSpan>
)