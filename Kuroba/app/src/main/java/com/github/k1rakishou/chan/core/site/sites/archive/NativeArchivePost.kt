package com.github.k1rakishou.chan.core.site.sites.archive

data class NativeArchivePostList(
  val nextPage: Int? = null,
  val posts: List<NativeArchivePost> = emptyList()
) {

  fun isEmpty(): Boolean = posts.isEmpty()

}

sealed class NativeArchivePost {
  data class Chan4NativeArchivePost(
    val threadNo: Long,
    val comment: String
  ) : NativeArchivePost()

  data class DvachNativeArchivePost(
    val threadNo: Long,
    val comment: String
  ) : NativeArchivePost()
}