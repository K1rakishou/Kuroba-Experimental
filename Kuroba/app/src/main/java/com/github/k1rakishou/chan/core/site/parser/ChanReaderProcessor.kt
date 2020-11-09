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
package com.github.k1rakishou.chan.core.site.parser

import com.github.k1rakishou.chan.core.model.ChanPostBuilder
import com.github.k1rakishou.chan.utils.PostUtils
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.repository.ChanPostRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChanReaderProcessor(
  private val chanPostRepository: ChanPostRepository,
  val chanDescriptor: ChanDescriptor
) {
  private val toParse = mutableListWithCap<ChanPostBuilder>(64)
  private val postNoOrderedList = mutableListWithCap<Long>(64)
  private var op: ChanPostBuilder? = null

  private val lock = Mutex()

  suspend fun setOp(op: ChanPostBuilder?) {
    lock.withLock { this.op = op }
  }

  suspend fun getOp(): ChanPostBuilder? {
    return lock.withLock { this.op }
  }

  suspend fun getThreadCap(): Int? {
    return lock.withLock { op?.stickyCap }
  }

  suspend fun addPost(postBuilder: ChanPostBuilder) {
    lock.withLock {
      if (differsFromCached(postBuilder)) {
        addForParse(postBuilder)
      }

      postNoOrderedList.add(postBuilder.id)
    }
  }

  suspend fun getToParse(): List<ChanPostBuilder> {
    return lock.withLock { toParse }
  }

  suspend fun getPostsSortedByIndexes(posts: List<ChanPost>): List<ChanPost> {
    return lock.withLock {
      return@withLock postNoOrderedList.mapNotNull { postNo ->
        return@mapNotNull posts.firstOrNull { post -> post.postNo() == postNo }
      }
    }
  }

  suspend fun getPostNoListOrdered(): List<Long> {
    return lock.withLock { postNoOrderedList }
  }

  suspend fun getTotalPostsCount(): Int {
    return lock.withLock { postNoOrderedList.size }
  }

  private fun differsFromCached(builder: ChanPostBuilder): Boolean {
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

    check(postDescriptor.isOP() == builder.op) {
      "isOP flags differ for ($postDescriptor) and ${builder}, " +
        "postDescriptor.isOP: ${postDescriptor.isOP()}, builder.op: ${builder.op}"
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
      ?: return true

    if (builder.getPostHash != cachedPostHash) {
      chanPostRepository.putPostHash(builder.postDescriptor, builder.getPostHash)
      return true
    }

    return false
  }

  private fun addForParse(postBuilder: ChanPostBuilder) {
    toParse.add(postBuilder)
  }
}