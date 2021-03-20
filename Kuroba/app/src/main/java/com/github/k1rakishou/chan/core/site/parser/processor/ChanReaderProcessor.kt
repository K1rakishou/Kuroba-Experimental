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
package com.github.k1rakishou.chan.core.site.parser.processor

import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.options.ChanReadOptions
import com.github.k1rakishou.common.removeIfKt
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.util.ChanPostUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChanReaderProcessor(
  private val chanPostRepository: ChanPostRepository,
  private val chanReadOptions: ChanReadOptions,
  override val chanDescriptor: ChanDescriptor
) : IChanReaderProcessor {
  private val toParse = mutableListWithCap<ChanPostBuilder>(64)
  private val postOrderedList = mutableListWithCap<PostDescriptor>(64)
  private var op: ChanPostBuilder? = null

  override val canUseEmptyBoardIfBoardDoesNotExist: Boolean
    get() = false

  private val lock = Mutex()

  override suspend fun setOp(op: ChanPostBuilder?) {
    lock.withLock { this.op = op }
  }

  override suspend fun getOp(): ChanPostBuilder? {
    return lock.withLock { this.op }
  }

  override suspend fun addPost(postBuilder: ChanPostBuilder) {
    lock.withLock {
      if (differsFromCached(postBuilder)) {
        toParse.add(postBuilder)
      }

      postOrderedList.add(postBuilder.postDescriptor)
    }
  }

  override suspend fun applyChanReadOptions() {
    if (chanDescriptor !is ChanDescriptor.ThreadDescriptor) {
      return
    }

    if (chanReadOptions.isDefault()) {
      return
    }

    lock.withLock {
      val postRanges = chanReadOptions.getRetainPostRanges(postOrderedList.size)
      val postDescriptorsToDelete = mutableSetOf<PostDescriptor>()

      Logger.d(TAG, "applyChanReadOptions(chanReadOptions=$chanReadOptions) " +
        "postsCount=${postOrderedList.size}, postRanges=$postRanges")

      for ((index, postDescriptor) in postOrderedList.withIndex()) {
        val anyRangeContainsThisPost = postRanges.any { postRange -> postRange.contains(index) }
        if (anyRangeContainsThisPost) {
          // At least one range contains this post's index, so we need to retain it
          continue
        }

        postDescriptorsToDelete += postDescriptor
      }

      if (postDescriptorsToDelete.isEmpty()) {
        return@withLock
      }

      Logger.d(TAG, "applyChanReadOptions() postDescriptorsToDelete=${postDescriptorsToDelete.size}")

      postOrderedList.removeAll(postDescriptorsToDelete)
      toParse.removeIfKt { postToParse -> postToParse.postDescriptor in postDescriptorsToDelete }
    }
  }

  override suspend fun getToParse(): List<ChanPostBuilder> {
    return lock.withLock { toParse }
  }

  override suspend fun getThreadDescriptors(): List<ChanDescriptor.ThreadDescriptor> {
    return lock.withLock {
      return@withLock toParse
        .map { chanPostBuilder -> chanPostBuilder.postDescriptor.threadDescriptor() }
    }
  }

  override suspend fun getTotalPostsCount(): Int {
    return lock.withLock { postOrderedList.size }
  }

  private fun differsFromCached(builder: ChanPostBuilder): Boolean {
    if (chanDescriptor is ChanDescriptor.CatalogDescriptor) {
      // Always update catalog posts
      return true
    }

    if (builder.op) {
      // Always update original post
      return true
    }

    val chanPost = chanPostRepository.getCachedPost(builder.postDescriptor)
    if (chanPost == null) {
      return true
    }

    val postsDiffer = ChanPostUtils.postsDiffer(
      chanPostBuilder = builder,
      chanPostFromCache = chanPost,
      isThreadMode = chanDescriptor is ChanDescriptor.ThreadDescriptor
    )

    if (postsDiffer) {
      return true
    }

    val cachedPostHash = chanPostRepository.getPostHash(builder.postDescriptor)
    if (cachedPostHash == null) {
      chanPostRepository.putPostHash(builder.postDescriptor, builder.getPostHash)
      return true
    }

    if (builder.getPostHash != cachedPostHash) {
      chanPostRepository.putPostHash(builder.postDescriptor, builder.getPostHash)
      return true
    }

    return false
  }

  override fun toString(): String {
    return "ChanReaderProcessor{chanDescriptor=$chanDescriptor, toParse=${toParse.size}}"
  }

  companion object {
    private const val TAG = "ChanReaderProcessor"
  }
}