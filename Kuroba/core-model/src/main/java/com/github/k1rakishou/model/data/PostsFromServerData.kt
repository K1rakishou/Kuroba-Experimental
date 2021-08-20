package com.github.k1rakishou.model.data

import com.github.k1rakishou.model.data.descriptor.PostDescriptor

class PostsFromServerData(
  val allPostDescriptors: Set<PostDescriptor>,
  val isIncrementalUpdate: Boolean
)