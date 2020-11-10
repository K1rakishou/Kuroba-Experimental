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

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.helper.FilterEngine
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.loader.internal.DatabasePostLoader
import com.github.k1rakishou.chan.core.site.loader.internal.NormalPostLoader
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.ParsePostsUseCase
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.ReloadPostsFromDatabaseUseCase
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.StorePostsInRepositoryUseCase
import com.github.k1rakishou.chan.core.site.parser.ChanReader
import com.github.k1rakishou.chan.core.site.parser.ChanReaderProcessor
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.repository.ChanCatalogSnapshotRepository
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.source.cache.ChanCacheOptions
import com.google.gson.stream.JsonReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
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
class ChanThreadLoaderCoordinator(
  private val proxiedOkHttpClient: ProxiedOkHttpClient,
  private val savedReplyManager: SavedReplyManager,
  private val filterEngine: FilterEngine,
  private val chanPostRepository: ChanPostRepository,
  private val chanCatalogSnapshotRepository: ChanCatalogSnapshotRepository,
  private val appConstants: AppConstants,
  private val postFilterManager: PostFilterManager,
  private val verboseLogsEnabled: Boolean,
  private val boardManager: BoardManager
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

  private val parsePostsUseCase by lazy {
    ParsePostsUseCase(
      verboseLogsEnabled,
      dispatcher,
      chanPostRepository,
      filterEngine,
      postFilterManager,
      savedReplyManager,
      boardManager
    )
  }

  private val storePostsInRepositoryUseCase by lazy {
    StorePostsInRepositoryUseCase(
      chanPostRepository
    )
  }

  private val normalPostLoader by lazy {
    NormalPostLoader(
      appConstants,
      parsePostsUseCase,
      storePostsInRepositoryUseCase,
      reloadPostsFromDatabaseUseCase,
      chanPostRepository,
      chanCatalogSnapshotRepository
    )
  }

  private val databasePostLoader by lazy {
    DatabasePostLoader(
      reloadPostsFromDatabaseUseCase
    )
  }

  @OptIn(ExperimentalTime::class)
  suspend fun loadThreadOrCatalog(
    url: String,
    chanDescriptor: ChanDescriptor,
    cacheOptions: ChanCacheOptions,
    chanReader: ChanReader
  ): ModularResult<ThreadLoadResult> {
    Logger.d(TAG, "loadThreadOrCatalog($url, $chanDescriptor, $cacheOptions, ${chanReader.javaClass.simpleName})")

    return withContext(Dispatchers.IO) {
      BackgroundUtils.ensureBackgroundThread()
      chanPostRepository.awaitUntilInitialized()

      return@withContext Try {
        val request = Request.Builder()
          .url(url)
          .get()
          .header("User-Agent", appConstants.userAgent)
          .build()

        val (response, requestDuration) = try {
          measureTimedValue { proxiedOkHttpClient.okHttpClient().suspendCall(request) }
        } catch (error: IOException) {
          return@Try fallbackPostLoadOnNetworkError(chanDescriptor, error)
        }

        Logger.d(TAG, "loadThreadOrCatalog() from network took $requestDuration")

        if (!response.isSuccessful) {
          return@Try fallbackPostLoadOnNetworkError(chanDescriptor, ServerException(response.code))
        }

        val (chanReaderProcessor, readPostsDuration) = measureTimedValue {
          readPostsFromResponse(response, chanDescriptor, chanReader)
            .unwrap()
        }

        Logger.d(TAG, "loadThreadOrCatalog() read posts from response took $readPostsDuration")

        return@Try normalPostLoader.loadPosts(
          url,
          chanReaderProcessor,
          cacheOptions,
          chanDescriptor,
          chanReader
        )
      }.mapError { error -> ChanLoaderException(error) }
    }
  }

  suspend fun reloadThreadFromDatabase(
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ) {
    Logger.d(TAG, "reloadThreadFromDatabase($threadDescriptor)")
    BackgroundUtils.ensureBackgroundThread()

    databasePostLoader.loadPosts(threadDescriptor)
  }

  suspend fun reloadCatalogFromDatabase(
    threadDescriptors: List<ChanDescriptor.ThreadDescriptor>
  ) {
    Logger.d(TAG, "reloadCatalogFromDatabase(${threadDescriptors.size})")
    BackgroundUtils.ensureBackgroundThread()

    databasePostLoader.loadCatalog(threadDescriptors)
  }

  private suspend fun fallbackPostLoadOnNetworkError(
    chanDescriptor: ChanDescriptor,
    error: Exception
  ): ThreadLoadResult {
    BackgroundUtils.ensureBackgroundThread()

    databasePostLoader.loadPosts(chanDescriptor)
      ?: throw error

    val isThreadDeleted = error is ServerException && error.statusCode == 404
    if (isThreadDeleted && chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      chanPostRepository.markThreadAsDeleted(chanDescriptor, true)
    }

    Logger.e(TAG, "Successfully recovered from network error (${error.errorMessageOrClassName()})")
    return ThreadLoadResult.Loaded(chanDescriptor)
  }

  private suspend fun readPostsFromResponse(
    response: Response,
    chanDescriptor: ChanDescriptor,
    chanReader: ChanReader
  ): ModularResult<ChanReaderProcessor> {
    BackgroundUtils.ensureBackgroundThread()

    return Try {
      val body = response.body
        ?: throw IOException("Response has no body")

      return@Try body.byteStream().use { inputStream ->
        return@use JsonReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
          .use { jsonReader ->
            val chanReaderProcessor = ChanReaderProcessor(
              chanPostRepository,
              chanDescriptor
            )

            when (chanDescriptor) {
              is ChanDescriptor.ThreadDescriptor -> chanReader.loadThread(jsonReader, chanReaderProcessor)
              is ChanDescriptor.CatalogDescriptor -> chanReader.loadCatalog(jsonReader, chanReaderProcessor)
              else -> throw IllegalArgumentException("Unknown mode")
            }

            return@use chanReaderProcessor
          }
      }
    }
  }

  companion object {
    private const val TAG = "ChanThreadLoaderCoordinator"
    private const val threadFactoryName = "post_parser_thread_%d"

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
    fun getChanUrl(site: Site, chanDescriptor: ChanDescriptor): HttpUrl {
      return when (chanDescriptor) {
        is ChanDescriptor.ThreadDescriptor -> site.endpoints().thread(chanDescriptor)
        is ChanDescriptor.CatalogDescriptor -> site.endpoints().catalog(chanDescriptor.boardDescriptor)
        else -> throw IllegalArgumentException("Unknown mode")
      }
    }
  }

}