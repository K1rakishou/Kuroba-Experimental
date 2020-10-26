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
package com.github.k1rakishou.chan.core.model

import com.github.k1rakishou.chan.core.manager.PostPreloadedInfoHolder
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor

class ChanThread(
  val chanDescriptor: ChanDescriptor,
  newPosts: List<Post>
) {
  private val threadPosts: MutableList<Post> = ArrayList(128)

  init {
    threadPosts.clear()
    threadPosts.addAll(newPosts)
  }

  @get:Synchronized
  val postsCount: Int
    get() = threadPosts.size

  @get:Synchronized
  val imagesCount: Int
    get() = threadPosts.sumBy { post -> post.postImagesCount }

  /**
   * Not safe! Only use for read-only operations!
   */
  @get:Synchronized
  val op: Post
    get() = threadPosts[0]

  @get:Synchronized
  @set:Synchronized
  var postPreloadedInfoHolder: PostPreloadedInfoHolder? = null

  @get:Synchronized
  @set:Synchronized
  var isClosed = false

  @get:Synchronized
  @set:Synchronized
  var isDeleted = false

  @get:Synchronized
  @set:Synchronized
  var isArchived = false

  @Synchronized
  fun canUpdateThread(): Boolean {
    return !isClosed && !isDeleted && !isArchived
  }

  @Synchronized
  fun setNewPosts(newPosts: List<Post>) {
    threadPosts.clear()
    threadPosts.addAll(newPosts)
  }

  @Synchronized
  fun getPosts(): List<Post> {
    return threadPosts
  }

  @Synchronized
  fun clearPosts() {
    threadPosts.clear()
  }

  @Synchronized
  fun deletePost(postDescriptor: PostDescriptor) {
    val index = threadPosts.indexOfFirst { post -> post.postDescriptor == postDescriptor }
    if (index >= 0) {
      threadPosts.removeAt(index)
    }
  }

  @Synchronized
  fun iteratePostsAround(postDescriptor: PostDescriptor, count: Int, iterator: (Post) -> Unit) {
    require(count > 0) { "Bad count: $count" }

    val indexOfPost = threadPosts.indexOfFirst { post -> post.postDescriptor == postDescriptor }
    if (indexOfPost < 0) {
      return
    }

    val from = (indexOfPost - count).coerceIn(0, threadPosts.size)
    val to = (indexOfPost + count).coerceIn(0, threadPosts.size)

    for (index in from until to) {
      threadPosts.getOrNull(index)?.let { post -> iterator(post) }
    }
  }

  @Synchronized
  fun iteratePostImages(postDescriptor: PostDescriptor, iterator: (PostImage) -> Unit): Boolean {
    val post = threadPosts.firstOrNull { post -> post.postDescriptor == postDescriptor }
      ?: return false

    post.iteratePostImages { postImage -> iterator(postImage) }
    return true
  }

  override fun toString(): String {
    return "ChanThread{" +
      "chanDescriptor=" + chanDescriptor +
      ", closed=" + isClosed +
      ", archived=" + isArchived +
      ", deleted=" + isDeleted +
      '}'
  }

}