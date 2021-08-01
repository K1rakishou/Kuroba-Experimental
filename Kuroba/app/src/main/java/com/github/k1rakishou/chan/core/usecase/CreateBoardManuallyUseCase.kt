package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.parser.processor.AbstractChanReaderProcessor
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import okhttp3.Request
import java.io.IOException

class CreateBoardManuallyUseCase(
  private val siteManager: SiteManager,
  private val proxiedOkHttpClient: RealProxiedOkHttpClient
) : ISuspendUseCase<ChanDescriptor.CatalogDescriptor, CreateBoardManuallyUseCase.Result> {

  override suspend fun execute(parameter: ChanDescriptor.CatalogDescriptor): Result {
    BackgroundUtils.ensureBackgroundThread()

    return ModularResult.Try { executeInternal(parameter) }
      .mapErrorToValue { error -> Result.UnknownError(error) }
  }

  private suspend fun executeInternal(catalogDescriptor: ChanDescriptor.CatalogDescriptor): Result {
    val siteDescriptor = catalogDescriptor.siteDescriptor()

    val site = siteManager.bySiteDescriptor(siteDescriptor)
      ?: return Result.FailedToFindSite(siteDescriptor)

    val catalogUrl = site.endpoints().catalog(catalogDescriptor.boardDescriptor, null)

    val request = Request.Builder()
      .url(catalogUrl)
      .get()
      .build()

    val response = proxiedOkHttpClient.okHttpClient().suspendCall(request)

    if (!response.isSuccessful) {
      return Result.BadResponse(response.code)
    }

    val body = response.body
      ?: throw IOException("Response body is null")

    val chanReaderProcessor = SimpleCountingChanReaderProcessor(catalogDescriptor)

    try {
      body.byteStream().use { inputStream ->
        site.chanReader().loadCatalog(request.url.toString(), inputStream, chanReaderProcessor)
      }
    } catch (error: Throwable) {
      Logger.e(TAG, "loadCatalog($siteDescriptor) error", error)
    }

    val totalPostsCount = chanReaderProcessor.getTotalPostsCount()
    if (totalPostsCount <= 0) {
      return Result.FailedToParseAnyThreads(catalogDescriptor)
    }

    return Result.Success(catalogDescriptor)
  }

  class SimpleCountingChanReaderProcessor(
    override val chanDescriptor: ChanDescriptor
  ) : AbstractChanReaderProcessor() {
    private var threadsInCatalogCounter = 0

    override val canUseEmptyBoardIfBoardDoesNotExist: Boolean
      get() = true

    override suspend fun setOp(op: ChanPostBuilder?) {
      // no-op
    }

    override suspend fun addPost(postBuilder: ChanPostBuilder) {
      check(postBuilder.op) { "PostBuilder is not OP!" }
      ++threadsInCatalogCounter
    }

    override suspend fun addManyPosts(postBuilders: List<ChanPostBuilder>) {
      postBuilders.forEach { postBuilder ->
        check(postBuilder.op) { "PostBuilder is not OP!" }
      }

      threadsInCatalogCounter += postBuilders.size
    }

    override suspend fun applyChanReadOptions() {
      // no-op
    }

    override suspend fun getToParse(): List<ChanPostBuilder> {
      return emptyList()
    }

    override suspend fun getThreadDescriptors(): List<ChanDescriptor.ThreadDescriptor> {
      return emptyList()
    }

    override suspend fun getTotalPostsCount(): Int {
      return threadsInCatalogCounter
    }
  }

  sealed class Result {
    class FailedToFindSite(val siteDescriptor: SiteDescriptor) : Result()
    class BadResponse(val code: Int) : Result()
    class UnknownError(val throwable: Throwable) : Result()
    class FailedToParseAnyThreads(val chanDescriptor: ChanDescriptor) : Result()
    class Success(val chanDescriptor: ChanDescriptor) : Result()
  }

  companion object {
    private const val TAG = "CreateBoardManuallyUseCase"
  }
}