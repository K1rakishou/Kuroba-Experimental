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
import com.github.adamantcheese.chan.core.database.DatabaseManager
import com.github.adamantcheese.chan.core.database.DatabaseSavedReplyManager
import com.github.adamantcheese.chan.core.manager.ArchivesManager
import com.github.adamantcheese.chan.core.manager.FilterEngine
import com.github.adamantcheese.chan.core.mapper.ChanPostUnparsedMapper
import com.github.adamantcheese.chan.core.mapper.ChanPostUnparsedMapper.fromPostBuilder
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Filter
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.core.net.JsonReaderRequest
import com.github.adamantcheese.chan.core.site.loader.ChanLoaderRequestParams
import com.github.adamantcheese.chan.core.site.loader.ChanLoaderResponse
import com.github.adamantcheese.chan.utils.DescriptorUtils
import com.github.adamantcheese.chan.utils.DescriptorUtils.getDescriptor
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.AppConstants
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor.Companion.create
import com.github.adamantcheese.model.data.post.ChanPostUnparsed
import com.github.adamantcheese.model.repository.ArchivesRepository
import com.github.adamantcheese.model.repository.ChanPostRepository
import okhttp3.HttpUrl
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayList
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

/**
 * Process a typical imageboard json response.<br></br>
 * This class is highly multithreaded, take good care to not access models that are to be only
 * changed on the main thread.
 */
class ChanReaderRequest(
        private val databaseManager: DatabaseManager,
        private val filterEngine: FilterEngine,
        private val chanPostRepository: ChanPostRepository,
        private val appConstants: AppConstants,
        private val archivesManager: ArchivesManager,
        private val archivesRepository: ArchivesRepository,
        request: ChanLoaderRequestParams
) : JsonReaderRequest<ChanLoaderResponse>(getChanUrl(request.loadable).toString(), request.listener, request.errorListener) {
    val loadable = request.loadable.clone()

    private val cachedPostsMap: MutableMap<Long, Post>
    private val reader: ChanReader
    private val databaseSavedReplyManager: DatabaseSavedReplyManager
    private val filters: MutableList<Filter>

    override fun getPriority(): Priority {
        return Priority.HIGH
    }

    init {
        cachedPostsMap = request.cached.associateBy { post -> post.no }.toMutableMap()
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

        val isCatalog = when {
            loadable.isThreadMode -> false
            loadable.isCatalogMode -> true
            else -> throw IllegalArgumentException("Unknown mode")
        }

        if (!isCatalog) {
            // TODO(archives):

            val threadDescriptor = DescriptorUtils.getThreadDescriptorOrThrow(loadable)
            val archiveDescriptor = archivesManager.getArchiveDescriptor(threadDescriptor)

            if (archiveDescriptor != null) {
                val threadArchiveRequestLink = archivesManager.getRequestLinkForThread(
                        threadDescriptor,
                        archiveDescriptor
                )

                if (threadArchiveRequestLink != null) {
                    val supportsFiles = archivesManager.doesArchiveSupportsFilesForBoard(
                            archiveDescriptor,
                            loadable.boardDescriptor
                    )

                    val archivePosts = archivesRepository.fetchThreadFromNetworkBlocking(
                            threadArchiveRequestLink,
                            threadDescriptor.opNo,
                            supportsFiles
                    ).unwrap()
                }
            }
        }

        val (storedPostNoList, storeDuration) = measureTimedValue {
            storeNewPostsInRepository(
                    chanReaderProcessor.getToParse(),
                    isCatalog
            )
        }

        val (updatedPostNoList, updateDuration) = measureTimedValue {
            updateOldPostsInRepository(
                    chanReaderProcessor.getToUpdateInRepository(),
                    isCatalog
            )
        }

        val updatedPosts = hashSetOf<Long>().apply {
            addAll(storedPostNoList)
            addAll(updatedPostNoList)
        }

        val (reloadedPosts, reloadingDuration, parsingDuration) = reloadPostsFromRepository(
                updatedPosts,
                chanReaderProcessor,
                getDescriptor(chanReaderProcessor.loadable)
        )

        val cachedPostsCount = chanPostRepository.getCachedValuesCount()

        Logger.d(TAG, "store new posts took $storeDuration (stored ${storedPostNoList.size} posts), " +
                "update posts took $updateDuration (updated ${updatedPostNoList.size} posts), " +
                "reload posts took $reloadingDuration, parse posts took = $parsingDuration " +
                "(reloaded ${reloadedPosts.size} posts), " +
                "cachedPostsCount = ($cachedPostsCount/${appConstants.maxPostsCountInPostsCache})")

        val op = checkNotNull(chanReaderProcessor.op) { "OP is null" }
        return processPosts(op, reloadedPosts)
    }

    @OptIn(ExperimentalTime::class)
    private fun updateOldPostsInRepository(
            toUpdate: List<Post.Builder>,
            isCatalog: Boolean
    ): List<Long> {
        if (toUpdate.isEmpty()) {
            return emptyList()
        }

        val unparsedPosts: MutableList<ChanPostUnparsed> = ArrayList(toUpdate.size)
        for (postBuilder in toUpdate) {
            val postDescriptor = create(
                    postBuilder.board.site.name(),
                    postBuilder.board.code,
                    postBuilder.getOpId(),
                    postBuilder.id
            )

            unparsedPosts.add(fromPostBuilder(postDescriptor, postBuilder))
        }

        return chanPostRepository.insertOrUpdateManyBlocking(unparsedPosts, isCatalog).unwrap()
    }

    @OptIn(ExperimentalTime::class)
    private fun storeNewPostsInRepository(
            toParse: List<Post.Builder>,
            isCatalog: Boolean
    ): List<Long> {
        if (toParse.isEmpty()) {
            return emptyList()
        }

        val unparsedPosts: MutableList<ChanPostUnparsed> = ArrayList(toParse.size)
        for (postBuilder in toParse) {
            val postDescriptor = create(
                    postBuilder.board.site.name(),
                    postBuilder.board.code,
                    postBuilder.getOpId(),
                    postBuilder.id
            )

            unparsedPosts.add(fromPostBuilder(postDescriptor, postBuilder))
        }

        return chanPostRepository.insertOrUpdateManyBlocking(unparsedPosts, isCatalog).unwrap()
    }

    @OptIn(ExperimentalTime::class)
    private fun reloadPostsFromRepository(
            updatedPosts: Set<Long>,
            chanReaderProcessor: ChanReaderProcessor,
            chanDescriptor: ChanDescriptor
    ): Triple<List<Post>, Duration, Duration> {
        val (toParse, reloadingDuration) = measureTimedValue {
            return@measureTimedValue when (chanDescriptor) {
                is ChanDescriptor.ThreadDescriptor -> {
                    chanPostRepository.getThreadPostsBlocking(
                            chanDescriptor,
                            chanReaderProcessor.getPostNoListOrdered()
                    ).unwrap()
                }
                is ChanDescriptor.CatalogDescriptor -> {
                    chanPostRepository.getCatalogOriginalPostsBlocking(
                            chanDescriptor,
                            chanReaderProcessor.getPostNoListOrdered()
                    ).unwrap()
                }
            }.map { unparsedPost ->
                return@map ChanPostUnparsedMapper.toPostBuilder(
                        loadable.board,
                        unparsedPost
                )
            }
        }

        val (parsedPosts, parsingDuration) = measureTimedValue {
            return@measureTimedValue parsePosts(updatedPosts, toParse, chanDescriptor)
        }

        if (parsedPosts.isEmpty()) {
            return Triple(emptyList(), reloadingDuration, parsingDuration)
        }

        val sortedPosts = chanReaderProcessor.getPostsSortedByIndexes(parsedPosts)
        return Triple(sortedPosts, reloadingDuration, parsingDuration)
    }

    private fun parsePosts(
            updatedPosts: Set<Long>,
            toParse: List<Post.Builder>,
            chanDescriptor: ChanDescriptor
    ): List<Post> {
        val cachedPosts = toParse.mapNotNull { postBuilder ->
            val fromCache = cachedPostsMap[postBuilder.id]
                    ?: return@mapNotNull null

            if (updatedPosts.contains(postBuilder.id)) {
                // Post has new info since we last cached it so we need to parse it again
                return@mapNotNull null
            }

            // Post didn't change since we last cached it and we have it in the cache - we don't
            // need to parse it again
            return@mapNotNull fromCache
        }

        val parsedPosts = toParse
                .filter { postBuilder -> !cachedPostsMap.containsKey(postBuilder.id) }
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

        return cachedPosts + parsedPosts
    }

    private fun processPosts(op: Post.Builder, allPosts: List<Post>): ChanLoaderResponse {
        val response = ChanLoaderResponse(op, ArrayList(allPosts.size))
        val cachedPosts = ArrayList<Post>()
        val newPosts = ArrayList<Post>()

        if (cachedPostsMap.isNotEmpty()) {
            // Add all posts that were parsed before
            cachedPosts.addAll(cachedPostsMap.values)
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
        cachedPostsMap.clear()
        cachedPostsMap.putAll(totalPosts.associateBy { post -> post.no })

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