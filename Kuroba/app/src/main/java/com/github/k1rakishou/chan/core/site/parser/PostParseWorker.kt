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

import com.github.k1rakishou.chan.core.manager.FilterEngine
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.model.Post
import com.github.k1rakishou.chan.core.model.PostFilter
import com.github.k1rakishou.chan.ui.theme.ChanTheme
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.filter.ChanFilter
import java.util.*

// Called concurrently to parse the post html and the filters on it
// belong to ChanReaderRequest
internal class PostParseWorker(
  private val filterEngine: FilterEngine,
  private val postFilterManager: PostFilterManager,
  private val savedReplyManager: SavedReplyManager,
  private val currentTheme: ChanTheme,
  private val filters: List<ChanFilter>,
  private val postBuilder: Post.Builder,
  private val reader: ChanReader,
  private val internalIds: Set<Long>,
  private val boardDescriptors: Set<BoardDescriptor>
) {

  suspend fun parse(): Post? {
    return Try {
      // needed for "Apply to own posts" to work correctly
      postBuilder.isSavedReply(savedReplyManager.isSaved(postBuilder.postDescriptor))

      // Process the filters before finish, because parsing the html is dependent on filter matches
      processPostFilter(postBuilder)

      val parser = reader.getParser()
        ?: throw NullPointerException("PostParser cannot be null!")

      return@Try parser.parse(currentTheme, postBuilder, object : PostParser.Callback {
        override fun isSaved(postNo: Long, postSubNo: Long): Boolean {
          return savedReplyManager.isSaved(postBuilder.postDescriptor.descriptor, postNo, postSubNo)
        }

        override fun isInternal(postNo: Long): Boolean {
          return internalIds.contains(postNo)
        }

        override fun isValidBoard(boardDescriptor: BoardDescriptor): Boolean {
          return boardDescriptors.contains(boardDescriptor)
        }
      })
    }.mapErrorToValue { error ->
      Logger.e(TAG, "Error parsing post ${postBuilderToString(postBuilder)}", error)
      return@mapErrorToValue null
    }
  }

  @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
  private fun processPostFilter(post: Post.Builder) {
    val postDescriptor = post.postDescriptor

    if (postFilterManager.contains(postDescriptor)) {
      // Fast path. We have already processed this post so we don't want to do that again. This
      // should make filter processing way faster after the initial processing but it's kinda
      // dangerous in case a post is updated on the server which "shouldn't" happen normally. It
      // can happen when a poster is getting banned with a message and we can't handle that for now,
      // because 4chan as well as other sites do not provide "last_modified" parameter for posts.
      // There is a workaround for that - to compare post that we got from the server with the one
      // in the database and if they differ update the "last_modified" but it will make everything
      // slower. Maybe it's doable by calculating a post hash and store it in the memory cache and
      // in the database too.
      return
    }

    for (filter in filters) {
      if (filterEngine.matches(filter, post)) {
        postFilterManager.insert(postDescriptor, createPostFilter(filter))
        return
      }

      postFilterManager.remove(postDescriptor)
    }
  }

  @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
  private fun createPostFilter(filter: ChanFilter): PostFilter {
    return when (FilterEngine.FilterAction.forId(filter.action)) {
      FilterEngine.FilterAction.COLOR -> {
        PostFilter(
          enabled = filter.enabled,
          filterHighlightedColor = filter.color,
          filterReplies = filter.applyToReplies,
          filterOnlyOP = filter.onlyOnOP,
          filterSaved = filter.applyToSaved
        )
      }
      FilterEngine.FilterAction.HIDE -> {
        PostFilter(
          enabled = filter.enabled,
          filterStub = true,
          filterReplies = filter.applyToReplies,
          filterOnlyOP = filter.onlyOnOP
        )
      }
      FilterEngine.FilterAction.REMOVE -> {
        PostFilter(
          enabled = filter.enabled,
          filterRemove = true,
          filterReplies = filter.applyToReplies,
          filterOnlyOP = filter.onlyOnOP
        )
      }
      FilterEngine.FilterAction.WATCH -> {
        PostFilter(
          enabled = filter.enabled,
          filterWatch = true,
          filterOnlyOP = true
        )
      }
    }
  }

  private fun postBuilderToString(postBuilder: Post.Builder): String {
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