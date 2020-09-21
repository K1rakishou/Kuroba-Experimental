package com.github.k1rakishou.chan.core.manager

import android.text.format.DateUtils
import com.github.k1rakishou.chan.core.model.Post
import com.github.k1rakishou.chan.core.settings.ChanSettings
import com.github.k1rakishou.chan.ui.helper.PostHelper
import com.github.k1rakishou.chan.utils.BackgroundUtils

/**
 * A holder for posts' info that is quite expensive to calculate on the main thread. To avoid
 * micro-freezes when opening a catalog/thread we want to unload as much heavy calculations to a
 * background thread as possible. This holder contains results of those calculations which can then
 * be used directly inside the PostCell, CardPostCell and PostStubCell classes.
 * Method [preloadPostInfo] "should" be called on a background thread (even though it's not always
 * possible
 * */
class PostPreloadedInfoHolder {
  private val postPreloadedTimeMap = hashMapOf<Long, CharSequence>()

  fun preloadPostsInfo(posts: List<Post>) {
    posts.forEach { post -> preloadPostInfo(post) }
  }

  fun getPostTime(post: Post): CharSequence {
    BackgroundUtils.ensureMainThread()

    return synchronized(this) {
      return@synchronized requireNotNull(postPreloadedTimeMap[post.no]) {
        "Post time was not preloaded for post with no ${post.no}"
      }
    }
  }

  private fun preloadPostInfo(post: Post) {
    val postTime = calculatePostTime(post)

    synchronized(this) {
      postPreloadedTimeMap[post.no] = postTime
    }
  }

  private fun calculatePostTime(post: Post): CharSequence {
    return if (ChanSettings.postFullDate.get()) {
      PostHelper.getLocalDate(post)
    } else {
      DateUtils.getRelativeTimeSpanString(
        post.time * 1000L,
        System.currentTimeMillis(),
        DateUtils.SECOND_IN_MILLIS,
        0
      )
    }
  }
}