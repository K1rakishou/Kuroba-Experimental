package com.github.k1rakishou.model.data

import com.github.k1rakishou.model.data.descriptor.PostDescriptor

class PostsFromServerData(
  val allPostDescriptors: Set<PostDescriptor>,
  val isIncrementalUpdate: Boolean,
  // When false that means we are reloading posts from the cache/database and not loading them from
  // the server.
  val isUpdatingDataFromTheServer: Boolean
)