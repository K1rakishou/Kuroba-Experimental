package com.github.k1rakishou.chan.core.loader

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPost

data class LoaderBatchResult(
  val chanDescriptor: ChanDescriptor,
  val post: ChanPost,
  val results: List<LoaderResult>
)