package com.github.adamantcheese.chan.core.model

data class PostIndexed(
  val post: Post,
  // All indexes are zero-based.
  // The index of this post among other posts in a thread (not including posts removed by filters)
  val currentPostIndex: Int,
  // The index of this post among other posts in a thread (including posts removed by filters). If
  // there are any removed posts in a thread, this index will be greater than currentPostIndex
  val realPostIndex: Int
)