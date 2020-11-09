package com.github.k1rakishou.model.data.thread

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPost

// TODO(KurobaEx): delete me
class ChanPostCollection(
  val chanDescriptor: ChanDescriptor,
  val posts: List<ChanPost>
)