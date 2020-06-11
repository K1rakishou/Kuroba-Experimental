package com.github.adamantcheese.chan.core.site.loader.internal

import com.github.adamantcheese.chan.core.model.Post
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

internal abstract class AbstractPostLoader {

  protected fun fillInReplies(totalPosts: List<Post>) {
    val postsByNo: MutableMap<Long, Post> = HashMap()
    for (post in totalPosts) {
      postsByNo[post.no] = post
    }

    // Maps post no's to a list of no's that that post received replies from
    val replies: MutableMap<Long, MutableList<Long>> = HashMap()
    for (sourcePost in totalPosts) {
      for (replyTo in sourcePost.repliesTo) {
        var value = replies[replyTo]

        if (value == null) {
          value = ArrayList(3)
          replies[replyTo] = value
        }

        value.add(sourcePost.no)
      }
    }

    for ((key, value) in replies) {
      val subject = postsByNo[key]
      // Sometimes a post replies to a ghost, a post that doesn't exist.
      if (subject != null) {
        subject.repliesFrom = value
      }
    }
  }

}