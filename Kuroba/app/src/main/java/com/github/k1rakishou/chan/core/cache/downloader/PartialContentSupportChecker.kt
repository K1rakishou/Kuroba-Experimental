package com.github.k1rakishou.chan.core.cache.downloader

import android.util.LruCache
import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.base.okhttp.RealDownloaderOkHttpClient
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteBase
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.isTimeoutCancellationException
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import dagger.Lazy
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response

/**
 * This class is used to figure out whether an image or a file can be downloaded from the server in
 * separate chunks concurrently using HTTP Partial-Content. For batched image downloading and
 * media prefetching this method returns false because we should download them normally. Chunked
 * downloading should only be used for high priority files/images like in the gallery when the user
 * is viewing them. Everything else should be downloaded in a singe chunk.
 * */
internal class PartialContentSupportChecker(
  private val downloaderOkHttpClientLazy: Lazy<RealDownloaderOkHttpClient>,
  private val activeDownloads: ActiveDownloads,
  private val siteResolver: SiteResolver,
  private val maxTimeoutMs: Long
) {
  // Thread safe
  private val cachedResults = LruCache<HttpUrl, PartialContentCheckResult>(1024)

  @GuardedBy("itself")
  private val checkedChanHosts = mutableMapOf<String, Boolean>()

  private val downloaderOkHttpClient: RealDownloaderOkHttpClient
    get() = downloaderOkHttpClientLazy.get()

  suspend fun check(mediaUrl: HttpUrl): PartialContentCheckResult {
    if (activeDownloads.isBatchDownload(mediaUrl)) {
      return PartialContentCheckResult(false)
    }

    val host = mediaUrl.host
    if (host.isNullOrBlank()) {
      Logger.error(TAG) { "Bad url, can't extract host: '$mediaUrl'" }
      return PartialContentCheckResult(supportsPartialContentDownload = false)
    }

    val site = siteResolver.findSiteForUrl(host) as? SiteBase

    val enabled = site?.getChunkDownloaderSiteProperties()
      ?.enabled
      ?: false

    if (!enabled) {
      // Disabled for this site
      return PartialContentCheckResult(supportsPartialContentDownload = false)
    }

    if (site?.concurrentFileDownloadingChunks?.get()?.chunksCount() == 1) {
      // The setting is set to only use 1 chunk per download
      return PartialContentCheckResult(supportsPartialContentDownload = false)
    }

    val fileSize = activeDownloads.get(mediaUrl)?.extraInfo?.fileSize ?: -1L
    if (fileSize > 0) {
      val hostAlreadyChecked = synchronized(checkedChanHosts) {
        checkedChanHosts.containsKey(host)
      }

      // If a host is already check (we sent HEAD request to it at least 1 time during the app
      // lifetime) we can go a fast route and  just check the cached value (whether the site)
      // supports partial content or not
      if (hostAlreadyChecked) {
        val siteSendsFileSizeInBytes = site
          ?.getChunkDownloaderSiteProperties()
          ?.siteSendsCorrectFileSizeInBytes
          ?: false

        // Some sites may send file size in KBs (2ch.hk does that) so we can't use fileSize
        // that we get with json for such sites and we have to determine the file size
        // by sending HEAD requests every time
        if (siteSendsFileSizeInBytes) {
          val supportsPartialContent = synchronized(checkedChanHosts) {
            checkedChanHosts[host] ?: false
          }

          if (supportsPartialContent) {
            // Fast path: we already had a file size and already checked whether this
            // chan supports Partial Content. So we don't need to send HEAD request.
            return PartialContentCheckResult(
              supportsPartialContentDownload = true,
              // We are not sure about this one but it doesn't matter
              // because we have another similar check in the downloader.
              notFoundOnServer = false,
              length = fileSize
            )
          }

          return PartialContentCheckResult(supportsPartialContentDownload = false)
        }

        // fallthrough
      }

      // fallthrough
    }

    val cached = cachedResults.get(mediaUrl)
    if (cached != null) {
      return cached
    }

    Logger.d(TAG, "Sending HEAD request to url ($mediaUrl)")

    val headRequestBuilder = Request.Builder()
      .head()
      .url(mediaUrl)

    site?.let { it.requestModifier()?.modifyFullImageHeadRequest(it, headRequestBuilder) }

    val headRequest = headRequestBuilder.build()
    val startTime = System.currentTimeMillis()

    try {
      return withTimeout(maxTimeoutMs) {
        val partialContentCheckResult = checkPartialContentSupport(
          headRequest = headRequest,
          mediaUrl = mediaUrl,
          site = site,
          startTime = startTime
        )

        val diff = System.currentTimeMillis() - startTime
        Logger.debug(TAG) {
          "HEAD request to url ($mediaUrl) has succeeded " +
                  "(partialContentCheckResult: ${partialContentCheckResult}), time: ${diff}ms"
        }

        return@withTimeout partialContentCheckResult
      }
    } catch (error: Throwable) {
      if (error.isTimeoutCancellationException()) {
        val diff = System.currentTimeMillis() - startTime
        Logger.error(TAG) {
          "HEAD request to url ($mediaUrl) has failed " +
                  "because of '${error.javaClass.simpleName}' exception, time: ${diff}ms"
        }

        // Do not cache this result because after this request the file should be cached by the
        // cloudflare, so the next time we open it, it should load way faster
        return PartialContentCheckResult(
          supportsPartialContentDownload = false
        )
      }

      throw error
    }
  }

  private suspend fun checkPartialContentSupport(
    headRequest: Request,
    mediaUrl: HttpUrl,
    site: SiteBase?,
    startTime: Long
  ): PartialContentCheckResult {
    val downloadState = activeDownloads.getState(mediaUrl)
    if (downloadState != DownloadState.Running) {
      when (downloadState) {
        DownloadState.Canceled -> activeDownloads.get(mediaUrl)?.cancelableDownload?.cancel()
        DownloadState.Stopped -> activeDownloads.get(mediaUrl)?.cancelableDownload?.stop()
        else -> {
          throw RuntimeException("DownloadState must be either Stopped or Canceled")
        }
      }

      throw MediaDownloadException.CancellationException(downloadState, mediaUrl)
    }

    val response = downloaderOkHttpClient.okHttpClient().suspendCall(headRequest)

    return handleResponse(
      site = site,
      response = response,
      mediaUrl = mediaUrl,
      startTime = startTime
    )
  }

  private fun handleResponse(
    site: Site?,
    response: Response,
    mediaUrl: HttpUrl,
    startTime: Long
  ): PartialContentCheckResult {
    val statusCode = response.code
    if (statusCode == 404) {
      val notFoundOnServer = site?.redirectsToArchiveThread() != true

      // Fast path: the server returned 404 so that mean we don't have to do any other GET
      // requests since the file does not exist
      val result = PartialContentCheckResult(
        supportsPartialContentDownload = false,
        notFoundOnServer = notFoundOnServer
      )

      return cacheAndReturn(mediaUrl, result)
    }

    val acceptsRangesValue = response.header(ACCEPT_RANGES_HEADER)
    if (acceptsRangesValue == null) {
      Logger.debug(TAG) { "($mediaUrl) does not support partial content (ACCEPT_RANGES_HEADER is null)" }
      return cacheAndReturn(mediaUrl, PartialContentCheckResult(false))
    }

    if (!acceptsRangesValue.equals(ACCEPT_RANGES_HEADER_VALUE, true)) {
      Logger.debug(TAG) {
        "($mediaUrl) does not support partial content " +
                "(bad ACCEPT_RANGES_HEADER = ${acceptsRangesValue})"
      }

      return cacheAndReturn(mediaUrl, PartialContentCheckResult(false))
    }

    val contentLengthValue = response.header(CONTENT_LENGTH_HEADER)
    if (contentLengthValue == null) {
      // 8kun doesn't send Content-Length header whatsoever, but it sends correct file size
      // in thread.json. So we can try using that.

      if (!canWeUseFileSizeFromJson(mediaUrl)) {
        Logger.debug(TAG) { "($mediaUrl) does not support partial content (CONTENT_LENGTH_HEADER is null)" }
        return cacheAndReturn(mediaUrl, PartialContentCheckResult(false))
      }
    }

    val length = if (contentLengthValue != null) {
      contentLengthValue.toLongOrNull()
    } else {
      activeDownloads.get(mediaUrl)?.extraInfo?.fileSize ?: -1L
    }

    if (length == null || length <= 0) {
      Logger.debug(TAG) {
        "($mediaUrl) does not support partial content " +
                "(bad CONTENT_LENGTH_HEADER = ${contentLengthValue})"
      }

      return cacheAndReturn(mediaUrl, PartialContentCheckResult(false))
    }

    if (length < ConcurrentChunkedFileDownloader.MIN_CHUNK_SIZE) {
      Logger.debug(TAG) { "($mediaUrl) download file normally (file length < MIN_CHUNK_SIZE, length = $length)" }
      // Download tiny files normally, no need to chunk them
      return cacheAndReturn(mediaUrl, PartialContentCheckResult(false, length = length))
    }

    val cfCacheStatusHeader = response.header(CF_CACHE_STATUS_HEADER)
    val diff = System.currentTimeMillis() - startTime
    Logger.debug(TAG) { "url: '$mediaUrl', fileSize: $length, cfCacheStatusHeader: $cfCacheStatusHeader, took: ${diff}ms" }

    val host = mediaUrl.host
    if (host.isNotNullNorBlank()) {
      synchronized(checkedChanHosts) { checkedChanHosts.put(host, true) }
    }

    val result = PartialContentCheckResult(
      supportsPartialContentDownload = true,
      notFoundOnServer = false,
      length = length
    )

    return cacheAndReturn(mediaUrl, result)
  }

  private fun canWeUseFileSizeFromJson(mediaUrl: HttpUrl): Boolean {
    val fileSize = activeDownloads.get(mediaUrl)?.extraInfo?.fileSize ?: -1L
    if (fileSize <= 0) {
      return false
    }

    val host = mediaUrl.host
    if (host.isNullOrBlank()) {
      Logger.error(TAG) { "Bad url, can't extract host: '$mediaUrl'" }
      return false
    }

    return siteResolver.findSiteForUrl(host)
      ?.getChunkDownloaderSiteProperties()
      ?.siteSendsCorrectFileSizeInBytes
      ?: false
  }

  private fun cacheAndReturn(
    mediaUrl: HttpUrl,
    partialContentCheckResult: PartialContentCheckResult
  ): PartialContentCheckResult {
    cachedResults.put(mediaUrl, partialContentCheckResult)
    return partialContentCheckResult
  }

  /**
   * For tests
   * */
  fun clear() {
    cachedResults.evictAll()
  }

  companion object {
    private const val TAG = "PartialContentSupportChecker"
    private const val ACCEPT_RANGES_HEADER = "Accept-Ranges"
    private const val CONTENT_LENGTH_HEADER = "Content-Length"
    private const val CF_CACHE_STATUS_HEADER = "CF-Cache-Status"
    private const val ACCEPT_RANGES_HEADER_VALUE = "bytes"
  }

}