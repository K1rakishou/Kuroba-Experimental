package com.github.k1rakishou.model.data.post

import android.text.Spannable
import android.text.SpannableString
import com.github.k1rakishou.core_spannable.PostLinkable

// Thread safe
class PostCommentBuilder(
  private var unparsedComment: String? = null,
  private var parsedComment: Spannable? = null,
  private val postLinkables: MutableSet<PostLinkable> = mutableSetOf()
) {
  var commentUpdateCounter: Int = 0
    private set

  @Synchronized
  fun setUnparsedComment(comment: String) {
    this.unparsedComment = comment
    ++this.commentUpdateCounter
  }

  @Synchronized
  fun setParsedComment(comment: Spannable) {
    this.parsedComment = comment
  }

  @Synchronized
  fun getComment(): CharSequence {
    if (parsedComment != null) {
      return parsedComment!!
    }

    return unparsedComment ?: ""
  }

  @Synchronized
  fun getUnparsedComment(): String {
    return unparsedComment ?: ""
  }

  @Synchronized
  fun addPostLinkable(linkable: PostLinkable) {
    this.postLinkables.add(linkable)
  }

  @Synchronized
  fun getAllLinkables(): List<PostLinkable> {
    return postLinkables.toList()
  }

  @Synchronized
  fun setPostLinkables(postLinkables: List<PostLinkable>) {
    this.postLinkables.clear()
    this.postLinkables.addAll(postLinkables)
  }

  @Synchronized
  fun hasComment() = unparsedComment != null

  @Synchronized
  fun copy(): PostCommentBuilder {
    val parsedCommentCopy = if (parsedComment == null) {
      parsedComment
    } else {
      SpannableString(parsedComment)
    }

    return PostCommentBuilder(
      unparsedComment = unparsedComment,
      parsedComment = parsedCommentCopy,
      postLinkables = postLinkables.toMutableSet()
    )
  }

  override fun toString(): String {
    return "PostCommentBuilder(comment=${unparsedComment?.take(64)})"
  }

  companion object {
    @JvmStatic
    fun create() = PostCommentBuilder()
  }

}