package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.loader.ChanThreadLoaderCoordinator
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.ParsePostsV1UseCase
import com.github.k1rakishou.chan.core.site.parser.processor.ChanReaderProcessor
import com.github.k1rakishou.common.EmptyBodyResponseException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.options.ChanLoadOptions
import com.github.k1rakishou.model.data.options.ChanReadOptions
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.repository.ChanPostRepository
import dagger.Lazy
import okhttp3.Request
import kotlin.time.ExperimentalTime

class ThreadDownloaderPersistPostsInDatabaseUseCase(
  private val siteManager: SiteManager,
  private val chanThreadLoaderCoordinator: Lazy<ChanThreadLoaderCoordinator>,
  private val parsePostsV1UseCase: ParsePostsV1UseCase,
  private val chanPostRepository: ChanPostRepository,
  private val proxiedOkHttpClient: RealProxiedOkHttpClient
) : ISuspendUseCase<DownloadParams, ModularResult<DownloadResult>> {

  override suspend fun execute(parameter: DownloadParams): ModularResult<DownloadResult> {
    val ownerThreadDatabaseId = parameter.ownerThreadDatabaseId
    val threadDescriptor = parameter.threadDescriptor

    return ModularResult.Try {
      downloadThreadPosts(
        ownerThreadDatabaseId = ownerThreadDatabaseId,
        threadDescriptor = threadDescriptor,
        isReloadingAfter404 = false
      )
    }
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun downloadThreadPosts(
    ownerThreadDatabaseId: Long,
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    isReloadingAfter404: Boolean
  ): DownloadResult {
    Logger.d(TAG, "downloadThreadPosts($ownerThreadDatabaseId, $threadDescriptor, $isReloadingAfter404)")

    val site = siteManager.bySiteDescriptor(threadDescriptor.siteDescriptor())
      ?: throw ThreadDownloadException("No site found by siteDescriptor ${threadDescriptor.siteDescriptor()}")

    val chanLoadUrl = chanThreadLoaderCoordinator.get().getChanUrl(
      site = site,
      chanDescriptor = threadDescriptor,
      page = null,
      postProcessFlags = ChanThreadLoaderCoordinator.PostProcessFlags(
        reloadingAfter404 = isReloadingAfter404
      ),
      forceFullLoad = true
    )

    val requestBuilder = Request.Builder()
      .url(chanLoadUrl.url)
      .get()

    site.requestModifier().modifyCatalogOrThreadGetRequest(
      site = site,
      chanDescriptor = threadDescriptor,
      requestBuilder = requestBuilder
    )

    val response = proxiedOkHttpClient.okHttpClient().suspendCall(requestBuilder.build())
    if (!response.isSuccessful) {
      if (response.code == 404) {
        chanPostRepository.updateThreadState(
          threadDescriptor = threadDescriptor,
          deleted = true
        )

        if (!isReloadingAfter404 && site.redirectsToArchiveThread()) {
          // Fix for 2ch.hk archived threads
          return downloadThreadPosts(
            ownerThreadDatabaseId = ownerThreadDatabaseId,
            threadDescriptor = threadDescriptor,
            isReloadingAfter404 = true
          )
        }

        return DownloadResult(
          deleted = true,
          closed = false,
          archived = false,
          posts = emptyList()
        )
      }

      throw ThreadDownloadException("Bad response code for '${chanLoadUrl.url}', code: ${response.code}")
    }

    val body = response.body
      ?: throw EmptyBodyResponseException()

    val chanReader = site.chanReader()

    val chanReaderProcessor = body.byteStream().use { inputStream ->
      return@use chanThreadLoaderCoordinator.get().readPostsFromResponse(
        page = null,
        chanLoadUrl = chanLoadUrl,
        responseBodyStream = inputStream,
        chanDescriptor = threadDescriptor,
        chanReadOptions = ChanReadOptions.default(),
        chanLoadOptions = ChanLoadOptions.retainAll(),
        chanReaderProcessorOptions = ChanReaderProcessor.Options(isDownloadingThread = true),
        chanReader = chanReader
      ).unwrap()
    }

    val postParser = chanReader.getParser()
      ?: throw NullPointerException("PostParser cannot be null!")

    val parsingResult = parsePostsV1UseCase.parseNewPostsPosts(
      chanDescriptor = threadDescriptor,
      postParser = postParser,
      postBuildersToParse = chanReaderProcessor.getToParse()
    )

    chanPostRepository.insertOrUpdatePostsInDatabase(
      ownerThreadDatabaseId,
      parsingResult.parsedPosts
    ).unwrap()

    Logger.d(TAG, "downloadThreadPosts() deleted=${chanReaderProcessor.deleted}, " +
      "closed=${chanReaderProcessor.closed}, " +
      "archived=${chanReaderProcessor.archived}, " +
      "posts=${parsingResult.parsedPosts.size}")

    return DownloadResult(
      deleted = chanReaderProcessor.deleted,
      closed = chanReaderProcessor.closed,
      archived = chanReaderProcessor.archived,
      posts = parsingResult.parsedPosts
    )
  }

  class ThreadDownloadException(message: String) : Exception(message)

  companion object {
    private const val TAG = "ThreadDownloaderPersistPostsInDatabaseUseCase"
  }
}

data class DownloadParams(
  val ownerThreadDatabaseId: Long,
  val threadDescriptor: ChanDescriptor.ThreadDescriptor
)

data class DownloadResult(
  val deleted: Boolean,
  val closed: Boolean,
  val archived: Boolean,
  val posts: List<ChanPost>
)