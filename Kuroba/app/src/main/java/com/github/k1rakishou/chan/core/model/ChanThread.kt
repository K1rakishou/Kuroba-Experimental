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
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor

class ChanThread(
  val chanDescriptor: ChanDescriptor,
  newPosts: List<Post>
) {
  private val threadPosts = ArrayList<Post>(128)
  private val postsByPostDescriptorMap = mutableMapWithCap<PostDescriptor, Post>(128)

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

  init {
    setNewPosts(newPosts)
  }

  @Synchronized
  fun setNewPosts(newPosts: List<Post>) {
    threadPosts.clear()
    postsByPostDescriptorMap.clear()

    threadPosts.addAll(newPosts)

    newPosts.forEach { post ->
      postsByPostDescriptorMap[post.postDescriptor] = post
    }
  }

  @Synchronized
  fun getPosts(): List<Post> {
    return threadPosts
  }

  @Synchronized
  fun clearPosts() {
    threadPosts.clear()
    postsByPostDescriptorMap.clear()
  }

  @Synchronized
  fun deletePost(postDescriptor: PostDescriptor) {
    val index = threadPosts.indexOfFirst { post -> post.postDescriptor == postDescriptor }
    if (index >= 0) {
      threadPosts.removeAt(index)
    }

    postsByPostDescriptorMap.remove(postDescriptor)
  }

  @Synchronized
  fun mapPostsAround(postDescriptor: PostDescriptor, leftCount: Int, rightCount: Int): List<PostDescriptor> {
    if (leftCount == 0 && rightCount == 0) {
      return emptyList()
    }

    check(leftCount >= 0) { "Bad left count: $leftCount" }
    check(rightCount >= 0) { "Bad right count: $rightCount" }

    val indexOfPost = threadPosts.indexOfFirst { post -> post.postDescriptor == postDescriptor }
    if (indexOfPost < 0) {
      return emptyList()
    }

    val from = (indexOfPost - leftCount).coerceIn(0, threadPosts.size)
    val to = (indexOfPost + rightCount).coerceIn(0, threadPosts.size)

    val postDescriptors = mutableListWithCap<PostDescriptor>(to - from)

    for (index in from until to) {
      threadPosts.getOrNull(index)?.let { post -> postDescriptors += post.postDescriptor }
    }

    return postDescriptors
  }

  @Synchronized
  fun getPostDescriptorRelativeTo(postDescriptor: PostDescriptor, offset: Int): PostDescriptor? {
    val currentPostIndex = threadPosts.indexOfFirst { post -> post.postDescriptor == postDescriptor }
    if (currentPostIndex < 0) {
      return null
    }

    val postIndex = (currentPostIndex + offset).coerceIn(0, threadPosts.size)
    return threadPosts.getOrNull(postIndex)?.postDescriptor
  }

  @Synchronized
  fun iteratePostImages(postDescriptor: PostDescriptor, iterator: (PostImage) -> Unit): Boolean {
    val post = postsByPostDescriptorMap[postDescriptor]
      ?: return false

    post.iteratePostImages { postImage -> iterator(postImage) }
    return true
  }

  @Synchronized
  fun postHasImages(postDescriptor: PostDescriptor): Boolean {
    return postsByPostDescriptorMap[postDescriptor]?.postImages?.isNotEmpty() ?: false
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