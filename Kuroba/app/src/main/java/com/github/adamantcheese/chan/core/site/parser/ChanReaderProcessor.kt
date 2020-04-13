/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
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
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.repository.ChanPostRepository

class ChanReaderProcessor(
        private val chanPostRepository: ChanPostRepository,
        val loadable: Loadable
) {
    private val toParse = ArrayList<Post.Builder>()
    private val toUpdateInRepository = ArrayList<Post.Builder>()
    private val postNoOrderedList = mutableListOf<Long>()

    var op: Post.Builder? = null

    fun containsPostNo(postNo: Long): ModularResult<Boolean> {
        return chanPostRepository.containsPostBlocking(
                loadable.getPostDescriptor(postNo)
        )
    }

    fun addForParse(postBuilder: Post.Builder) {
        toParse.add(postBuilder)
        addPostNoOrdered(postBuilder.id)
    }

    fun addForUpdateInDatabase(postBuilder: Post.Builder) {
        toUpdateInRepository.add(postBuilder)
        addPostNoOrdered(postBuilder.id)
    }

    private fun addPostNoOrdered(postNo: Long) {
        postNoOrderedList.add(postNo)
    }

    fun getToParse(): List<Post.Builder> {
        return toParse
    }

    fun getToUpdateInRepository(): List<Post.Builder> {
        return toUpdateInRepository
    }

    fun getPostsSortedByIndexes(posts: List<Post>): List<Post> {
        return postNoOrderedList.mapNotNull { postNo ->
            return@mapNotNull posts.firstOrNull { post -> post.no == postNo }
        }
    }

    fun getThreadIdsOrdered(): List<Long> {
        check(loadable.isCatalogMode) { "Cannot load threads in thread mode!" }

        return postNoOrderedList
    }
}