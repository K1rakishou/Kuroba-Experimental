package com.github.k1rakishou.model.data.post

import android.text.Spannable
import android.text.SpannableString
import com.github.k1rakishou.core_spannable.PostLinkable

// Thread safe
class PostCommentBuilder(
  private var comment: Spannable? = null,
  private val postLinkables: MutableSet<PostLinkable> = mutableSetOf()
) {
  var commentUpdateCounter: Int = 0
    private set

  @Synchronized
  fun setComment(comment: Spannable) {
    this.comment = comment
    ++this.commentUpdateCounter
  }

  @Synchronized
  fun setParsedComment(comment: Spannable) {
    this.comment = comment
  }

  @Synchronized
  fun getComment(): CharSequence {
    return comment ?: ""
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
  fun setPostLinkables(postLinkables: MutableSet<PostLinkable>) {
    this.postLinkables.clear()
    this.postLinkables.addAll(postLinkables)
  }

  @Synchronized
  fun hasComment() = comment != null

  @Synchronized
  fun copy(): PostCommentBuilder {
    val commentCopy = if (comment == null) {
      comment
    } else {
      SpannableString(comment)
    }

    return PostCommentBuilder(
      commentCopy,
      postLinkables.toMutableSet()
    )
  }

  override fun toString(): String {
    return "PostCommentBuilder(comment=${comment?.take(64)})"
  }

  companion object {
    @JvmStatic
    fun create() = PostCommentBuilder()
  }

}