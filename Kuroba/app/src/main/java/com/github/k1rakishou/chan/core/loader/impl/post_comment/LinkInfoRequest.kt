package com.github.k1rakishou.chan.core.loader.impl.post_comment

import com.github.k1rakishou.model.data.media.GenericVideoId
import com.github.k1rakishou.model.data.video_service.MediaServiceType

class LinkInfoRequest(
  val videoId: GenericVideoId,
  val mediaServiceType: MediaServiceType,
  val oldPostLinkableSpans: MutableList<CommentPostLinkableSpan>
)