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

class ChanReaderProcessor(
  private val chanPostRepository: ChanPostRepository,
  val chanDescriptor: ChanDescriptor
) {
  private val toParse = ArrayList<Post.Builder>()
  private val postNoOrderedList = mutableListWithCap<Long>(64)

  var op: Post.Builder? = null

  fun getThreadCap(): Int {
    var maxCount = op?.stickyCap ?: Int.MAX_VALUE
    if (maxCount < 0) {
      maxCount = Int.MAX_VALUE
    }

    return maxCount
  }

  suspend fun addPost(postBuilder: Post.Builder) {
    if (differsFromCached(postBuilder)) {
      addForParse(postBuilder)
    }

    postNoOrderedList.add(postBuilder.id)
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
      ?: return true

    return PostUtils.postsDiffer(builder, chanPost)
  }


  private fun addForParse(postBuilder: Post.Builder) {
    toParse.add(postBuilder)
  }

  fun getToParse(): List<Post.Builder> {
    return toParse
  }

  fun getPostsSortedByIndexes(posts: List<Post>): List<Post> {
    return postNoOrderedList.mapNotNull { postNo ->
      return@mapNotNull posts.firstOrNull { post -> post.no == postNo }
    }
  }

  fun getPostNoListOrdered(): List<Long> {
    return postNoOrderedList
  }
}