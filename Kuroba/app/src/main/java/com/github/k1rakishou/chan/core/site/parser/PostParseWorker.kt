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

import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import java.util.*

// Called concurrently to parse the post html and the filters on it
// belong to ChanReaderRequest
internal class PostParseWorker(
  private val postBuilder: ChanPostBuilder,
  private val postParser: PostParser,
  private val internalIds: Set<Long>,
  private val savedPosts: Set<PostDescriptor>,
  private val hiddenOrRemovedPosts: Map<PostDescriptor, Int>,
  private val isParsingCatalog: Boolean
) {

  suspend fun parse(): ChanPost? {
    return Try {
      return@Try postParser.parseFull(postBuilder, object : PostParser.Callback {

        override fun isSaved(threadNo: Long, postNo: Long, postSubNo: Long): Boolean {
          if (threadNo <= 0 || postNo <= 0) {
            return false
          }

          val postDescriptor = PostDescriptor.create(
            chanDescriptor = postBuilder.postDescriptor.descriptor,
            threadNo = threadNo,
            postNo = postNo,
            postSubNo = postSubNo
          )

          return savedPosts.contains(postDescriptor)
        }

        override fun isHiddenOrRemoved(threadNo: Long, postNo: Long, postSubNo: Long): Int {
          if (threadNo <= 0 || postNo <= 0) {
            return PostParser.NORMAL_POST
          }

          val postDescriptor = PostDescriptor.create(
            chanDescriptor = postBuilder.postDescriptor.descriptor,
            threadNo = threadNo,
            postNo = postNo,
            postSubNo = postSubNo
          )

          return hiddenOrRemovedPosts[postDescriptor] ?: PostParser.NORMAL_POST
        }

        override fun isInternal(postNo: Long): Boolean {
          return internalIds.contains(postNo)
        }

        override fun isParsingCatalogPosts(): Boolean {
          return isParsingCatalog
        }

      })
    }.mapErrorToValue { error ->
      Logger.e(TAG, "Error parsing post ${postBuilderToString(postBuilder)}", error)
      return@mapErrorToValue null
    }
  }

  private fun postBuilderToString(postBuilder: ChanPostBuilder): String {
    return String.format(
      Locale.ENGLISH,
      "{postNo=%d, comment=%s}",
      postBuilder.id,
      postBuilder.postCommentBuilder.getComment()
    )
  }

  companion object {
    private const val TAG = "PostParseWorker"
  }

}