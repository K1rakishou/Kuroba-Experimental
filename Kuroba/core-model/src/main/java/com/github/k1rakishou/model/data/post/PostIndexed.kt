package com.github.k1rakishou.model.data.post

data class PostIndexed(
  val post: ChanPost,
  // The index of this post among other posts in a thread (not including posts removed by filters)
  val postIndex: Int
)