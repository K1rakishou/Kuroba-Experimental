package com.github.k1rakishou.chan.core.cache.downloader

import com.github.k1rakishou.chan.core.base.okhttp.RealDownloaderOkHttpClient
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import dagger.Lazy
import okhttp3.HttpUrl
import okhttp3.Request

internal class ChunkDownloader(
  private val downloaderOkHttpClientLazy: Lazy<RealDownloaderOkHttpClient>,
  private val siteResolver: SiteResolver,
  private val activeDownloads: ActiveDownloads
) {
  private val downloaderOkHttpClient: RealDownloaderOkHttpClient
    get() = downloaderOkHttpClientLazy.get()

  suspend fun downloadChunk(
    mediaUrl: HttpUrl,
    chunk: Chunk,
    totalChunksCount: Int
  ): ChunkResponse {
    activeDownloads.ensureNotCanceled(mediaUrl)

    if (chunk.isWholeFile() && totalChunksCount > 1) {
      throw IllegalStateException("Bad amount of chunks, should be only one but actual: $totalChunksCount")
    }

    Logger.debug(TAG) {
      "downloadChunk() Start downloading '$mediaUrl', chunk: ${chunk}"
    }

    val requestBuilder = Request.Builder()
      .url(mediaUrl)

    siteResolver.findSiteForUrl(mediaUrl.toString())?.let { site ->
      site.requestModifier().modifyFullImageGetRequest(site, requestBuilder)
    }

    if (!chunk.isWholeFile()) {
      // If chunk.isWholeFile == true that means that either the file size is too small
      // (and there is no reason to download it in chunks (it should be less than
      // [FileCacheV2.MIN_CHUNK_SIZE])) or that the server does not support Partial Content
      // or the user turned off chunked file downloading, or we couldn't send HEAD request
      // (it was timed out) so we should download it normally.
      // In other words, if chunk.isWholeFile == true then we don't use the "Range" header.
      requestBuilder.header("Range", "bytes=" + chunk.start + "-" + chunk.end)
    }

    val httpRequest = requestBuilder.build()
    val startTime = System.currentTimeMillis()

    val response = try {
      downloaderOkHttpClient.okHttpClient().suspendCall(httpRequest)
    } catch (error: Throwable) {
      val diff = System.currentTimeMillis() - startTime
      val exceptionMessage = error.message ?: "No message"

      Logger.debug(TAG) {
        "downloadChunk() Couldn't get chunk response. " +
        "Reason: ${error.javaClass.simpleName}, message: '${exceptionMessage}', " +
        "mediaUrl: '$mediaUrl', chunk: ${chunk}, time: ${diff}ms"
      }

      throw error
    }

    val diff = System.currentTimeMillis() - startTime
    Logger.debug(TAG) { "downloadChunk() Got chunk response in '$mediaUrl' chunk: ${chunk} in ${diff}ms" }

    return ChunkResponse(chunk, response)
  }

  companion object {
    private const val TAG = "ChunkDownloader"
  }
}