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

import android.util.JsonReader
import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.core.database.DatabaseManager
import com.github.adamantcheese.chan.core.database.DatabaseSavedReplyManager
import com.github.adamantcheese.chan.core.manager.FilterEngine
import com.github.adamantcheese.chan.core.mapper.ChanPostUnparsedMapper
import com.github.adamantcheese.chan.core.mapper.ChanPostUnparsedMapper.fromPostBuilder
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Filter
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.core.net.JsonReaderRequest
import com.github.adamantcheese.chan.core.site.loader.ChanLoaderRequestParams
import com.github.adamantcheese.chan.core.site.loader.ChanLoaderResponse
import com.github.adamantcheese.chan.utils.DescriptorUtils.getDescriptor
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor.Companion.create
import com.github.adamantcheese.model.data.post.ChanPostUnparsed
import com.github.adamantcheese.model.repository.ChanPostRepository
import okhttp3.HttpUrl
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.collections.ArrayList
import kotlin.system.measureTimeMillis
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

/**
 * Process a typical imageboard json response.<br></br>
 * This class is highly multithreaded, take good care to not access models that are to be only
 * changed on the main thread.
 */
class ChanReaderRequest(
        request: ChanLoaderRequestParams
) : JsonReaderRequest<ChanLoaderResponse>(getChanUrl(request.loadable).toString(), request.listener, request.errorListener) {

    @Inject
    lateinit var databaseManager: DatabaseManager

    @Inject
    lateinit var filterEngine: FilterEngine

    @Inject
    lateinit var chanPostRepository: ChanPostRepository

    val loadable: Loadable

    private val cached: MutableList<Post>
    private val reader: ChanReader
    private val databaseSavedReplyManager: DatabaseSavedReplyManager
    private val filters: MutableList<Filter>

    override fun getPriority(): Priority {
        return Priority.HIGH
    }

    init {
        Chan.inject(this)

        // Copy the loadable and cached list. The cached array may changed/cleared by other threads.
        loadable = request.loadable.clone()
        cached = request.cached
        reader = request.chanReader
        filters = ArrayList()

        val enabledFilters = filterEngine.enabledFilters
        for (filter in enabledFilters) {
            if (filterEngine.matchesBoard(filter, loadable.board)) {
                // copy the filter because it will get used on other threads
                filters.add(filter.clone())
            }
        }

        databaseSavedReplyManager = databaseManager.databaseSavedReplyManager
    }

    @OptIn(ExperimentalTime::class)
    @Throws(Exception::class)
    override fun readJson(reader: JsonReader): ChanLoaderResponse {
        val chanReaderProcessor = ChanReaderProcessor(chanPostRepository, loadable)

        when {
            loadable.isThreadMode -> this.reader.loadThread(reader, chanReaderProcessor)
            loadable.isCatalogMode -> this.reader.loadCatalog(reader, chanReaderProcessor)
            else -> throw IllegalArgumentException("Unknown mode")
        }

        storeNewPostsInRepository(chanReaderProcessor.getToParse())
        updateOldPostsInRepository(chanReaderProcessor.getToUpdateInRepository())

        val list = reloadPostsFromDatabase(
                chanReaderProcessor,
                getDescriptor(chanReaderProcessor.loadable)
        )

        val op = checkNotNull(chanReaderProcessor.op) { "OP is null" }
        return processPosts(op, list)
    }

    @OptIn(ExperimentalTime::class)
    private fun updateOldPostsInRepository(toUpdate: List<Post.Builder>) {
        if (toUpdate.isEmpty()) {
            return
        }

        val unparsedPosts: MutableList<ChanPostUnparsed> = ArrayList(toUpdate.size)
        for (postBuilder in toUpdate) {
            val postDescriptor = create(
                    postBuilder.board.site.name(),
                    postBuilder.board.code,
                    postBuilder.getOpId(),
                    postBuilder.id
            )

            unparsedPosts.add(
                    fromPostBuilder(postDescriptor, postBuilder)
            )
        }

        val timedValue = measureTimedValue {
            chanPostRepository.insertOrUpdateManyBlocking(unparsedPosts).unwrap()
        }

        val timeMs = timedValue.duration.inMilliseconds
        val updatedPostsCount = timedValue.value

        Logger.d(TAG, "updateOldPostsInRepository took ${timeMs}ms, " +
                "updated ${updatedPostsCount} posts")
    }

    private fun storeNewPostsInRepository(toParse: List<Post.Builder>) {
        if (toParse.isEmpty()) {
            return
        }

        val unparsedPosts: MutableList<ChanPostUnparsed> = ArrayList(toParse.size)
        for (postBuilder in toParse) {
            val postDescriptor = create(
                    postBuilder.board.site.name(),
                    postBuilder.board.code,
                    postBuilder.getOpId(),
                    postBuilder.id
            )

            unparsedPosts.add(
                    fromPostBuilder(postDescriptor, postBuilder)
            )
        }

        val timeMs = measureTimeMillis {
            chanPostRepository.insertOrUpdateManyBlocking(unparsedPosts).unwrap()
        }

        Logger.d(TAG, "storeNewPostsInRepository took ${timeMs}ms, " +
                "inserted ${unparsedPosts.size} new posts")
    }

    private fun reloadPostsFromDatabase(
            chanReaderProcessor: ChanReaderProcessor,
            chanDescriptor: ChanDescriptor
    ): List<Post> {
        val toParse = when (chanDescriptor) {
            is ChanDescriptor.ThreadDescriptor -> {
                chanPostRepository.getThreadPostsBlocking(chanDescriptor).unwrap()
            }
            is ChanDescriptor.CatalogDescriptor -> {
                chanPostRepository.getCatalogOriginalPostsBlocking(
                        chanDescriptor,
                        chanReaderProcessor.getThreadIdsOrdered()
                ).unwrap()
            }
        }.map { unparsedPost -> ChanPostUnparsedMapper.toPostBuilder(loadable.board, unparsedPost) }

        val parsedPosts = parsePosts(toParse, chanDescriptor)
        if (parsedPosts.isEmpty()) {
            return emptyList()
        }

        return chanReaderProcessor.getPostsSortedByIndexes(parsedPosts)
    }

    private fun parsePosts(toParse: List<Post.Builder>, chanDescriptor: ChanDescriptor): List<Post> {
        return toParse
                .map { postToParse ->
                    return@map PostParseCallable(
                            filterEngine,
                            filters,
                            databaseSavedReplyManager,
                            chanPostRepository,
                            postToParse,
                            reader,
                            chanDescriptor
                    )
                }
                .chunked(Int.MAX_VALUE)
                .map { postParseCallableList -> EXECUTOR.invokeAll(postParseCallableList) }
                .flatMap { futureList -> futureList.mapNotNull { future -> future.get() } }
    }

    private fun processPosts(op: Post.Builder, allPosts: List<Post>): ChanLoaderResponse {
        val response = ChanLoaderResponse(op, ArrayList(allPosts.size))
        val cachedPosts = ArrayList<Post>()
        val newPosts = ArrayList<Post>()

        if (cached.size > 0) {
            // Add all posts that were parsed before
            cachedPosts.addAll(cached)
            val cachedPostsByNo: MutableMap<Long, Post> = HashMap()

            for (post in cachedPosts) {
                cachedPostsByNo[post.no] = post
            }

            val serverPostsByNo: MutableMap<Long, Post> = HashMap()
            for (post in allPosts) {
                serverPostsByNo[post.no] = post
            }

            // If there's a cached post but it's not in the list received from the server,
            // mark it as deleted
            if (loadable.isThreadMode) {
                for (cachedPost in cachedPosts) {
                    cachedPost.deleted.set(!serverPostsByNo.containsKey(cachedPost.no))
                }
            }

            // If there's a post in the list from the server, that's not in the cached list, add it.
            for (serverPost in allPosts) {
                if (!cachedPostsByNo.containsKey(serverPost.no)) {
                    newPosts.add(serverPost)
                }
            }
        } else {
            newPosts.addAll(allPosts)
        }

        val totalPosts = ArrayList<Post>(cachedPosts.size + newPosts.size)
        totalPosts.addAll(cachedPosts)
        totalPosts.addAll(newPosts)

        if (loadable.isThreadMode) {
            val postsByNo: MutableMap<Long, Post> = HashMap()
            for (post in totalPosts) {
                postsByNo[post.no] = post
            }

            // Maps post no's to a list of no's that that post received replies from
            val replies: MutableMap<Long, MutableList<Long>> = HashMap()
            for (sourcePost in totalPosts) {
                for (replyTo in sourcePost.repliesTo) {
                    var value = replies[replyTo]
                    if (value == null) {
                        value = ArrayList(3)
                        replies[replyTo] = value
                    }
                    value.add(sourcePost.no)
                }
            }

            for ((key, value) in replies) {
                val subject = postsByNo[key]
                // Sometimes a post replies to a ghost, a post that doesn't exist.
                if (subject != null) {
                    subject.repliesFrom = value
                }
            }
        }

        response.posts.addAll(totalPosts)
        return response
    }

    companion object {
        private const val TAG = "ChanReaderRequest"
        private const val threadFactoryName = "post_parser_thread_%d"

        private var THREAD_COUNT = 0
        private var EXECUTOR: ExecutorService
        private val threadIndex = AtomicInteger(0)

        init {
            THREAD_COUNT = Runtime.getRuntime().availableProcessors()
            Logger.d(TAG, "Thread count: $THREAD_COUNT")
            EXECUTOR = Executors.newFixedThreadPool(THREAD_COUNT) { runnable ->
                val threadName = String.format(
                        Locale.ENGLISH,
                        threadFactoryName,
                        threadIndex.getAndIncrement()
                )

                return@newFixedThreadPool Thread(runnable, threadName)
            }
        }

        private fun getChanUrl(loadable: Loadable): HttpUrl {
            if (loadable.site == null) {
                throw NullPointerException("Loadable.site == null")
            }
            if (loadable.board == null) {
                throw NullPointerException("Loadable.board == null")
            }

            return when {
                loadable.isThreadMode -> loadable.site.endpoints().thread(loadable.board, loadable)
                loadable.isCatalogMode -> loadable.site.endpoints().catalog(loadable.board)
                else -> throw IllegalArgumentException("Unknown mode")
            }
        }
    }

}