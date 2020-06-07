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
import com.github.adamantcheese.chan.ui.theme.Theme
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.DescriptorUtils.getDescriptor
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.PostUtils
import com.github.adamantcheese.chan.utils.errorMessageOrClassName
import com.github.adamantcheese.common.AppConstants
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.ModularResult.Companion.Try
import com.github.adamantcheese.common.suspendCall
import com.github.adamantcheese.model.data.archive.ArchivePost
import com.github.adamantcheese.model.data.archive.ArchiveThread
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveFetchResult
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor.Companion.create
import com.github.adamantcheese.model.data.post.ChanPost
import com.github.adamantcheese.model.repository.ChanPostRepository
import com.github.adamantcheese.model.repository.ThirdPartyArchiveInfoRepository
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.HttpUrl
import okhttp3.Request
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLException
import kotlin.collections.ArrayList
import kotlin.coroutines.CoroutineContext
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

/**
 * This class is kinda over complicated right now. It does way too much stuff. It tries to load the
 * catalog/thread json from the network as well as thread json from third-party archives (only
 * for 4chan). It automatically redirects you to an archived thread in case of original thread getting
 * 404ed. It automatically loads cached posts from the database when it was impossible to load posts
 * from the network. All of that stuff should be separated into their own classes some time in the
 * future. For now it will stay the way it is.
 * */
class ChanLoaderRequestExecutor(
  private val gson: Gson,
  private val okHttpClient: NetModule.ProxiedOkHttpClient,
  private val databaseSavedReplyManager: DatabaseSavedReplyManager,
  private val filterEngine: FilterEngine,
  private val chanPostRepository: ChanPostRepository,
  private val appConstants: AppConstants,
  private val archivesManager: ArchivesManager,
  private val thirdPartyArchiveInfoRepository: ThirdPartyArchiveInfoRepository,
  private val verboseLogsEnabled: Boolean,
  private val currentTheme: Theme
) : CoroutineScope {
  private val job = SupervisorJob()

  override val coroutineContext: CoroutineContext
    get() = Dispatchers.IO + job + CoroutineName("ChanReaderRequest")

  @Suppress("FoldInitializerAndIfToElvis")
  fun execute(
    url: String,
    requestParams: ChanLoaderRequestParams,
    resultCallback: (ModularResult<ChanLoaderResponse>) -> Unit
  ): Job {
    return launch {
      val result = Try { loadPostsInternal(url, requestParams) }
        .mapError { error -> ChanLoaderException(error) }

      resultCallback.invoke(result)
    }
  }

  private suspend fun loadPostsInternal(
    url: String,
    requestParams: ChanLoaderRequestParams
  ): ChanLoaderResponse {
    BackgroundUtils.ensureBackgroundThread()

    val descriptor = getDescriptor(requestParams.loadable)

    val archiveDescriptor = if (requestParams.retrieveDeletedPostsFromArchives) {
      when (descriptor) {
        is ChanDescriptor.ThreadDescriptor -> archivesManager.getArchiveDescriptor(descriptor).unwrap()
        is ChanDescriptor.CatalogDescriptor -> null
      }
    } else {
      null
    }

    val request = Request.Builder()
      .url(url)
      .get()
      .build()

    val response = try {
      okHttpClient.suspendCall(request)
    } catch (error: IOException) {
      // An IOException occurred during the network request. This is probably a network problem
      // or maybe even a server issue. Instead of showing the error we can try to load whatever
      // there is in the database cache.
      if (requestParams.loadable.isDownloadingOrDownloaded) {
        // Thread is being downloaded or has been already downloaded, so use
        // local copy instead.
        throw error
      }

      val chanLoaderResponse = tryLoadFromDiskCache(requestParams)
        ?: throw error

      Logger.d(TAG, "Successfully recovered from network error (${error.errorMessageOrClassName()})")
      return chanLoaderResponse!!
    }

    if (!response.isSuccessful) {
      if (response.code == 404) {
        if (requestParams.loadable.isDownloadingOrDownloaded) {
          // Thread is being downloaded or has been already downloaded, so use
          // local copy.
          throw ServerException(response.code)
        }

        // Thread is not being downloading/downloaded so fetch posts from an archive
        val chanLoaderResponse = tryLoadFromArchivesOrLocalCopyIfPossible(
          descriptor,
          archiveDescriptor,
          requestParams
        )

        if (chanLoaderResponse == null) {
          // Couldn't load neither posts from an archive nor posts cached in the
          // database
          throw ServerException(response.code)
        }

        Logger.d(TAG, "Successfully recovered from 404 error")
        return chanLoaderResponse!!
      }

      throw ServerException(response.code)
    }

    val body = response.body
      ?: throw IOException("Response has no body")

    return body.byteStream().use { inputStream ->
      return@use JsonReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
        .use { jsonReader ->
          return@use readJson(
            descriptor,
            archiveDescriptor,
            requestParams,
            jsonReader
          ).unwrap()
        }
    }
  }

  private suspend fun tryLoadFromDiskCache(
    requestParams: ChanLoaderRequestParams
  ): ChanLoaderResponse? {
    BackgroundUtils.ensureBackgroundThread()

    val reloadedPosts = reloadPostsFromRepository(
      getDescriptor(requestParams.loadable),
      requestParams.loadable
    )

    if (reloadedPosts.isEmpty()) {
      Logger.d(TAG, "tryLoadFromDiskCache() returned empty list")
      return null
    }

    val originalPost = reloadedPosts.firstOrNull { post -> post.isOP }
    if (originalPost == null) {
      Logger.e(TAG, "tryLoadFromDiskCache() Reloaded from the database posts have no OP")
      return null
    }

    fillInReplies(reloadedPosts)

    return ChanLoaderResponse(originalPost.toPostBuilder(null), reloadedPosts).apply {
      preloadPostsInfo()
    }
  }

  private suspend fun tryLoadFromArchivesOrLocalCopyIfPossible(
    descriptor: ChanDescriptor,
    archiveDescriptor: ArchiveDescriptor?,
    requestParams: ChanLoaderRequestParams
  ): ChanLoaderResponse? {
    BackgroundUtils.ensureBackgroundThread()

    if (descriptor !is ChanDescriptor.ThreadDescriptor) {
      // We don't support catalog loading from archives
      return null
    }

    val postsFromArchive = getPostsFromArchiveIfNecessary(
      emptyList(),
      requestParams.loadable,
      descriptor,
      archiveDescriptor
    ).safeUnwrap { error ->
      Logger.e(TAG, "tryLoadFromArchivesOrLocalCopyIfPossible() Error while trying to get " +
        "posts from archive", error)
      return null
    }

    if (postsFromArchive.isNotEmpty()) {
      val parsedPosts = parseNewPostsPosts(
        descriptor,
        requestParams.loadable,
        requestParams.chanReader,
        postsFromArchive,
        Int.MAX_VALUE
      )

      storeNewPostsInRepository(parsedPosts, false)
    }

    val reloadedPosts = reloadPostsFromRepository(
      descriptor,
      requestParams.loadable
    )

    if (reloadedPosts.isEmpty()) {
      Logger.d(TAG, "tryLoadFromArchivesOrLocalCopyIfPossible() returned empty list")
      return null
    }

    val originalPost = reloadedPosts.firstOrNull { post -> post.isOP }
    if (originalPost == null) {
      Logger.e(TAG, "tryLoadFromArchivesOrLocalCopyIfPossible() Reloaded from the database " +
        "posts have no OP")
      return null
    }

    fillInReplies(reloadedPosts)

    return ChanLoaderResponse(originalPost.toPostBuilder(archiveDescriptor), reloadedPosts).apply {
      preloadPostsInfo()
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun readJson(
    descriptor: ChanDescriptor,
    archiveDescriptor: ArchiveDescriptor?,
    requestParams: ChanLoaderRequestParams,
    jsonReader: JsonReader
  ): ModularResult<ChanLoaderResponse> {
    BackgroundUtils.ensureBackgroundThread()

    return Try {
      val chanReaderProcessor = ChanReaderProcessor(
        chanPostRepository,
        requestParams.loadable
      )

      val loadable = requestParams.loadable
      val reader = requestParams.chanReader

      when {
        loadable.isThreadMode -> reader.loadThread(jsonReader, chanReaderProcessor)
        loadable.isCatalogMode -> reader.loadCatalog(jsonReader, chanReaderProcessor)
        else -> throw IllegalArgumentException("Unknown mode")
      }

      val (archivePosts, archiveFetchDuration) = measureTimedValue {
        return@measureTimedValue getPostsFromArchiveIfNecessary(
          chanReaderProcessor.getToParse(),
          requestParams.loadable,
          descriptor,
          archiveDescriptor
        ).safeUnwrap { error ->
          Logger.e(TAG, "Error while trying to get posts from archive", error)
          return@measureTimedValue emptyList<Post.Builder>()
        }
      }

      val (parsedPosts, parsingDuration) = measureTimedValue {
        val posts = mergePosts(chanReaderProcessor.getToParse(), archivePosts)
        return@measureTimedValue parseNewPostsPosts(
          descriptor,
          loadable,
          reader,
          posts,
          chanReaderProcessor.getThreadCap()
        )
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
          descriptor,
          requestParams.loadable
        )
      }

      val cachedPostsCount = chanPostRepository.getCachedValuesCount()
      val postsFromCache = reloadedPosts.count { post -> post.isFromCache }

      val logMsg = """
ChanReaderRequest.readJson() stats:
Store new posts took $storeDuration (stored ${storedPostNoList.size} posts).
Reload posts took $reloadingDuration, (reloaded ${reloadedPosts.size} posts, from cache: $postsFromCache).
Parse posts took = $parsingDuration, (parsed ${parsedPosts.size} posts).
Archive fetch took $archiveFetchDuration, (fetched ${archivePosts.size} deleted posts).
Total in-memory cached posts count = ($cachedPostsCount/${appConstants.maxPostsCountInPostsCache}).
"""

      Logger.d(TAG, logMsg)

      val op = checkNotNull(chanReaderProcessor.op) { "OP is null" }
      return@Try processPosts(op, reloadedPosts, requestParams)
    }
  }

  /**
   * [postsFromServer] and [postsFromArchive] may contain posts with the same postNo so we need to
   * filter out [postsFromServer] in case there are already posts with the same postNos in
   * [postsFromArchive].
   * */
  private fun mergePosts(
    postsFromServer: List<Post.Builder>,
    postsFromArchive: List<Post.Builder>
  ): List<Post.Builder> {
    BackgroundUtils.ensureBackgroundThread()

    val resultList = mutableListOf<Post.Builder>()
    val archivePostsMap = postsFromArchive
      .associateBy { archivePost -> archivePost.id }
      .toMutableMap()

    postsFromServer.forEach { freshPost ->
      // If we have two posts with the same postNo in both fresh posts and posts from archives
      // then prefer the post from archives because it should contain more useful information
      // (like deleted images etc)
      if (!archivePostsMap.containsKey(freshPost.id)) {
        resultList += freshPost
      } else {
        resultList += requireNotNull(archivePostsMap[freshPost.id])
        archivePostsMap.remove(freshPost.id)
      }
    }

    archivePostsMap.values.forEach { actuallyDeletedPost ->
      resultList += actuallyDeletedPost
    }

    return resultList
  }

  private suspend fun getPostsFromArchiveIfNecessary(
    freshPostsFromServer: List<Post.Builder>,
    loadable: Loadable,
    descriptor: ChanDescriptor,
    archiveDescriptor: ArchiveDescriptor?
  ): ModularResult<List<Post.Builder>> {
    BackgroundUtils.ensureBackgroundThread()

    return Try<List<Post.Builder>> {
      if (descriptor !is ChanDescriptor.ThreadDescriptor) {
        return@Try emptyList()
      }

      if (loadable.isDownloadingOrDownloaded) {
        // Do not fetch posts from archives in local threads
        return@Try emptyList()
      }

      if (archiveDescriptor == null) {
        if (verboseLogsEnabled) {
          Logger.d(TAG, "No archives for thread descriptor: $descriptor")
        }

        // We probably don't have archives for this site or all archives are dead
        return@Try emptyList()
      }

      if (verboseLogsEnabled) {
        Logger.d(TAG, "Got archive descriptor: $archiveDescriptor")
      }

      val threadArchiveRequestLink = archivesManager.getRequestLinkForThread(
        descriptor,
        archiveDescriptor
      )

      if (threadArchiveRequestLink == null) {
        return@Try emptyList()
      }

      val archiveThreadResult = thirdPartyArchiveInfoRepository.fetchThreadFromNetwork(
        threadArchiveRequestLink,
        descriptor.opNo
      )

      val archiveThread = when (archiveThreadResult) {
        is ModularResult.Error -> {
          if (archiveThreadResult.error is CancellationException ||
            archiveThreadResult.error is SSLException
          ) {
            Logger.e(
              TAG,
              "Error while fetching archive posts",
              archiveThreadResult.error.errorMessageOrClassName()
            )
          } else {
            Logger.e(TAG, "Error while fetching archive posts", archiveThreadResult.error)
          }

          val fetchResult = ThirdPartyArchiveFetchResult.error(
            archiveDescriptor,
            descriptor,
            archiveThreadResult.error.errorMessageOrClassName()
          )

          archivesManager.insertFetchHistory(fetchResult).unwrap()
          ArchiveThread(emptyList())
        }
        is ModularResult.Value -> {
          Logger.d(TAG, "Successfully fetched ${archiveThreadResult.value.posts.size} " +
            "posts from archive ${archiveDescriptor}")

          val fetchResult = ThirdPartyArchiveFetchResult.success(
            archiveDescriptor,
            descriptor
          )

          archivesManager.insertFetchHistory(fetchResult).unwrap()
          archiveThreadResult.value
        }
      }

      if (archiveThread.posts.isEmpty()) {
        return@Try emptyList()
      }

      val archivePostsNoList = archiveThread.posts.map { archivePost -> archivePost.postNo }.toSet()
      val freshPostsMap = freshPostsFromServer.associateBy { postBuilder -> postBuilder.id }

      val cachedPostsMap = chanPostRepository.getThreadPosts(
        descriptor,
        archiveDescriptor.getArchiveId(),
        archivePostsNoList
      ).unwrap()
        .associateBy { chanPost -> chanPost.postDescriptor.postNo }

      val archivePostsThatWereDeleted = archiveThread.posts.filter { archivePost ->
        return@filter retainDeletedOrUpdatedPosts(
          archivePost,
          freshPostsMap,
          cachedPostsMap
        )
      }

      Logger.d(TAG, "thirdPartyArchiveInfoRepository.fetchThreadFromNetwork fetched " +
        "${archiveThread.posts.size} posts in total and " +
        "${archivePostsThatWereDeleted.size} deleted (or updated) posts")

      return@Try ArchiveThreadMapper.fromThread(
        loadable.board,
        ArchiveThread(archivePostsThatWereDeleted),
        archiveDescriptor
      )
    }
  }

  /**
   * Returns true (thus not filtering out this [archivePost]) if either both of the maps
   * [freshPostsMap] and [cachedPostsMap] does not contain this post or if it differs enough from
   * either the freshPost or the cachedPost.
   * */
  private fun retainDeletedOrUpdatedPosts(
    archivePost: ArchivePost,
    freshPostsMap: Map<Long, Post.Builder>,
    cachedPostsMap: Map<Long, ChanPost>
  ): Boolean {
    BackgroundUtils.ensureBackgroundThread()

    val freshPost = freshPostsMap[archivePost.postNo]
    if (freshPost != null) {
      // Post already exists in the fresh posts list we got from the server. We need to check
      // whether the archive post is more valuable to us than the fresh post (e.g. archived
      // post has more images than fresh post)
      return PostUtils.shouldRetainPostFromArchive(archivePost, freshPost)
    }

    val cachedPost = cachedPostsMap[archivePost.postNo]
    if (cachedPost != null) {
      // Post already exists in the cache/database.  We need to check whether the archive post
      // is more valuable to us than the fresh post (e.g. archived post has more images than
      // cached post)
      return PostUtils.shouldRetainPostFromArchive(archivePost, cachedPost)
    }

    // Post does not exist neither in fresh posts nor in cached posts meaning it was deleted from
    // the server so we need to retain it
    return true
  }

  @OptIn(ExperimentalTime::class)
  suspend fun storeNewPostsInRepository(
    posts: List<Post>,
    isCatalog: Boolean
  ): List<Long> {
    BackgroundUtils.ensureBackgroundThread()

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

      val archiveId = post.archiveDescriptor?.getArchiveId() ?: ArchiveDescriptor.NO_ARCHIVE_ID
      chanPosts.add(fromPost(gson, postDescriptor, post, archiveId))
    }

    return chanPostRepository.insertOrUpdateMany(
      chanPosts,
      isCatalog
    ).unwrap()
  }

  private suspend fun reloadPostsFromRepository(
    chanDescriptor: ChanDescriptor,
    loadable: Loadable
  ): List<Post> {
    BackgroundUtils.ensureBackgroundThread()

    val archiveId = archivesManager.getLastUsedArchiveForThread(chanDescriptor)?.getArchiveId()
      ?: ArchiveDescriptor.NO_ARCHIVE_ID

    return when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        chanPostRepository.getThreadPosts(chanDescriptor, archiveId, Int.MAX_VALUE)
          .unwrap()
          .sortedBy { chanPost -> chanPost.postDescriptor.postNo }
      }
      is ChanDescriptor.CatalogDescriptor -> {
        val postsToLoadCount = loadable.board.pages * loadable.board.perPage

        chanPostRepository.getCatalogOriginalPosts(chanDescriptor, archiveId, postsToLoadCount)
          .unwrap()
          // Sort in descending order by threads' lastModified value because that's the BUMP ordering
          .sortedByDescending { chanPost -> chanPost.lastModified }
      }
    }.map { post ->
      return@map ChanPostMapper.toPost(
        gson,
        loadable.board,
        post,
        currentTheme,
        archivesManager.getArchiveDescriptorByDatabaseId(post.archiveId)
      )
    }
  }

  private suspend fun reloadPostsFromRepository(
    chanReaderProcessor: ChanReaderProcessor,
    chanDescriptor: ChanDescriptor,
    loadable: Loadable
  ): List<Post> {
    BackgroundUtils.ensureBackgroundThread()

    val archiveId = archivesManager.getLastUsedArchiveForThread(chanDescriptor)?.getArchiveId()
      ?: ArchiveDescriptor.NO_ARCHIVE_ID

    val posts = when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        val maxCount = chanReaderProcessor.getThreadCap()

        // When in the mode, we can just select every post we have for this thread
        // descriptor and then just sort the in the correct order. We should also use
        // the stickyCap parameter if present.
        chanPostRepository.getThreadPosts(chanDescriptor, archiveId, maxCount)
          .unwrap()
          .sortedBy { chanPost -> chanPost.postDescriptor.postNo }
      }
      is ChanDescriptor.CatalogDescriptor -> {
        val postsToGet = chanReaderProcessor.getPostNoListOrdered()

        // When in catalog mode, we can't just select posts from the database and then
        // sort them, because the actual order of the posts in the catalog depends on
        // a lot of stuff (thread may be saged/auto-saged by mods etc). So the easiest way
        // is to get every post by it's postNo that we receive from the server. It's
        // already in correct order (the server order) so we don't even need to sort
        // them.
        chanPostRepository.getCatalogOriginalPosts(chanDescriptor, archiveId, postsToGet)
          .unwrap()
      }
    }.map { post ->
      return@map ChanPostMapper.toPost(
        gson,
        loadable.board,
        post,
        currentTheme,
        archivesManager.getArchiveDescriptorByDatabaseId(post.archiveId)
      )
    }

    return when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> posts
      is ChanDescriptor.CatalogDescriptor -> chanReaderProcessor.getPostsSortedByIndexes(posts)
    }
  }

  private suspend fun parseNewPostsPosts(
    chanDescriptor: ChanDescriptor,
    loadable: Loadable,
    chanReader: ChanReader,
    postBuildersToParse: List<Post.Builder>,
    maxCount: Int
  ): List<Post> {
    BackgroundUtils.ensureBackgroundThread()

    if (verboseLogsEnabled) {
      Logger.d(TAG, "parseNewPostsPosts(chanDescriptor=$chanDescriptor, " +
        "loadable=${loadable.toShortString()}, " +
        "postsToParseSize=${postBuildersToParse.size}, " +
        "maxCount=$maxCount)")
    }

    if (postBuildersToParse.isEmpty()) {
      return emptyList()
    }

    var internalIds = postBuildersToParse
      .map { postBuilder -> postBuilder.id }
      .toSet()

    if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      val archiveId = archivesManager.getLastUsedArchiveForThread(chanDescriptor)?.getArchiveId()
        ?: ArchiveDescriptor.NO_ARCHIVE_ID

      if (archiveId != ArchiveDescriptor.NO_ARCHIVE_ID) {
        internalIds = chanPostRepository.getThreadPostIds(chanDescriptor, archiveId, maxCount)
          .mapErrorToValue { error ->
            Logger.e(TAG, "Error while trying to get post ids for a thread" +
              " (chanDescriptor=$chanDescriptor, archiveId=$archiveId, maxCount=$maxCount)", error)
            return@mapErrorToValue emptySet<Long>()
          }
      }
    }

    return coroutineScope {
      return@coroutineScope postBuildersToParse
        .chunked(POSTS_PER_BATCH)
        .flatMap { postToParseChunk ->
          val deferred = postToParseChunk.map { postToParse ->
            return@map async(dispatcher) {
              return@async PostParseWorker(
                filterEngine,
                databaseSavedReplyManager,
                currentTheme,
                loadFilters(loadable),
                postToParse,
                chanReader,
                internalIds
              ).parse()
            }
          }

          return@flatMap deferred.awaitAll().filterNotNull()
        }
    }
  }

  private fun loadFilters(loadable: Loadable): List<Filter> {
    BackgroundUtils.ensureBackgroundThread()

    return filterEngine.enabledFilters
      .filter { filter -> filterEngine.matchesBoard(filter, loadable.board) }
      // copy the filter because it will get used on other threads
      .map { filter -> filter.clone() }
  }

  private fun processPosts(
    op: Post.Builder,
    allPosts: List<Post>,
    requestParams: ChanLoaderRequestParams
  ): ChanLoaderResponse {
    BackgroundUtils.ensureBackgroundThread()

    val cachedPosts = ArrayList<Post>()
    val newPosts = ArrayList<Post>()
    val loadable = requestParams.loadable
    val cachedPostsMap = requestParams.cached.associateBy { post -> post.no }.toMutableMap()

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
      fillInReplies(totalPosts)
    }

    val response = ChanLoaderResponse(op, totalPosts.toList())
    response.preloadPostsInfo()

    return response
  }

  private fun fillInReplies(totalPosts: List<Post>) {
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

  companion object {
    private const val TAG = "ChanLoaderRequestExecutor"
    private const val threadFactoryName = "post_parser_thread_%d"

    private val POSTS_PER_BATCH = 16

    private val THREAD_COUNT = Runtime.getRuntime().availableProcessors()
    private val threadIndex = AtomicInteger(0)
    private val dispatcher: CoroutineDispatcher

    init {
      Logger.d(TAG, "Thread count: $THREAD_COUNT")

      val executor = Executors.newFixedThreadPool(THREAD_COUNT) { runnable ->
        val threadName = String.format(
          Locale.ENGLISH,
          threadFactoryName,
          threadIndex.getAndIncrement()
        )

        return@newFixedThreadPool Thread(runnable, threadName)
      }

      dispatcher = executor.asCoroutineDispatcher()
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