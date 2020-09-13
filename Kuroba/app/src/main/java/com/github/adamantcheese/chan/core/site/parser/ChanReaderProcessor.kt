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
package com.github.adamantcheese.chan.core.site.parser

import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.utils.PostUtils
import com.github.adamantcheese.common.mutableListWithCap
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.repository.ChanPostRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChanReaderProcessor(
  private val chanPostRepository: ChanPostRepository,
  val chanDescriptor: ChanDescriptor
) {
  private val toParse = mutableListWithCap<Post.Builder>(64)
  private val postNoOrderedList = mutableListWithCap<Long>(64)
  private var op: Post.Builder? = null

  private val lock = Mutex()

  suspend fun setOp(op: Post.Builder?) {
    lock.withLock { this.op = op }
  }

  suspend fun getOp(): Post.Builder? {
    return lock.withLock { this.op }
  }

  suspend fun getThreadCap(): Int {
    return lock.withLock {
      var maxCount = op?.stickyCap ?: Int.MAX_VALUE
      if (maxCount < 0) {
        maxCount = Int.MAX_VALUE
      }

      return@withLock maxCount
    }
  }

  suspend fun addPost(postBuilder: Post.Builder) {
    lock.withLock {
      if (differsFromCached(postBuilder)) {
        addForParse(postBuilder)
      }

      postNoOrderedList.add(postBuilder.id)
    }
  }

  suspend fun getToParse(): List<Post.Builder> {
    return lock.withLock { toParse }
  }

  suspend fun getPostsSortedByIndexes(posts: List<Post>): List<Post> {
    return lock.withLock {
      return@withLock postNoOrderedList.mapNotNull { postNo ->
        return@mapNotNull posts.firstOrNull { post -> post.no == postNo }
      }
    }
  }

  suspend fun getPostNoListOrdered(): List<Long> {
    return lock.withLock { postNoOrderedList }
  }

  suspend fun getTotalPostsCount(): Int {
    return lock.withLock { postNoOrderedList.size }
  }

  private suspend fun differsFromCached(builder: Post.Builder): Boolean {
    val postDescriptor = if (builder.op) {
      PostDescriptor.create(
        builder.boardDescriptor!!.siteName(),
        builder.boardDescriptor!!.boardCode,
        builder.id
      )
    } else {
      PostDescriptor.create(
        builder.boardDescriptor!!.siteName(),
        builder.boardDescriptor!!.boardCode,
        builder.opId,
        builder.id
      )
    }

    val chanPost = chanPostRepository.getCachedPost(postDescriptor, builder.op)
    if (chanPost == null) {
      chanPostRepository.putPostHash(builder.postDescriptor, builder.getPostHash)
      return true
    }

    if (PostUtils.postsDiffer(builder, chanPost)) {
      return true
    }

    val cachedPostHash = chanPostRepository.getPostHash(builder.postDescriptor)
    if (cachedPostHash == null) {
      return true
    }

    if (builder.getPostHash != cachedPostHash) {
      chanPostRepository.putPostHash(builder.postDescriptor, builder.getPostHash)
      return true
    }

    return false
  }


  private fun addForParse(postBuilder: Post.Builder) {
    toParse.add(postBuilder)
  }
}