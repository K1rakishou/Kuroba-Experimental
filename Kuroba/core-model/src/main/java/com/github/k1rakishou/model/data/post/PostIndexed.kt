package com.github.k1rakishou.model.data.post

data class PostIndexed(
  val chanPost: ChanPost,
  // The index of this post among other posts in a thread (not including posts removed by filters)
  val postIndex: Int
)

data class ChanPostWithFilterResult(
  val chanPost: ChanPost,
  var postFilterResult: PostFilterResult = PostFilterResult.Leave
)

enum class PostFilterResult {
  Leave,
  Hide,
  Remove
}