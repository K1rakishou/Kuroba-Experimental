package com.github.adamantcheese.chan.core.model

import com.github.adamantcheese.chan.ui.text.span.PostLinkable

// Thread safe
class PostCommentBuilder(
  private var comment: CharSequence? = null,
  private val postLinkables: MutableSet<PostLinkable> = mutableSetOf()
) {
  var commentUpdateCounter: Int = 0
    private set

  @Synchronized
  fun setComment(comment: CharSequence) {
    this.comment = comment
    ++this.commentUpdateCounter
  }

  @Synchronized
  fun setParsedComment(comment: CharSequence) {
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
  fun toPostComment(): PostComment {
    val c = checkNotNull(comment) { "Comment is null!" }

    return PostComment(c, postLinkables.toList())
  }

  override fun toString(): String {
    return "PostCommentBuilder(comment=${comment?.take(64)})"
  }

  companion object {
    @JvmStatic
    fun create() = PostCommentBuilder()
  }

}