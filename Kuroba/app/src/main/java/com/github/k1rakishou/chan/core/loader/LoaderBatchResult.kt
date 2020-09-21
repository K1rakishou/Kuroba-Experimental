package com.github.k1rakishou.chan.core.loader

import com.github.k1rakishou.chan.core.model.Post
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

data class LoaderBatchResult(
  val chanDescriptor: ChanDescriptor,
  val post: Post,
  val results: List<LoaderResult>
)