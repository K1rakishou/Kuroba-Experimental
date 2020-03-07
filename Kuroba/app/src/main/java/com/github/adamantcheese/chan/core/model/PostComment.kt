package com.github.adamantcheese.chan.core.model

import com.github.adamantcheese.chan.ui.text.span.PostLinkable

// TODO(ODL): figure out whether it's possible to get rid of "linkables" list and just get them from
//  the comment itself by using spannable.getSpans() since we don't really use other than in couple
//  of places.
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

    companion object {
        @JvmStatic
        fun empty() = PostComment("", mutableListOf())
    }

}