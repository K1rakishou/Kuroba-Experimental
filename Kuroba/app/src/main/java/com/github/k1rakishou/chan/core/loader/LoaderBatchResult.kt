package com.github.k1rakishou.chan.core.loader

import com.github.k1rakishou.model.data.descriptor.PostDescriptor

data class LoaderBatchResult(
  val postDescriptor: PostDescriptor,
  val results: List<LoaderResult>
)