package com.github.adamantcheese.chan.core.model

import com.github.adamantcheese.chan.ui.text.span.PostLinkable

// Thread safe
data class PostComment(
  @get:Synchronized
  @set:Synchronized
  var comment: CharSequence,
  @get:Synchronized
  val linkables: List<PostLinkable>
) {

  @Synchronized
  fun getAllLinkables(): List<PostLinkable> {
    return linkables.toList()
  }

  @Synchronized
  fun hasComment() = comment.isNotEmpty()

  @Synchronized
  fun containsPostLinkable(postLinkable: PostLinkable): Boolean {
    return linkables.contains(postLinkable)
  }

  override fun toString(): String {
    return "PostComment(comment='${comment.take(64)}\', linkablesCount=${linkables.size})"
  }

  companion object {
    @JvmStatic
    fun empty() = PostComment("", mutableListOf())
  }

}