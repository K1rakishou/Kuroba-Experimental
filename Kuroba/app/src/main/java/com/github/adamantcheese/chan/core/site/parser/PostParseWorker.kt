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

import com.github.adamantcheese.chan.core.manager.FilterEngine
import com.github.adamantcheese.chan.core.manager.PostFilterManager
import com.github.adamantcheese.chan.core.manager.SavedReplyManager
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.PostFilter
import com.github.adamantcheese.chan.core.model.orm.Filter
import com.github.adamantcheese.chan.ui.theme.Theme
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.ModularResult.Companion.Try
import java.util.*

// Called concurrently to parse the post html and the filters on it
// belong to ChanReaderRequest
internal class PostParseWorker(
  private val filterEngine: FilterEngine,
  private val postFilterManager: PostFilterManager,
  private val savedReplyManager: SavedReplyManager,
  private val currentTheme: Theme,
  private val filters: List<Filter>,
  private val postBuilder: Post.Builder,
  private val reader: ChanReader,
  private val internalIds: Set<Long>
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
      })
    }.mapErrorToValue { error ->
      Logger.e(TAG, "Error parsing post ${postBuilderToString(postBuilder)}", error)
      return@mapErrorToValue null
    }
  }

  @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
  private fun processPostFilter(post: Post.Builder) {
    for (filter in filters) {
      val postDescriptor = post.postDescriptor

      // TODO(KurobaEx): do not match filters against posts that we already have in postFilterManager.
      //  Clear that map when the user adds/updates/removes a filter.
      if (filterEngine.matches(filter, post)) {
        postFilterManager.insert(postDescriptor, createPostFilter(filter))
        return
      }

      postFilterManager.remove(postDescriptor)
    }
  }

  @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
  private fun createPostFilter(filter: Filter): PostFilter {
    return when (FilterEngine.FilterAction.forId(filter.action)) {
      FilterEngine.FilterAction.COLOR -> {
        PostFilter(
          filterHighlightedColor = filter.color,
          filterReplies = filter.applyToReplies,
          filterOnlyOP = filter.onlyOnOP,
          filterSaved = filter.applyToSaved
        )
      }
      FilterEngine.FilterAction.HIDE -> {
        PostFilter(
          filterHighlightedColor = 0,
          filterStub = true,
          filterReplies = filter.applyToReplies,
          filterOnlyOP = filter.onlyOnOP
        )
      }
      FilterEngine.FilterAction.REMOVE -> {
        PostFilter(
          filterHighlightedColor = 0,
          filterRemove = true,
          filterReplies = filter.applyToReplies,
          filterOnlyOP = filter.onlyOnOP
        )
      }
      FilterEngine.FilterAction.WATCH -> {
        PostFilter(
          filterHighlightedColor = 0,
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