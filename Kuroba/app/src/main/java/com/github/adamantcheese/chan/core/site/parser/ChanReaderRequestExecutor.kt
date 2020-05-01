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
import com.github.adamantcheese.chan.core.database.DatabaseSavedReplyManager
import com.github.adamantcheese.chan.core.di.NetModule
import com.github.adamantcheese.chan.core.manager.ArchivesManager
import com.github.adamantcheese.chan.core.manager.FilterEngine
import com.github.adamantcheese.chan.core.mapper.ArchiveThreadMapper
import com.github.adamantcheese.chan.core.mapper.ChanPostMapper
import com.github.adamantcheese.chan.core.mapper.ChanPostMapper.fromPost
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Filter
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.core.site.loader.ChanLoaderException
import com.github.adamantcheese.chan.core.site.loader.ChanLoaderRequestParams
import com.github.adamantcheese.chan.core.site.loader.ChanLoaderResponse
import com.github.adamantcheese.chan.core.site.loader.ServerException
import com.github.adamantcheese.chan.utils.DescriptorUtils
import com.github.adamantcheese.chan.utils.DescriptorUtils.getDescriptor
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.errorMessageOrClassName
import com.github.adamantcheese.common.AppConstants
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.ModularResult.Companion.Try
import com.github.adamantcheese.common.suspendCall
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveFetchResult
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor.Companion.create
import com.github.adamantcheese.model.data.post.ChanPost
import com.github.adamantcheese.model.repository.ChanPostRepository
import com.github.adamantcheese.model.repository.ThirdPartyArchiveInfoRepository
import com.github.adamantcheese.model.source.remote.ArchivesRemoteSource
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.HttpUrl
import okhttp3.Request
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayList
import kotlin.coroutines.CoroutineContext
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

/**
 * Process a typical imageboard json response.<br></br>
 * This class is highly multithreaded, take good care to not access models that are to be only
 * changed on the main thread.
 */
class ChanReaderRequestExecutor(
        private val gson: Gson,
        private val okHttpClient: NetModule.ProxiedOkHttpClient,
        private val databaseSavedReplyManager: DatabaseSavedReplyManager,
        private val filterEngine: FilterEngine,
        private val chanPostRepository: ChanPostRepository,
        private val appConstants: AppConstants,
        private val archivesManager: ArchivesManager,
        private val thirdPartyArchiveInfoRepository: ThirdPartyArchiveInfoRepository,
        private val request: ChanLoaderRequestParams,
        private val verboseLogsEnabled: Boolean
) : CoroutineScope {
    private val job = SupervisorJob()

    private val loadable = request.loadable.clone()
    private val forceLoading = request.forceLoading
    private val reader = request.chanReader
    private val cachedPostsMap = request.cached.associateBy { post -> post.no }.toMutableMap()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job + CoroutineName("ChanReaderRequest")

    fun execute(url: String, resultCallback: (ModularResult<ChanLoaderResponse>) -> Unit): Job {
        return launch {
            val result = Try {
                val request = Request.Builder()
                        .url(url)
                        .get()
                        .build()

                val chanReaderProcessor = ChanReaderProcessor(chanPostRepository, loadable)
                val response = okHttpClient.suspendCall(request)

                if (!response.isSuccessful) {
                    if (response.code == 404) {
                        if (chanReaderProcessor.loadable.isDownloadingOrDownloaded) {
                            // Thread is being downloaded or has been already downloaded, so use
                            // local copy.
                            throw ServerException(response.code)
                        }

                        // Thread is not being downloading/downloaded so fetch posts from an archive
                        val chanLoaderResponse = tryLoadFromArchivesOrLocalCopyIfPossible(chanReaderProcessor)
                        if (chanLoaderResponse == null) {
                            // Couldn't load neither posts from an archive nor posts cached in the
                            // database
                            throw ServerException(response.code)
                        }

                        return@Try chanLoaderResponse!!
                    }

                    throw ServerException(response.code)
                }

                val body = response.body
                        ?: throw IOException("Response has no body")

                return@Try body.byteStream().use { inputStream ->
                    return@use JsonReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
                            .use { jsonReader -> readJson(chanReaderProcessor, jsonReader).unwrap() }
                }
            }.mapError { error -> ChanLoaderException(error) }

            resultCallback.invoke(result)
        }
    }

    suspend fun tryLoadFromArchivesOrLocalCopyIfPossible(
            chanReaderProcessor: ChanReaderProcessor
    ): ChanLoaderResponse? {
        val postsFromArchive = getPostsFromArchiveIfNecessary(chanReaderProcessor.getToParse())
                .safeUnwrap { error ->
                    Logger.e(TAG, "Error while trying to get posts from archive", error)
                    return null
                }

        if (postsFromArchive.isNotEmpty()) {
            val parsedPosts = parseNewPostsPosts(loadable, postsFromArchive)
            storeNewPostsInRepository(parsedPosts, false)
        }

        val reloadedPosts = reloadPostsFromRepository(
                chanReaderProcessor,
                getDescriptor(loadable)
        )

        if (reloadedPosts.isEmpty()) {
            Logger.d(TAG, "reloadPostsFromRepository() returned empty list")
            return null
        }

        val originalPost = reloadedPosts.firstOrNull { post -> post.isOP }
        if (originalPost == null) {
            Logger.e(TAG, "Reloaded from the database posts have no OP")
            return null
        }

        val threadDescriptor = DescriptorUtils.getThreadDescriptorOrThrow(loadable)
        val archiveDescriptor = archivesManager.getArchiveDescriptor(threadDescriptor, forceLoading)
                .safeUnwrap { error ->
                    Logger.e(TAG, "Error while trying to get archive descriptor", error)
                    return null
                }

        return ChanLoaderResponse(originalPost.toPostBuilder(archiveDescriptor), reloadedPosts)
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun readJson(
            chanReaderProcessor: ChanReaderProcessor,
            reader: JsonReader
    ): ModularResult<ChanLoaderResponse> {
        return Try {
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
                val posts = chanReaderProcessor.getToParse() + archivePosts
                return@measureTimedValue parseNewPostsPosts(loadable, posts)
            }

            val (storedPostNoList, storeDuration) = measureTimedValue {
                storeNewPostsInRepository(
                        parsedPosts,
                        loadable.isCatalogMode
                )
            }

            val (reloadedPosts, reloadingDuration) = measureTimedValue {
                return@measureTimedValue reloadPostsFromRepository(
                        chanReaderProcessor,
                        getDescriptor(chanReaderProcessor.loadable)
                )
            }

            val cachedPostsCount = chanPostRepository.getCachedValuesCount()

            val logMsg = """
ChanReaderRequest.readJson() stats:
Store new posts took $storeDuration (stored ${storedPostNoList.size} posts).
Reload posts took $reloadingDuration, parse posts took = $parsingDuration (reloaded ${reloadedPosts.size} posts).
Archive fetch took $archiveFetchDuration, (fetched ${archivePosts.size} deleted posts).
Total in-memory cached posts count = ($cachedPostsCount/${appConstants.maxPostsCountInPostsCache}).
"""

            Logger.d(TAG, logMsg)

            val op = checkNotNull(chanReaderProcessor.op) { "OP is null" }
            return@Try processPosts(op, reloadedPosts)
        }
    }

    suspend fun getPostsFromArchiveIfNecessary(
            freshPostsFromServer: List<Post.Builder>
    ): ModularResult<List<Post.Builder>> {
        return Try<List<Post.Builder>> {
            if (loadable.isCatalogMode) {
                return@Try emptyList()
            }

            if (loadable.isDownloadingOrDownloaded) {
                // Do not fetch posts from archives in local threads
                return@Try emptyList()
            }

            val threadDescriptor = DescriptorUtils.getThreadDescriptorOrThrow(loadable)
            val archiveDescriptor = archivesManager.getArchiveDescriptor(
                    threadDescriptor,
                    forceLoading
            ).unwrap()

            if (archiveDescriptor == null) {
                if (verboseLogsEnabled) {
                    Logger.d(TAG, "No archives for thread descriptor: $threadDescriptor")
                }

                // We probably don't have archives for this site or all archives are dead
                return@Try emptyList()
            }

            if (verboseLogsEnabled) {
                Logger.d(TAG, "Got archive descriptor: $archiveDescriptor")
            }

            val threadArchiveRequestLink = archivesManager.getRequestLinkForThread(
                    threadDescriptor,
                    archiveDescriptor
            )

            if (threadArchiveRequestLink == null) {
                return@Try emptyList()
            }

            val supportsMedia = archivesManager.doesArchiveStoreMedia(
                    archiveDescriptor,
                    loadable.boardDescriptor
            )

            val archiveThreadResult = thirdPartyArchiveInfoRepository.fetchThreadFromNetwork(
                    threadArchiveRequestLink,
                    threadDescriptor.opNo,
                    archivesManager.archiveStoresThumbnails(archiveDescriptor),
                    supportsMedia
            )

            val archiveThread = when (archiveThreadResult) {
                is ModularResult.Error -> {
                    Logger.e(TAG, "Error while fetching archive posts", archiveThreadResult.error)

                    val fetchResult = ThirdPartyArchiveFetchResult.error(
                            archiveDescriptor,
                            threadDescriptor,
                            archiveThreadResult.error.errorMessageOrClassName()
                    )

                    archivesManager.insertFetchHistory(fetchResult).unwrap()
                    ArchivesRemoteSource.ArchiveThread(emptyList())
                }
                is ModularResult.Value -> {
                    Logger.d(TAG, "Successfully fetched ${archiveThreadResult.value.posts.size} " +
                            "posts from archive ${archiveDescriptor}")

                    val fetchResult = ThirdPartyArchiveFetchResult.success(
                            archiveDescriptor,
                            threadDescriptor
                    )

                    archivesManager.insertFetchHistory(fetchResult).unwrap()
                    archiveThreadResult.value
                }
            }

            if (archiveThread.posts.isEmpty()) {
                return@Try emptyList()
            }

            val freshPostNoSet = freshPostsFromServer.map { postBuilder -> postBuilder.id }.toSet()
            val archivePostsNoList = archiveThread.posts.map { archivePost -> archivePost.postNo }

            val notCachedArchivePostNoSet = chanPostRepository.filterAlreadyCachedPostNo(
                    threadDescriptor,
                    archivePostsNoList
            ).unwrap().toSet()

            val archivePostsThatWereDeleted = archiveThread.posts.filter { archivePost ->
                return@filter archivePost.postNo !in freshPostNoSet
                        && archivePost.postNo in notCachedArchivePostNoSet
            }

            Logger.d(TAG, "thirdPartyArchiveInfoRepository.fetchThreadFromNetwork fetched " +
                    "${archiveThread.posts.size} posts in total and " +
                    "${archivePostsThatWereDeleted.size} deleted posts")

            val mappedArchivePosts = ArchiveThreadMapper.fromThread(
                    loadable.board,
                    ArchivesRemoteSource.ArchiveThread(archivePostsThatWereDeleted)
            )

            mappedArchivePosts.forEach { postBuilder ->
                postBuilder.setArchiveDescriptor(archiveDescriptor)
            }

            return@Try mappedArchivePosts
        }
    }

    @OptIn(ExperimentalTime::class)
    suspend fun storeNewPostsInRepository(
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

        return chanPostRepository.insertOrUpdateMany(
                chanPosts,
                isCatalog
        ).unwrap()
    }

    suspend fun reloadPostsFromRepository(
            chanReaderProcessor: ChanReaderProcessor,
            chanDescriptor: ChanDescriptor
    ): List<Post> {
        val posts = when (chanDescriptor) {
            is ChanDescriptor.ThreadDescriptor -> {
                var maxCount = chanReaderProcessor.op?.stickyCap ?: Int.MAX_VALUE
                if (maxCount < 0) {
                    maxCount = Int.MAX_VALUE
                }

                // When in the mode, we can just select every post we have for this thread
                // descriptor and then just sort the in the correct order. We should also use
                // the stickyCap parameter if present.
                chanPostRepository.getThreadPosts(chanDescriptor, maxCount).unwrap()
            }
            is ChanDescriptor.CatalogDescriptor -> {
                val postsToGet = chanReaderProcessor.getPostNoListOrdered()

                // When in catalog mode, we can't just select posts from the database and then
                // sort them, because the actual order of the posts in the catalog depends on
                // a lot of stuff (thread may be saged/auto-saged by mods etc). So the easiest way
                // is to get every post by it's postNo that we receive from the server. It's
                // already in correct order (the server order) so we don't even need to sort
                // them.
                chanPostRepository.getCatalogOriginalPosts(chanDescriptor, postsToGet).unwrap()
            }
        }.map { post -> ChanPostMapper.toPost(gson, loadable.board, post) }

        return when (chanDescriptor) {
            is ChanDescriptor.ThreadDescriptor -> posts
            is ChanDescriptor.CatalogDescriptor -> chanReaderProcessor.getPostsSortedByIndexes(posts)
        }
    }

    private fun parseNewPostsPosts(
            loadable: Loadable,
            postBuildersToParse: List<Post.Builder>
    ): List<Post> {
        if (verboseLogsEnabled) {
            Logger.d(TAG, "parseNewPostsPosts(loadable=${loadable.toShortString()}, " +
                    "postsToParseSize=${postBuildersToParse.size}")
        }

        if (postBuildersToParse.isEmpty()) {
            return emptyList()
        }

        val internalIds = postBuildersToParse.map { postBuilder ->
            return@map postBuilder.id
        }.toSet()

        return postBuildersToParse
                .map { postToParse ->
                    return@map PostParseCallable(
                            filterEngine,
                            loadFilters(loadable),
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

    private fun loadFilters(loadable: Loadable): List<Filter> {
        return filterEngine.enabledFilters
                .filter { filter -> filterEngine.matchesBoard(filter, loadable.board) }
                // copy the filter because it will get used on other threads
                .map { filter -> filter.clone() }
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

        @JvmStatic
        fun getChanUrl(loadable: Loadable): HttpUrl {
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