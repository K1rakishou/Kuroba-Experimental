package com.github.adamantcheese.chan.core.loader

import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor

data class LoaderBatchResult(
  val chanDescriptor: ChanDescriptor,
  val post: Post,
  val results: List<LoaderResult>
)