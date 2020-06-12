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
package com.github.adamantcheese.chan.core.site.loader

import android.util.JsonReader
import com.github.adamantcheese.chan.core.database.DatabaseSavedReplyManager
import com.github.adamantcheese.chan.core.di.NetModule
import com.github.adamantcheese.chan.core.manager.ArchivesManager
import com.github.adamantcheese.chan.core.manager.FilterEngine
import com.github.adamantcheese.chan.core.manager.PostFilterManager
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.core.site.loader.internal.ArchivePostLoader
import com.github.adamantcheese.chan.core.site.loader.internal.DatabasePostLoader
import com.github.adamantcheese.chan.core.site.loader.internal.NormalPostLoader
import com.github.adamantcheese.chan.core.site.loader.internal.Utils
import com.github.adamantcheese.chan.core.site.loader.internal.usecase.GetPostsFromArchiveUseCase
import com.github.adamantcheese.chan.core.site.loader.internal.usecase.ParsePostsUseCase
import com.github.adamantcheese.chan.core.site.loader.internal.usecase.ReloadPostsFromDatabaseUseCase
import com.github.adamantcheese.chan.core.site.loader.internal.usecase.StorePostsInRepositoryUseCase
import com.github.adamantcheese.chan.core.site.parser.ChanReaderProcessor
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.DescriptorUtils.getDescriptor
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.errorMessageOrClassName
import com.github.adamantcheese.common.AppConstants
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.ModularResult.Companion.Try
import com.github.adamantcheese.common.suspendCall
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.repository.ChanPostRepository
import com.github.adamantcheese.model.repository.ThirdPartyArchiveInfoRepository
import com.google.gson.Gson
import kotlinx.coroutines.*
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

/**
 * This class is kinda over complicated right now. It does way too much stuff. It tries to load the
 * catalog/thread json from the network as well as thread json from third-party archives (only
 * for 4chan). It automatically redirects you to an archived thread in case of original thread getting
 * 404ed. It automatically loads cached posts from the database when it was impossible to load posts
 * from the network. All of that stuff should be separated into their own classes some time in the
 * future. For now it will stay the way it is.
 * */
class ChanThreadLoaderCoordinator(
  private val gson: Gson,
  private val okHttpClient: NetModule.ProxiedOkHttpClient,
  private val databaseSavedReplyManager: DatabaseSavedReplyManager,
  private val filterEngine: FilterEngine,
  private val chanPostRepository: ChanPostRepository,
  private val appConstants: AppConstants,
  private val archivesManager: ArchivesManager,
  private val thirdPartyArchiveInfoRepository: ThirdPartyArchiveInfoRepository,
  private val postFilterManager: PostFilterManager,
  private val verboseLogsEnabled: Boolean,
  private val themeHelper: ThemeHelper
) : CoroutineScope {
  private val job = SupervisorJob()

  override val coroutineContext: CoroutineContext
    get() = Dispatchers.IO + job + CoroutineName("ChanReaderRequest")

  private val reloadPostsFromDatabaseUseCase by lazy {
    ReloadPostsFromDatabaseUseCase(
      gson,
      archivesManager,
      chanPostRepository,
      themeHelper
    )
  }

  private val parsePostsUseCase by lazy {
    ParsePostsUseCase(
      verboseLogsEnabled,
      dispatcher,
      archivesManager,
      chanPostRepository,
      filterEngine,
      postFilterManager,
      databaseSavedReplyManager,
      themeHelper
    )
  }

  private val getPostsFromArchiveUseCase by lazy {
    GetPostsFromArchiveUseCase(
      verboseLogsEnabled,
      archivesManager,
      thirdPartyArchiveInfoRepository,
      chanPostRepository
    )
  }

  private val storePostsInRepositoryUseCase by lazy {
    StorePostsInRepositoryUseCase(
      gson,
      chanPostRepository
    )
  }

  private val archivePostLoader by lazy {
    ArchivePostLoader(
      parsePostsUseCase,
      getPostsFromArchiveUseCase,
      storePostsInRepositoryUseCase,
      archivesManager
    )
  }

  private val normalPostLoader by lazy {
    NormalPostLoader(
      appConstants,
      getPostsFromArchiveUseCase,
      parsePostsUseCase,
      storePostsInRepositoryUseCase,
      reloadPostsFromDatabaseUseCase,
      chanPostRepository
    )
  }

  private val databasePostLoader by lazy { DatabasePostLoader(reloadPostsFromDatabaseUseCase) }

  fun loadThread(
    url: String,
    requestParams: ChanLoaderRequestParams,
    resultCallback: (ModularResult<ThreadLoadResult>) -> Unit
  ): Job {
    return launch {
      BackgroundUtils.ensureBackgroundThread()

      val result = Try {
        val request = Request.Builder()
          .url(url)
          .get()
          .build()

        val response = try {
          okHttpClient.suspendCall(request)
        } catch (error: IOException) {
          return@Try fallbackPostLoadOnNetworkError(requestParams, error)
        }

        val descriptor = getDescriptor(requestParams.loadable)
        val archiveDescriptor = Utils.getArchiveDescriptor(archivesManager, descriptor, requestParams)

        if (!response.isSuccessful) {
          if (response.code == NOT_FOUND) {
            return@Try fallbackPostLoadWhenThreadIsDead(
              descriptor,
              requestParams,
              response.code
            )
          }

          throw ServerException(response.code)
        }

        val chanReaderProcessor = readPostsFromResponse(response, requestParams)
          .unwrap()

        return@Try normalPostLoader.loadPosts(
          chanReaderProcessor,
          requestParams,
          descriptor,
          archiveDescriptor
        )
      }.mapError { error -> ChanLoaderException(error) }

      resultCallback.invoke(result)
    }
  }

  private suspend fun fallbackPostLoadWhenThreadIsDead(
    descriptor: ChanDescriptor,
    requestParams: ChanLoaderRequestParams,
    responseCode: Int
  ): ThreadLoadResult {
    archivePostLoader.updateThreadPostsFromArchiveIfNeeded(descriptor, requestParams)
      .mapErrorToValue { error ->
        Logger.e(TAG, "Error updating thread posts from archive", error)
      }

    val chanLoaderResponse = databasePostLoader.loadPosts(requestParams)
      ?: throw ServerException(responseCode)

    Logger.d(TAG, "Successfully recovered from 404 error")
    return ThreadLoadResult.LoadedFromArchive(chanLoaderResponse)
  }

  private suspend fun fallbackPostLoadOnNetworkError(
    requestParams: ChanLoaderRequestParams,
    error: IOException
  ): ThreadLoadResult {
    val chanLoaderResponse = databasePostLoader.loadPosts(requestParams)
      ?: throw error

    Logger.d(TAG, "Successfully recovered from network error (${error.errorMessageOrClassName()})")
    return ThreadLoadResult.LoadedFromDatabaseCopy(chanLoaderResponse)
  }

  private suspend fun readPostsFromResponse(
    response: Response,
    requestParams: ChanLoaderRequestParams
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
              requestParams.loadable
            )

            val loadable = requestParams.loadable
            val reader = requestParams.chanReader

            when {
              loadable.isThreadMode -> reader.loadThread(jsonReader, chanReaderProcessor)
              loadable.isCatalogMode -> reader.loadCatalog(jsonReader, chanReaderProcessor)
              else -> throw IllegalArgumentException("Unknown mode")
            }

            return@use chanReaderProcessor
          }
      }
    }
  }

  companion object {
    private const val TAG = "ChanLoaderRequestExecutor"
    private const val threadFactoryName = "post_parser_thread_%d"
    private const val NOT_FOUND = 404

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