package com.github.k1rakishou.chan.core.model

import com.github.k1rakishou.model.data.post.ChanPost

data class PostIndexed(
  val post: ChanPost,
  // All indexes are zero-based.
  // The index of this post among other posts in a thread (not including posts removed by filters)
  val currentPostIndex: Int,
  // The index of this post among other posts in a thread (including posts removed by filters). If
  // there are any removed posts in a thread, this index will be greater than currentPostIndex
  val realPostIndex: Int
)