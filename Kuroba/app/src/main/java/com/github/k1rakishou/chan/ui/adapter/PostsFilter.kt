/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.ui.adapter

import android.text.TextUtils
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.helper.PostHideHelper
import com.github.k1rakishou.chan.core.model.Post
import com.github.k1rakishou.chan.core.model.PostIndexed
import com.github.k1rakishou.chan.utils.Logger
import java.util.*
import javax.inject.Inject

class PostsFilter(
  private val order: Order,
  private val query: String?
) {

  @Inject
  lateinit var postHideHelper: PostHideHelper

  init {
    Chan.inject(this)
  }

  suspend fun apply(original: List<Post>): List<PostIndexed> {
    val posts: MutableList<Post> = ArrayList(original)
    val originalPostIndexes = calculateOriginalPostIndexes(original)

    if (order != Order.BUMP) {
      processOrder(posts)
    }

    if (!TextUtils.isEmpty(query)) {
      processSearch(posts)
    }

    // Process hidden by filter and post/thread hiding
    val retainedPosts: List<Post> = postHideHelper.filterHiddenPosts(posts)
      .safeUnwrap { error ->
        Logger.e(TAG, "postHideHelper.filterHiddenPosts error", error)
        return emptyList()
      }

    val indexedPosts: MutableList<PostIndexed> = ArrayList(retainedPosts.size)

    for (currentPostIndex in retainedPosts.indices) {
      val retainedPost = retainedPosts[currentPostIndex]
      val realIndex = requireNotNull(originalPostIndexes[retainedPost.no])

      indexedPosts.add(PostIndexed(retainedPost, currentPostIndex, realIndex))
    }

    return indexedPosts
  }

  private fun calculateOriginalPostIndexes(original: List<Post>): Map<Long, Int> {
    if (original.isEmpty()) {
      return emptyMap()
    }

    val originalPostIndexes: MutableMap<Long, Int> = HashMap(original.size)
    for (postIndex in original.indices) {
      val post = original[postIndex]
      originalPostIndexes[post.no] = postIndex
    }

    return originalPostIndexes
  }

  private fun processOrder(posts: List<Post>) {
    when (order) {
      Order.IMAGE -> Collections.sort(posts, IMAGE_COMPARATOR)
      Order.REPLY -> Collections.sort(posts, REPLY_COMPARATOR)
      Order.NEWEST -> Collections.sort(posts, NEWEST_COMPARATOR)
      Order.OLDEST -> Collections.sort(posts, OLDEST_COMPARATOR)
      Order.MODIFIED -> Collections.sort(posts, MODIFIED_COMPARATOR)
      Order.ACTIVITY -> Collections.sort(posts, THREAD_ACTIVITY_COMPARATOR)
      Order.BUMP -> {
        // no-op
      }
    }
  }

  private fun processSearch(posts: MutableList<Post>) {
    val lowerQuery = query?.toLowerCase(Locale.ENGLISH)
      ?: return

    var add: Boolean
    val iterator = posts.iterator()

    while (iterator.hasNext()) {
      val item = iterator.next()
      add = false

      if (item.comment.toString().toLowerCase(Locale.ENGLISH).contains(lowerQuery)) {
        add = true
      } else if (item.subject != null
        && item.subject.toString().toLowerCase(Locale.ENGLISH).contains(lowerQuery)) {
        add = true
      } else if (item.name.toLowerCase(Locale.ENGLISH).contains(lowerQuery)) {
        add = true
      } else if (item.postImagesCount > 0) {
        for (image in item.postImages) {
          if (image.filename != null && image.filename.toLowerCase(Locale.ENGLISH).contains(lowerQuery)) {
            add = true
            break
          }
        }
      }

      if (!add) {
        iterator.remove()
      }
    }
  }

  enum class Order(var orderName: String) {
    BUMP("bump"),
    REPLY("reply"),
    IMAGE("image"),
    NEWEST("newest"),
    OLDEST("oldest"),
    MODIFIED("modified"),
    ACTIVITY("activity");

    companion object {
      fun find(name: String): Order? {
        for (mode in values()) {
          if (mode.orderName == name) {
            return mode
          }
        }

        return null
      }

      @JvmStatic
      fun isNotBumpOrder(orderString: String): Boolean {
        val o = find(orderString)
        return BUMP != o
      }
    }
  }

  companion object {
    private const val TAG = "PostsFilter"

    private val IMAGE_COMPARATOR = Comparator { lhs: Post, rhs: Post -> rhs.threadImagesCount - lhs.threadImagesCount }
    private val REPLY_COMPARATOR = Comparator { lhs: Post, rhs: Post -> rhs.totalRepliesCount - lhs.totalRepliesCount }
    private val NEWEST_COMPARATOR = Comparator { lhs: Post, rhs: Post -> (rhs.time - lhs.time).toInt() }
    private val OLDEST_COMPARATOR = Comparator { lhs: Post, rhs: Post -> (lhs.time - rhs.time).toInt() }
    private val MODIFIED_COMPARATOR = Comparator { lhs: Post, rhs: Post -> (rhs.lastModified - lhs.lastModified).toInt() }

    private val THREAD_ACTIVITY_COMPARATOR = Comparator { lhs: Post, rhs: Post ->
      val currentTimeSeconds = System.currentTimeMillis() / 1000
      // we can't divide by zero, but we can divide by the smallest thing that's closest to 0 instead
      val eps = 0.0001f

      val lhsDivider = if (lhs.totalRepliesCount > 0) {
        lhs.totalRepliesCount.toFloat()
      } else {
        eps
      }

      val rhsDivider = if (rhs.totalRepliesCount > 0) {
        rhs.totalRepliesCount.toFloat()
      } else {
        eps
      }

      val score1 = ((currentTimeSeconds - lhs.time).toFloat() / lhsDivider).toLong()
      val score2 = ((currentTimeSeconds - rhs.time).toFloat() / rhsDivider).toLong()

      return@Comparator score1.compareTo(score2)
    }
  }

}