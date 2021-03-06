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

import com.github.k1rakishou.chan.core.base.okhttp.CloudFlareHandlerInterceptor
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.helper.ChanLoadProgressEvent
import com.github.k1rakishou.chan.core.helper.ChanLoadProgressNotifier
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
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
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.BadStatusResponseException
import com.github.k1rakishou.common.EmptyBodyResponseException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.options.ChanCacheOptions
import com.github.k1rakishou.model.data.options.ChanCacheUpdateOptions
import com.github.k1rakishou.model.data.options.ChanLoadOptions
import com.github.k1rakishou.model.data.options.ChanReadOptions
import com.github.k1rakishou.model.data.options.PostsToReloadOptions
import com.github.k1rakishou.model.repository.ChanCatalogSnapshotRepository
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import java.io.InputStream
import java.util.*
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
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
  private val siteResolver: SiteResolver,
  private val chanLoadProgressNotifier: ChanLoadProgressNotifier,
  private val chanThreadsCache: ChanThreadsCache,
  private val threadDownloadManager: ThreadDownloadManager,
  private val parsePostsV1UseCase: ParsePostsV1UseCase
) : CoroutineScope {
  private val job = SupervisorJob()

  override val coroutineContext: CoroutineContext
    get() = Dispatchers.IO + job + CoroutineName("ChanThreadLoaderCoordinator")

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
      siteManager,
      parsePostsV1UseCase,
      storePostsInRepositoryUseCase,
      chanPostRepository,
      chanCatalogSnapshotRepository,
      chanLoadProgressNotifier
    )
  }

  private val databasePostLoader by lazy {
    DatabasePostLoader(
      reloadPostsFromDatabaseUseCase,
      chanCatalogSnapshotRepository
    )
  }

  @OptIn(ExperimentalTime::class)
  suspend fun loadThreadOrCatalog(
    site: Site,
    chanDescriptor: ChanDescriptor,
    chanCacheOptions: ChanCacheOptions,
    cacheUpdateOptions: ChanCacheUpdateOptions,
    chanReadOptions: ChanReadOptions,
    chanLoadOptions: ChanLoadOptions,
    chanReaderProcessorOptions: ChanReaderProcessor.Options
  ): ModularResult<ThreadLoadResult> {
    threadDownloadManager.awaitUntilInitialized()

    val chanLoadUrl = getChanUrl(site, chanDescriptor)
    val chanReader = site.chanReader()

    Logger.d(TAG, "loadThreadOrCatalog(chanLoadUrl=$chanLoadUrl, chanDescriptor=$chanDescriptor, " +
      "chanCacheOptions=$chanCacheOptions, chanReadOptions=$chanReadOptions, " +
      "chanReader=${chanReader.javaClass.simpleName})")

    return withContext(Dispatchers.IO) {
      BackgroundUtils.ensureBackgroundThread()

      val isThreadDownloaded = chanDescriptor is ChanDescriptor.ThreadDescriptor
        && threadDownloadManager.isThreadFullyDownloaded(chanDescriptor)

      return@withContext Try {
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

        chanLoadProgressNotifier.sendProgressEvent(ChanLoadProgressEvent.LoadingJson(chanDescriptor))

        val (response, requestDuration) = try {
          measureTimedValue { proxiedOkHttpClient.okHttpClient().suspendCall(requestBuilder.build()) }
        } catch (error: Throwable) {
          if (error is CloudFlareHandlerInterceptor.CloudFlareDetectedException) {
            throw error
          }

          return@Try fallbackPostLoadOnNetworkError(
            chanLoadUrl = chanLoadUrl,
            chanDescriptor = chanDescriptor,
            error = error,
            isThreadDownloaded = isThreadDownloaded
          )
        }

        if (!response.isSuccessful) {
          return@Try fallbackPostLoadOnNetworkError(
            chanLoadUrl = chanLoadUrl,
            chanDescriptor = chanDescriptor,
            error = BadStatusResponseException(response.code),
            isThreadDownloaded = isThreadDownloaded
          )
        }

        chanLoadProgressNotifier.sendProgressEvent(ChanLoadProgressEvent.ReadingJson(chanDescriptor))

        val (chanReaderProcessor, readPostsDuration) = measureTimedValue {
          val body = response.body
            ?: throw EmptyBodyResponseException()

          return@measureTimedValue body.byteStream().use { inputStream ->
            return@use readPostsFromResponse(
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

        if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
          chanPostRepository.updateThreadState(
            threadDescriptor = chanDescriptor,
            deleted = chanReaderProcessor.deleted && !isThreadDownloaded,
            archived = chanReaderProcessor.archived || isThreadDownloaded,
            closed = chanReaderProcessor.closed
          )
        }

        if (chanReaderProcessor.error != null) {
          when (val error = chanReaderProcessor.error!!) {
            is SiteSpecificError.ErrorCode -> {
              throw SiteError(error.errorCode, error.errorMessage)
            }
            else -> error("Unknown error: ${error}")
          }
        }

        val postParser = chanReader.getParser()
          ?: throw NullPointerException("PostParser cannot be null!")

        val (threadLoadResult, loadTimeInfo) = chanPostPersister.persistPosts(
          chanReaderProcessor = chanReaderProcessor,
          cacheOptions = chanCacheOptions,
          cacheUpdateOptions = cacheUpdateOptions,
          chanDescriptor = chanDescriptor,
          postParser = postParser
        )

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

      appendLine("Threads with more than one post " +
        "count: ($threadsWithMoreThanOnePostCount/${ChanThreadsCache.IMMUNE_THREADS_COUNT}), " +
        "total cached threads count: ${cachedThreadsCount}.")

      val fullLocalDuration = readPostsDuration + storeDuration + parsingDuration + filterProcessingDuration
      appendLine("Total local processing time: $fullLocalDuration")
    }

    Logger.d(TAG, logString)
  }

  suspend fun reloadAndReparseThreadPosts(
    postParser: PostParser,
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    cacheUpdateOptions: ChanCacheUpdateOptions,
    postsToReloadOptions: PostsToReloadOptions
  ): ModularResult<ThreadLoadResult> {
    Logger.d(TAG, "reloadAndReparseThread($threadDescriptor, $cacheUpdateOptions, $postsToReloadOptions)")

    return withContext(Dispatchers.IO) {
      return@withContext Try {
        val chanPostBuilders = chanPostRepository.getPostBuilders(threadDescriptor, postsToReloadOptions)
          .unwrap()

        if (chanPostBuilders.isEmpty()) {
          return@Try ThreadLoadResult.Error(ChanLoaderException.cacheIsEmptyException(threadDescriptor))
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
          chanReaderProcessor = chanReaderProcessor,
          cacheOptions = ChanCacheOptions.onlyCacheInMemory(),
          cacheUpdateOptions = cacheUpdateOptions,
          chanDescriptor = threadDescriptor,
          postParser = postParser
        )

        return@Try threadLoadResult
      }
    }
  }

  suspend fun reloadCatalogFromDatabase(
    catalogDescriptor: ChanDescriptor.CatalogDescriptor
  ): ModularResult<ThreadLoadResult> {
    Logger.d(TAG, "reloadCatalogFromDatabase($catalogDescriptor)")

    return withContext(Dispatchers.IO) {
      return@withContext Try {
        databasePostLoader.loadCatalog(catalogDescriptor)
        return@Try ThreadLoadResult.Loaded(catalogDescriptor)
      }
    }
  }

  private suspend fun fallbackPostLoadOnNetworkError(
    chanLoadUrl: ChanLoadUrl,
    chanDescriptor: ChanDescriptor,
    error: Throwable,
    isThreadDownloaded: Boolean
  ): ThreadLoadResult {
    BackgroundUtils.ensureBackgroundThread()

    val isThreadDeleted = (error is BadStatusResponseException && error.status == 404) && !isThreadDownloaded
    val isThreadArchived = (error is BadStatusResponseException && error.status == 404) && isThreadDownloaded

    if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      Logger.d(TAG, "fallbackPostLoadOnNetworkError(chanLoadUrl='${chanLoadUrl}') " +
        "isThreadDownloaded={$isThreadDownloaded}, isThreadDeleted=${isThreadDeleted}, " +
        "isThreadArchived=${isThreadArchived}, error=${error.errorMessageOrClassName()}")

      chanPostRepository.updateThreadState(
        threadDescriptor = chanDescriptor,
        archived = isThreadArchived,
        deleted = isThreadDeleted
      )
    }

    val chanLoaderResponse = databasePostLoader.loadPosts(chanDescriptor)
    if (chanLoaderResponse == null) {
      throw error
    }

    Logger.e(TAG, "Successfully recovered from network error (${error.errorMessageOrClassName()})")
    return ThreadLoadResult.Loaded(chanDescriptor)
  }

  suspend fun readPostsFromResponse(
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
        chanPostRepository,
        chanReadOptions,
        chanLoadOptions,
        chanReaderProcessorOptions,
        chanDescriptor
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
        else -> throw IllegalArgumentException("Unknown mode")
      }

      return@Try chanReaderProcessor
    }
  }

  fun getChanUrl(site: Site, chanDescriptor: ChanDescriptor, forceFullLoad: Boolean = false): ChanLoadUrl {
    val isThreadCached = if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      chanThreadsCache.getThreadPostsCount(chanDescriptor) > 1
    } else {
      false
    }

    if (forceFullLoad || !isThreadCached || chanDescriptor is ChanDescriptor.CatalogDescriptor) {
      return getChanUrlFullLoad(site, chanDescriptor)
    }

    val threadDescriptor = chanDescriptor as ChanDescriptor.ThreadDescriptor

    val lastPost = chanThreadsCache.getLastPost(threadDescriptor)
    if (lastPost == null) {
      return getChanUrlFullLoad(site, chanDescriptor)
    }

    val threadPartialLoadUrl = getChanUrlIncrementalLoad(site, threadDescriptor, lastPost.postDescriptor)
    if (threadPartialLoadUrl == null) {
      // Not supported by the site
      return getChanUrlFullLoad(site, chanDescriptor)
    }

    return threadPartialLoadUrl
  }

  private fun getChanUrlFullLoad(site: Site, chanDescriptor: ChanDescriptor): ChanLoadUrl {
    val url = when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> site.endpoints().thread(chanDescriptor)
      is ChanDescriptor.CatalogDescriptor -> site.endpoints().catalog(chanDescriptor.boardDescriptor)
      else -> throw IllegalArgumentException("Unknown mode")
    }

    return ChanLoadUrl(url, isIncremental = false)
  }

  private fun getChanUrlIncrementalLoad(
    site: Site,
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    postDescriptor: PostDescriptor
  ): ChanLoadUrl? {
    check(threadDescriptor == postDescriptor.threadDescriptor()) {
      "ThreadDescriptor ($threadDescriptor) differs from descriptor in this PostDescriptor ($postDescriptor)"
    }

    val incrementalLoadUrl = site.endpoints().threadPartial(postDescriptor)
    if (incrementalLoadUrl == null) {
      return null
    }

    return ChanLoadUrl(incrementalLoadUrl, isIncremental = true)
  }

  data class ChanLoadUrl(
    val url: HttpUrl,
    val isIncremental: Boolean
  ) {
    val urlString by lazy { url.toString() }
  }

  companion object {
    private const val TAG = "ChanThreadLoaderCoordinator"
  }

}