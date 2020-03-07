package com.github.adamantcheese.chan.core.model

import com.github.adamantcheese.chan.ui.text.span.PostLinkable

class PostCommentBuilder(
        private var comment: CharSequence? = null,
        private val postLinkables: MutableSet<PostLinkable> = mutableSetOf()
) {

    @Synchronized
    fun setComment(comment: CharSequence) {
        this.comment = comment
    }

    @Synchronized
    fun getComment(): CharSequence {
        return checkNotNull(this.comment) { "Comment is null!" }
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

    companion object {
        @JvmStatic
        fun create() = PostCommentBuilder()
    }

}