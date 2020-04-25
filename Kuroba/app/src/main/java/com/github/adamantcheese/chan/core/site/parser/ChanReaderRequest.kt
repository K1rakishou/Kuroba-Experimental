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

import android.util.JsonReader
import com.github.adamantcheese.chan.core.database.DatabaseManager
import com.github.adamantcheese.chan.core.database.DatabaseSavedReplyManager
import com.github.adamantcheese.chan.core.manager.ArchivesManager
import com.github.adamantcheese.chan.core.manager.FilterEngine
import com.github.adamantcheese.chan.core.mapper.ArchiveThreadMapper
import com.github.adamantcheese.chan.core.mapper.ChanPostMapper
import com.github.adamantcheese.chan.core.mapper.ChanPostMapper.fromPost
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
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.ModularResult.Companion.safeRun
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor.Companion.create
import com.github.adamantcheese.model.data.post.ChanPost
import com.github.adamantcheese.model.repository.ChanPostRepository
import com.github.adamantcheese.model.repository.ThirdPartyArchiveInfoRepository
import com.google.gson.Gson
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
        private val gson: Gson,
        private val databaseManager: DatabaseManager,
        private val filterEngine: FilterEngine,
        private val chanPostRepository: ChanPostRepository,
        private val appConstants: AppConstants,
        private val archivesManager: ArchivesManager,
        private val thirdPartyArchiveInfoRepository: ThirdPartyArchiveInfoRepository,
        private val request: ChanLoaderRequestParams
) : JsonReaderRequest<ChanLoaderResponse>(getChanUrl(request.loadable).toString(), request.listener, request.errorListener) {
    val loadable = request.loadable.clone()

    private val reader: ChanReader
    private val databaseSavedReplyManager: DatabaseSavedReplyManager

    private val cachedPostsMap: MutableMap<Long, Post>
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

        val (archivePosts, archiveFetchDuration) = measureTimedValue {
            return@measureTimedValue getPostsFromArchiveIfNecessary(chanReaderProcessor.getToParse())
                    .safeUnwrap { error ->
                        Logger.e(TAG, "Error while trying to get posts from archive", error)
                        return@measureTimedValue emptyList<Post.Builder>()
                    }
        }

        val (parsedPosts, parsingDuration) = measureTimedValue {
            // TODO(archives): !!!!!!!!!!!!!
            val posts = if (loadable.isCatalogMode) {
                chanReaderProcessor.getToParse() + archivePosts
            } else {
                archivePosts
            }

            return@measureTimedValue parseNewPostsPosts(posts)
        }

        val (storedPostNoList, storeDuration) = measureTimedValue {
            // TODO(archives): delete posts from the cache once it exceeds stickyCap
            //  (in case it's neither null nor -1)
            var stickyCap = chanReaderProcessor.op?.stickyCap
            if (stickyCap != null && stickyCap < 0) {
                stickyCap = null
            }

            storeNewPostsInRepository(
                    parsedPosts,
                    loadable.isCatalogMode
            )
        }

        val (reloadedPosts, reloadingDuration) = reloadPostsFromRepository(
                chanReaderProcessor,
                getDescriptor(chanReaderProcessor.loadable)
        )

        val cachedPostsCount = chanPostRepository.getCachedValuesCount()

        Logger.d(TAG, "ChanReaderRequest.readJson() stats:\n" +
                "Store new posts took $storeDuration (stored ${storedPostNoList.size} posts).\n" +
                "Reload posts took $reloadingDuration, parse posts took = $parsingDuration (reloaded ${reloadedPosts.size} posts).\n" +
                "Archive fetch took $archiveFetchDuration, (fetched ${archivePosts.size} deleted posts).\n" +
                "Total in-memory cached posts count = ($cachedPostsCount/${appConstants.maxPostsCountInPostsCache}).")

        val op = checkNotNull(chanReaderProcessor.op) { "OP is null" }
        return processPosts(op, reloadedPosts)
    }

    private fun getPostsFromArchiveIfNecessary(
            freshPostsFromServer: List<Post.Builder>
    ): ModularResult<List<Post.Builder>> {
        return safeRun<List<Post.Builder>> {
            if (loadable.isCatalogMode) {
                return@safeRun emptyList()
            }

            if (request.isPinWatcherLoader) {
                // We don't want to fetch deleted posts from archives for PinWatchers to avoid spamming
                // the archives's servers with our requests every n-seconds. Instead, we load deleted
                // posts only when the user open a thread normally (or by clicking a Pin).
                return@safeRun emptyList()
            }

            val threadDescriptor = DescriptorUtils.getThreadDescriptorOrThrow(loadable)
            val archiveDescriptor = archivesManager.getArchiveDescriptor(threadDescriptor)
            if (archiveDescriptor == null) {
                // We probably don't have archives for this site or all archives are dead

                Logger.d(TAG, "No archives for thread descriptor: $threadDescriptor")
                return@safeRun emptyList()
            }

            Logger.d(TAG, "Got archive descriptor: $archiveDescriptor")

            // TODO(archives): check whether it's okay to fetch posts:
            //  1. If it's an automatic thread update, then only fetch new posts once in 1 hour.
            //  2. If the user manually clicks the "fetch posts from archives" button then only fetch
            //      the posts once in like 5-10 minutes.

            val threadArchiveRequestLink = archivesManager.getRequestLinkForThread(
                    threadDescriptor,
                    archiveDescriptor
            )

            if (threadArchiveRequestLink == null) {
                // We probably don't have archives for this site or all archives are dead
                return@safeRun emptyList()
            }

            val supportsMedia = archivesManager.doesArchiveStoreMedia(
                    archiveDescriptor,
                    loadable.boardDescriptor
            )

            val supportsMediaThumbnails = archivesManager.doesArchiveStoreThumbnails(archiveDescriptor)

            val archiveThread = thirdPartyArchiveInfoRepository.fetchThreadFromNetworkBlocking(
                    threadArchiveRequestLink,
                    threadDescriptor.opNo,
                    supportsMediaThumbnails,
                    supportsMedia
            ).unwrap()

            if (archiveThread.posts.isEmpty()) {
                return@safeRun emptyList()
            }

            // TODO(archives): !!!!!!!!!!!!!
//            val freshPostNoSet = freshPostsFromServer.map { postBuilder -> postBuilder.id }.toSet()
//            val archivePostsNoList = archiveThread.posts.map { archivePost -> archivePost.postNo }
//
//            val notCachedArchivePostNoSet = chanPostRepository.filterAlreadyCachedPostNo(
//                    threadDescriptor,
//                    archivePostsNoList
//            ).unwrap().toSet()
//
//            val archivePostsThatWereDeleted = archiveThread.posts.filter { archivePost ->
//                return@filter archivePost.postNo !in freshPostNoSet
//                        && archivePost.postNo !in notCachedArchivePostNoSet
//            }
//
//            Logger.d(TAG, "archivesRepository.fetchThreadFromNetworkBlocking fetched " +
//                    "${archiveThread.posts.size} posts in total and " +
//                    "${archivePostsThatWereDeleted.size} deleted posts")

            val mappedArchivePosts = ArchiveThreadMapper.fromThread(
                    loadable.board,
//                    ArchivesRemoteSource.ArchiveThread(archivePostsThatWereDeleted),
                    archiveThread
            )

            // TODO(archives): this is probably okay but just to make sure I need to double check this
            mappedArchivePosts.forEach { postBuilder ->
                postBuilder.setArchiveDescriptor(archiveDescriptor)
            }

            return@safeRun mappedArchivePosts
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun storeNewPostsInRepository(
            posts: List<Post>,
            isCatalog: Boolean
    ): List<Long> {
        if (posts.isEmpty()) {
            return emptyList()
        }

        val chanPosts: MutableList<ChanPost> = ArrayList(posts.size)
        for (post in posts) {
            val postDescriptor = create(
                    post.board.site.name(),
                    post.board.code,
                    post.opNo,
                    post.no
            )

            chanPosts.add(fromPost(gson, postDescriptor, post))
        }

        return chanPostRepository.insertOrUpdateManyBlocking(
                chanPosts,
                isCatalog
        ).unwrap()
    }

    @OptIn(ExperimentalTime::class)
    private fun reloadPostsFromRepository(
            chanReaderProcessor: ChanReaderProcessor,
            chanDescriptor: ChanDescriptor
    ): Pair<List<Post>, Duration> {
        val (posts, reloadingDuration) = measureTimedValue {
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
            }.map { post ->
                return@map ChanPostMapper.toPost(
                        gson,
                        loadable.board,
                        post
                )
            }
        }

        val sortedPosts = chanReaderProcessor.getPostsSortedByIndexes(posts)
        return Pair(sortedPosts, reloadingDuration)
    }

    private fun parseNewPostsPosts(postBuildersToParse: List<Post.Builder>): List<Post> {
        val internalIds = postBuildersToParse.map { postBuilder -> postBuilder.id }.toSet()

        return postBuildersToParse
                .map { postToParse ->
                    return@map PostParseCallable(
                            filterEngine,
                            filters,
                            databaseSavedReplyManager,
                            postToParse,
                            reader,
                            internalIds
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
                    if (cachedPost.deleted.get()) {
                        // We already updated this post as deleted (most likely we got this info from
                        // a third-party archive)
                        continue
                    }

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