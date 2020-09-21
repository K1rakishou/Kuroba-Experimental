package com.github.k1rakishou.chan.core.loader.impl.post_comment

import com.github.k1rakishou.chan.ui.text.span.PostLinkable

internal data class CommentPostLinkableSpan(
  val postLinkable: PostLinkable,
  val start: Int,
  val end: Int
) {
  override fun toString(): String {
    val postLinkableString = when {
      postLinkable.key != null -> postLinkable.key.toString()
      postLinkable.linkableValue != null -> postLinkable.linkableValue.toString()
      else -> "<unknown>"
    }

    return "CommentSpan(postLinkable=$postLinkableString, start=$start, end=$end)"
  }
}