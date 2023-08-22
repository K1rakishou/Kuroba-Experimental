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
package com.github.k1rakishou.chan.core.site.loader

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.helper.ChanLoadProgressEvent
import com.github.k1rakishou.chan.core.helper.ChanLoadProgressNotifier
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.site.SiteSpecificError
import com.github.k1rakishou.chan.core.site.loader.internal.ChanPostPersister
import com.github.k1rakishou.chan.core.site.loader.internal.DatabasePostLoader
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.ParsePostsV1UseCase
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.ReloadPostsFromDatabaseUseCase
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.StorePostsInRepositoryUseCase
import com.github.k1rakishou.chan.core.site.parser.ChanReader
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.core.site.parser.processor.ChanReaderProcessor
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.BadStatusResponseException
import com.github.k1rakishou.common.EmptyBodyResponseException
import com.github.k1rakishou.common.FirewallDetectedException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.catalog.IChanCatalogSnapshot
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.options.ChanCacheOptions
import com.github.k1rakishou.model.data.options.ChanCacheUpdateOptions
import com.github.k1rakishou.model.data.options.ChanLoadOptions
import com.github.k1rakishou.model.data.options.ChanReadOptions
import com.github.k1rakishou.model.data.options.PostsToReloadOptions
import com.github.k1rakishou.model.repository.ChanCatalogSnapshotRepository
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.source.cache.ChanCatalogSnapshotCache
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
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
class ChanThreadLoaderCoordinator(
  private val proxiedOkHttpClient: ProxiedOkHttpClient,
  private val chanPostRepository: ChanPostRepository,
  private val chanCatalogSnapshotRepository: ChanCatalogSnapshotRepository,
  private val appConstants: AppConstants,
  private val boardManager: BoardManager,
  private val siteResolver: SiteResolver,
  private val chanLoadProgressNotifier: ChanLoadProgressNotifier,
  private val chanThreadsCache: ChanThreadsCache,
  private val chanCatalogSnapshotCache: ChanCatalogSnapshotCache,
  private val threadDownloadManager: ThreadDownloadManager,
  private val parsePostsV1UseCase: ParsePostsV1UseCase
) : CoroutineScope {
  private val job = SupervisorJob()

  override val coroutineContext: CoroutineContext
    get() = Dispatchers.IO + job + CoroutineName("ChanThreadLoaderCoordinator")

  private val lastFullThreadUpdate = ConcurrentHashMap<ChanDescriptor.ThreadDescriptor, Long>(16)

  private val reloadPostsFromDatabaseUseCase by lazy {
    ReloadPostsFromDatabaseUseCase(
      chanPostRepository,
      boardManager
    )
  }

  private val storePostsInRepositoryUseCase by lazy {
    StorePostsInRepositoryUseCase(
      chanPostRepository
    )
  }

  private val chanPostPersister by lazy {
    ChanPostPersister(
      boardManager,
      parsePostsV1UseCase,
      storePostsInRepositoryUseCase,
      chanPostRepository,
      chanCatalogSnapshotRepository,
      chanLoadProgressNotifier,
      chanCatalogSnapshotCache
    )
  }

  private val databasePostLoader by lazy {
    DatabasePostLoader(reloadPostsFromDatabaseUseCase)
  }

  suspend fun loadThreadOrCatalog(
    page: Int?,
    site: Site,
    compositeCatalogDescriptor: ChanDescriptor.CompositeCatalogDescriptor?,
    chanDescriptor: ChanDescriptor,
    chanCacheOptions: ChanCacheOptions,
    chanCacheUpdateOptions: ChanCacheUpdateOptions,
    chanReadOptions: ChanReadOptions,
    chanLoadOptions: ChanLoadOptions,
    postProcessFlags: PostProcessFlags? = null
  ): ModularResult<ThreadLoadResult> {
    return withContext(Dispatchers.IO) {
      return@withContext Try {
        val chanLoadUrl = getChanUrl(
          site = site,
          chanDescriptor = chanDescriptor,
          page = page,
          postProcessFlags = postProcessFlags
        )

        val chanReader = site.chanReader()

        val chanReaderProcessorOptions = ChanReaderProcessor.Options(
          isDownloadingThread = false,
          isIncrementalUpdate = chanLoadUrl.isIncremental
        )

        Logger.d(TAG, "loadThreadOrCatalog(chanLoadUrl=$chanLoadUrl, " +
          "compositeCatalogDescriptor=$compositeCatalogDescriptor, chanDescriptor=$chanDescriptor, " +
          "chanCacheOptions=$chanCacheOptions, chanCacheUpdateOptions=$chanCacheUpdateOptions, " +
          "chanReadOptions=$chanReadOptions, chanReader=${chanReader.javaClass.simpleName}, " +
          "postProcessFlags=$postProcessFlags)")

        val isThreadDownloaded = chanDescriptor is ChanDescriptor.ThreadDescriptor
          && threadDownloadManager.isThreadFullyDownloaded(chanDescriptor)

        if (chanDescriptor is ChanDescriptor.ThreadDescriptor && isThreadDownloaded) {
          chanPostRepository.updateThreadState(chanDescriptor, deleted = false, archived = true)

          val success = databasePostLoader.loadPosts(chanDescriptor) != null
          if (success) {
            return@Try ThreadLoadResult.Loaded(chanDescriptor)
          }

          // fallthrough
        }

        val requestBuilder = Request.Builder()
          .url(chanLoadUrl.url)
          .get()

        siteResolver.findSiteForUrl(chanLoadUrl.urlString)?.let { site ->
          site.requestModifier().modifyCatalogOrThreadGetRequest(
            site = site,
            chanDescriptor = chanDescriptor,
            requestBuilder = requestBuilder
          )
        }

        chanLoadProgressNotifier.sendProgressEvent(ChanLoadProgressEvent.Loading(chanDescriptor))

        val reloadFunc: suspend (error: Throwable) -> ThreadLoadResult = func@ { error: Throwable ->
          return@func fallbackPostLoadOnNetworkError(
            page = page,
            site = site,
            compositeCatalogDescriptor = compositeCatalogDescriptor,
            chanDescriptor = chanDescriptor,
            chanCacheOptions = chanCacheOptions,
            chanCacheUpdateOptions = chanCacheUpdateOptions,
            chanReadOptions = chanReadOptions,
            chanLoadOptions = chanLoadOptions,
            chanLoadUrl = chanLoadUrl,
            error = error,
            isThreadDownloaded = isThreadDownloaded,
            postProcessFlags = postProcessFlags
          )
        }

        val (response, requestDuration) = try {
          measureTimedValue { proxiedOkHttpClient.okHttpClient().suspendCall(requestBuilder.build()) }
        } catch (error: Throwable) {
          if (error is FirewallDetectedException) {
            throw error
          }

          return@Try reloadFunc(error)
        }

        if (!response.isSuccessful) {
          return@Try reloadFunc(BadStatusResponseException(response.code))
        }

        chanLoadProgressNotifier.sendProgressEvent(ChanLoadProgressEvent.Reading(chanDescriptor))

        val (chanReaderProcessor, readPostsDuration) = measureTimedValue {
          val body = response.body
            ?: throw EmptyBodyResponseException()

          return@measureTimedValue body.byteStream().use { inputStream ->
            return@use readPostsFromResponse(
              page = page,
              chanLoadUrl = chanLoadUrl,
              responseBodyStream = inputStream,
              chanDescriptor = chanDescriptor,
              chanReadOptions = chanReadOptions,
              chanLoadOptions = chanLoadOptions,
              chanReaderProcessorOptions = chanReaderProcessorOptions,
              chanReader = chanReader
            ).unwrap()
          }
        }

        Logger.d(TAG, "loadThreadOrCatalog(chanLoadUrl='${chanLoadUrl}') chanReaderProcessor=${chanReaderProcessor}")

        if (chanReaderProcessor.error != null) {
          val error = chanReaderProcessor.error!!
          if (error.isNotFoundError()) {
            return@Try reloadFunc(BadStatusResponseException.notFoundResponse())
          }

          when (error) {
            is SiteSpecificError.DvachError -> {
              throw SiteError(error.errorCode, error.errorMessage)
            }
            else -> error("Unknown error: ${error}")
          }
        }

        val postParser = chanReader.getParser()
          ?: throw NullPointerException("PostParser cannot be null!")

        val (threadLoadResult, loadTimeInfo) = chanPostPersister.persistPosts(
          compositeCatalogDescriptor = compositeCatalogDescriptor,
          chanDescriptor = chanDescriptor,
          chanReaderProcessor = chanReaderProcessor,
          cacheOptions = chanCacheOptions,
          chanCacheUpdateOptions = chanCacheUpdateOptions,
          postParser = postParser,
        )

        if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
          chanPostRepository.updateThreadState(
            threadDescriptor = chanDescriptor,
            deleted = chanReaderProcessor.deleted && !isThreadDownloaded,
            archived = chanReaderProcessor.archived || isThreadDownloaded,
            closed = chanReaderProcessor.closed
          )
        }

        loadRequestStatistics(
          url = chanLoadUrl.url,
          chanDescriptor = chanDescriptor,
          loadTimeInfo = loadTimeInfo,
          requestDuration = requestDuration,
          readPostsDuration = readPostsDuration
        )
        return@Try threadLoadResult
      }.mapError { error -> ChanLoaderException(error) }
    }
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun loadRequestStatistics(
    url: HttpUrl,
    chanDescriptor: ChanDescriptor,
    loadTimeInfo: ChanPostPersister.LoadTimeInfo?,
    requestDuration: Duration,
    readPostsDuration: Duration
  ) {
    if (loadTimeInfo == null) {
      return
    }

    val storeDuration = loadTimeInfo.storeDuration
    val storedPostsCount = loadTimeInfo.storedPostsCount
    val filterProcessingDuration = loadTimeInfo.filterProcessingDuration
    val filtersCount = loadTimeInfo.filtersCount
    val parsingDuration = loadTimeInfo.parsingDuration
    val parsedPostsCount = loadTimeInfo.parsedPostsCount
    val postsInChanReaderProcessor = loadTimeInfo.postsInChanReaderProcessor

    val cachedPostsCount = chanPostRepository.getTotalCachedPostsCount()
    val cachedThreadsCount = chanPostRepository.getTotalCachedThreadCount()
    val threadsWithMoreThanOnePostCount = chanPostRepository.getThreadsWithMoreThanOnePostCount()

    val currentThreadCachedPostsCount = if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      chanPostRepository.getThreadCachedPostsCount(chanDescriptor)
    } else {
      null
    }

    val logString = buildString {
      appendLine("ChanReaderRequest.readJson() stats:")
      appendLine("url = $url.")
      appendLine("Network request execution took $requestDuration.")
      appendLine("Json reading took $readPostsDuration.")
      appendLine("Store new posts took $storeDuration (stored $storedPostsCount posts).")
      appendLine("Parse posts took $parsingDuration (parsed $parsedPostsCount out of $postsInChanReaderProcessor posts).")
      appendLine("Filter processing took $filterProcessingDuration (filers count: $filtersCount).")
      appendLine("Total in-memory cached posts count: $cachedPostsCount/${appConstants.maxPostsCountInPostsCache}.")

      if (currentThreadCachedPostsCount != null) {
        appendLine("Current thread cached posts count: $currentThreadCachedPostsCount")
      }

      val isLowRamDevice = ChanSettings.isLowRamDevice()

      appendLine("Threads with more than one post " +
        "count: ($threadsWithMoreThanOnePostCount/${ChanThreadsCache.immuneThreadsCount(isLowRamDevice)}), " +
        "total cached threads count: ${cachedThreadsCount}.")

      val fullLocalDuration = readPostsDuration + storeDuration + parsingDuration + filterProcessingDuration
      appendLine("Total local processing time: $fullLocalDuration")
    }

    Logger.d(TAG, logString)
  }

  suspend fun reloadAndReparseThreadPosts(
    page: Int?,
    postParser: PostParser,
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    cacheUpdateOptions: ChanCacheUpdateOptions,
    postsToReloadOptions: PostsToReloadOptions
  ): ModularResult<ThreadLoadResult> {
    Logger.d(TAG, "reloadAndReparseThreadPosts($threadDescriptor, $cacheUpdateOptions, $postsToReloadOptions)")

    return withContext(Dispatchers.IO) {
      return@withContext Try {
        val chanPostBuilders = chanPostRepository.getThreadPostBuilders(threadDescriptor, postsToReloadOptions)
          .unwrap()

        if (chanPostBuilders.isEmpty()) {
          return@Try ThreadLoadResult.Error(
            threadDescriptor,
            ChanLoaderException.cacheIsEmptyException(threadDescriptor)
          )
        }

        val chanLoadOptions = when (postsToReloadOptions) {
          is PostsToReloadOptions.Reload -> {
            ChanLoadOptions.forceUpdatePosts(postsToReloadOptions.postDescriptors.toSet())
          }
          PostsToReloadOptions.ReloadAll -> {
            ChanLoadOptions.forceUpdateAllPosts()
          }
        }

        val chanReaderProcessor = ChanReaderProcessor(
          page = page,
          chanPostRepository = chanPostRepository,
          chanReadOptions = ChanReadOptions.default(),
          chanLoadOptions = chanLoadOptions,
          options = ChanReaderProcessor.Options(),
          chanDescriptor = threadDescriptor
        )

        chanPostBuilders.forEach { chanPostBuilder ->
          if (chanPostBuilder.op) {
            chanReaderProcessor.setOp(chanPostBuilder)
          }

          chanReaderProcessor.addPost(chanPostBuilder)
        }

        val (threadLoadResult, _) = chanPostPersister.persistPosts(
          compositeCatalogDescriptor = null,
          chanDescriptor = threadDescriptor,
          chanReaderProcessor = chanReaderProcessor,
          cacheOptions = ChanCacheOptions.onlyCacheInMemory(),
          chanCacheUpdateOptions = cacheUpdateOptions,
          postParser = postParser,
        )

        return@Try threadLoadResult
      }
    }
  }

  suspend fun reloadAndReparseCatalogPosts(
    page: Int?,
    catalogDescriptor: ChanDescriptor.CatalogDescriptor,
    postParser: PostParser
  ): ModularResult<ThreadLoadResult> {
    Logger.d(TAG, "reloadAndReparseCatalogPosts($catalogDescriptor)")

    return withContext(Dispatchers.IO) {
      return@withContext Try {
        val currentCatalogSnapshot = chanCatalogSnapshotRepository.getCatalogSnapshot(catalogDescriptor)
        if (currentCatalogSnapshot == null || currentCatalogSnapshot.isEmpty()) {
          return@Try ThreadLoadResult.Error(
            catalogDescriptor,
            ChanLoaderException.cacheIsEmptyException(catalogDescriptor)
          )
        }

        val chanPostBuilders = chanPostRepository.getCatalogPostBuilders(currentCatalogSnapshot)
          .unwrap()

        if (chanPostBuilders.isEmpty()) {
          return@Try ThreadLoadResult.Error(
            catalogDescriptor,
            ChanLoaderException.cacheIsEmptyException(catalogDescriptor)
          )
        }

        val chanReaderProcessor = ChanReaderProcessor(
          page = page,
          chanPostRepository = chanPostRepository,
          chanReadOptions = ChanReadOptions.default(),
          chanLoadOptions = ChanLoadOptions.forceUpdateAllPosts(),
          options = ChanReaderProcessor.Options(),
          chanDescriptor = catalogDescriptor
        )

        chanPostBuilders.forEach { chanPostBuilder ->
          if (chanPostBuilder.op) {
            chanReaderProcessor.setOp(chanPostBuilder)
          }

          chanReaderProcessor.addPost(chanPostBuilder)
        }

        val (threadLoadResult, _) = chanPostPersister.persistPosts(
          compositeCatalogDescriptor = null,
          chanDescriptor = catalogDescriptor,
          chanReaderProcessor = chanReaderProcessor,
          cacheOptions = ChanCacheOptions.onlyCacheInMemory(),
          chanCacheUpdateOptions =  ChanCacheUpdateOptions.DoNotUpdateCache,
          postParser = postParser,
        )

        return@Try threadLoadResult
      }
    }
  }

  @Suppress("IfThenToElvis")
  private suspend fun fallbackPostLoadOnNetworkError(
    page: Int?,
    site: Site,
    compositeCatalogDescriptor: ChanDescriptor.CompositeCatalogDescriptor?,
    chanDescriptor: ChanDescriptor,
    chanCacheOptions: ChanCacheOptions,
    chanCacheUpdateOptions: ChanCacheUpdateOptions,
    chanReadOptions: ChanReadOptions,
    chanLoadOptions: ChanLoadOptions,
    postProcessFlags: PostProcessFlags? = null,
    chanLoadUrl: ChanLoadUrl,
    error: Throwable,
    isThreadDownloaded: Boolean
  ): ThreadLoadResult {
    BackgroundUtils.ensureBackgroundThread()

    val isThreadDeleted = (error is BadStatusResponseException && error.status == 404) && !isThreadDownloaded

    // Check for null beforehand to avoid infinite recursion
    if (postProcessFlags == null && isThreadDeleted && site.redirectsToArchiveThread()) {
      return loadThreadOrCatalog(
        page = page,
        site = site,
        compositeCatalogDescriptor = compositeCatalogDescriptor,
        chanDescriptor = chanDescriptor,
        chanCacheOptions = chanCacheOptions,
        chanCacheUpdateOptions = chanCacheUpdateOptions,
        chanReadOptions = chanReadOptions,
        chanLoadOptions = chanLoadOptions,
        postProcessFlags = PostProcessFlags(reloadingAfter404 = true)
      ).unwrap()
    }

    val isThreadArchived = (error is BadStatusResponseException && error.status == 404) && isThreadDownloaded
    val catalogSnapshotDescriptor = compositeCatalogDescriptor ?: (chanDescriptor as? ChanDescriptor.ICatalogDescriptor)

    when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        Logger.d(TAG, "fallbackPostLoadOnNetworkError(chanLoadUrl='${chanLoadUrl}') " +
          "isThreadDownloaded={$isThreadDownloaded}, isThreadDeleted=${isThreadDeleted}, " +
          "isThreadArchived=${isThreadArchived}, error=${error.errorMessageOrClassName()}")

        chanPostRepository.updateThreadState(
          threadDescriptor = chanDescriptor,
          archived = isThreadArchived,
          deleted = isThreadDeleted
        )
      }
      is ChanDescriptor.ICatalogDescriptor -> {
        val isNotFoundStatus = (error is BadStatusResponseException && error.status == 404)

        when {
          isNotFoundStatus && catalogSnapshotDescriptor != null -> {
            chanCatalogSnapshotCache.get(catalogSnapshotDescriptor)?.onEndOfUnlimitedCatalogReached()
          }
          compositeCatalogDescriptor != null -> {
            val isLastDescriptor =
              compositeCatalogDescriptor.catalogDescriptors.lastOrNull() == chanDescriptor

            if (isLastDescriptor) {
              chanCatalogSnapshotCache.get(compositeCatalogDescriptor)?.onEndOfUnlimitedCatalogReached()
            }
          }
        }
      }
    }

    val chanLoaderResponse = databasePostLoader.loadPosts(chanDescriptor)
    if (chanLoaderResponse == null || chanLoaderResponse.posts.isEmpty()) {
      throw error
    }

    if (chanDescriptor is ChanDescriptor.ICatalogDescriptor && catalogSnapshotDescriptor != null) {
      val prevCatalogSnapshot = chanCatalogSnapshotCache.get(catalogSnapshotDescriptor)

      val isUnlimitedCatalog = when {
        prevCatalogSnapshot != null -> prevCatalogSnapshot.isUnlimitedOrCompositeCatalog
        chanDescriptor is ChanDescriptor.CatalogDescriptor -> {
          boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor)
            ?.isUnlimitedCatalog
            ?: false
        }
        else -> chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor
      }

      val newCatalogSnapshot = IChanCatalogSnapshot.fromSortedThreadDescriptorList(
        catalogDescriptor = catalogSnapshotDescriptor,
        threadDescriptors = chanLoaderResponse.posts.map { post -> post.postDescriptor.threadDescriptor() },
        isUnlimitedCatalog = isUnlimitedCatalog
      )

      val catalogSnapshot = chanCatalogSnapshotCache.getOrPut(
        key = catalogSnapshotDescriptor,
        valueFunc = {
          IChanCatalogSnapshot.fromSortedThreadDescriptorList(
            catalogDescriptor = catalogSnapshotDescriptor,
            threadDescriptors = emptyList(),
            isUnlimitedCatalog = isUnlimitedCatalog
          )
        }
      )

      catalogSnapshot.mergeWith(newCatalogSnapshot)
    }

    Logger.e(TAG, "Successfully recovered from network error (${error.errorMessageOrClassName()})")
    return ThreadLoadResult.Loaded(chanDescriptor)
  }

  suspend fun readPostsFromResponse(
    page: Int?,
    chanLoadUrl: ChanLoadUrl,
    responseBodyStream: InputStream,
    chanDescriptor: ChanDescriptor,
    chanReadOptions: ChanReadOptions,
    chanLoadOptions: ChanLoadOptions,
    chanReaderProcessorOptions: ChanReaderProcessor.Options,
    chanReader: ChanReader
  ): ModularResult<ChanReaderProcessor> {
    BackgroundUtils.ensureBackgroundThread()

    return Try {
      val chanReaderProcessor = ChanReaderProcessor(
        page = page,
        chanPostRepository = chanPostRepository,
        chanReadOptions = chanReadOptions,
        chanLoadOptions = chanLoadOptions,
        options = chanReaderProcessorOptions,
        chanDescriptor = chanDescriptor
      )

      when (chanDescriptor) {
        is ChanDescriptor.ThreadDescriptor -> {
          if (chanLoadUrl.isIncremental) {
            chanReader.loadThreadIncremental(chanLoadUrl.urlString, responseBodyStream, chanReaderProcessor)
          } else {
            chanReader.loadThreadFresh(chanLoadUrl.urlString, responseBodyStream, chanReaderProcessor)
          }
        }
        is ChanDescriptor.CatalogDescriptor -> {
          chanReader.loadCatalog(chanLoadUrl.urlString, responseBodyStream, chanReaderProcessor)
        }
        is ChanDescriptor.CompositeCatalogDescriptor -> {
          error("Cannot use CompositeCatalogDescriptor here")
        }
        else -> throw IllegalArgumentException("Unknown mode")
      }

      return@Try chanReaderProcessor
    }
  }

  fun getChanUrl(
    site: Site,
    chanDescriptor: ChanDescriptor,
    page: Int?,
    postProcessFlags: PostProcessFlags? = null,
    forceFullLoad: Boolean = false
  ): ChanLoadUrl {
    if (chanDescriptor is ChanDescriptor.ThreadDescriptor && postProcessFlags?.reloadingAfter404 == true) {
      val url = site.endpoints().threadArchive(chanDescriptor)
      if (url != null) {
        return ChanLoadUrl(url = url, isIncremental = false, page = page)
      }
    }

    val isThreadCached = if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      chanThreadsCache.getThreadPostsCount(chanDescriptor) > 1
    } else {
      false
    }

    val currentTime = System.currentTimeMillis()

    if (forceFullLoad || !isThreadCached || chanDescriptor is ChanDescriptor.ICatalogDescriptor) {
      if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
        lastFullThreadUpdate.put(chanDescriptor, currentTime)
      }

      return getChanUrlFullLoad(site, chanDescriptor, page)
    }

    val threadDescriptor = chanDescriptor as ChanDescriptor.ThreadDescriptor

    val lastPost = chanThreadsCache.getLastPost(threadDescriptor)
    if (lastPost == null) {
      lastFullThreadUpdate.put(chanDescriptor, currentTime)
      return getChanUrlFullLoad(site, chanDescriptor, page)
    }

    val threadPartialLoadUrl = getChanUrlIncrementalLoad(site, threadDescriptor, lastPost.postDescriptor)
    if (threadPartialLoadUrl == null) {
      // Not supported by the site
      lastFullThreadUpdate.put(chanDescriptor, currentTime)
      return getChanUrlFullLoad(site, chanDescriptor, page)
    }

    // We want to fully update the threads posts once in a while to check for deleted posts.
    val lastUpdateTime = lastFullThreadUpdate[chanDescriptor] ?: 0
    val timeout = if (isDevBuild()) { ONE_MINUTE } else { THREE_MINUTES }

    if (currentTime - lastUpdateTime > timeout) {
      lastFullThreadUpdate.put(chanDescriptor, currentTime)
      return getChanUrlFullLoad(site, chanDescriptor, page)
    }

    return threadPartialLoadUrl
  }

  private fun getChanUrlFullLoad(
    site: Site,
    chanDescriptor: ChanDescriptor,
    page: Int?
  ): ChanLoadUrl {
    val url = when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        site.endpoints().thread(chanDescriptor)
      }
      is ChanDescriptor.CatalogDescriptor -> {
        var catalog = site.endpoints().catalogPage(chanDescriptor.boardDescriptor, page)
        if (catalog == null) {
          catalog = site.endpoints().catalog(chanDescriptor.boardDescriptor)
        }

        catalog
      }
      is ChanDescriptor.CompositeCatalogDescriptor -> {
        error("Cannot use CompositeCatalogDescriptor here")
      }
      else -> throw IllegalArgumentException("Unknown mode: ${chanDescriptor.javaClass.simpleName}")
    }

    return ChanLoadUrl(url = url, isIncremental = false, page = page)
  }

  private fun getChanUrlIncrementalLoad(
    site: Site,
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    postDescriptor: PostDescriptor
  ): ChanLoadUrl? {
    if (threadDescriptor != postDescriptor.threadDescriptor()) {
      return null
    }

    val incrementalLoadUrl = site.endpoints().threadPartial(postDescriptor)
    if (incrementalLoadUrl == null) {
      return null
    }

    return ChanLoadUrl(url = incrementalLoadUrl, isIncremental = true, page = null)
  }

  data class PostProcessFlags(val reloadingAfter404: Boolean)

  data class ChanLoadUrl(
    val url: HttpUrl,
    // Whether we load only a part of a thread (Like, give me all posts starting with postNo).
    // For now only 2ch.hk supports this.
    val isIncremental: Boolean,
    val page: Int?
  ) {
    val urlString by lazy { url.toString() }
  }

  companion object {
    private const val TAG = "ChanThreadLoaderCoordinator"
    private val THREE_MINUTES = TimeUnit.MINUTES.toMillis(3)
    private val ONE_MINUTE = TimeUnit.MINUTES.toMillis(1)
  }

}