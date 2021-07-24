package com.github.k1rakishou.chan.core.cache.downloader

import com.github.k1rakishou.chan.core.base.okhttp.RealDownloaderOkHttpClient
import com.github.k1rakishou.chan.core.cache.downloader.DownloaderUtils.isCancellationError
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AppConstants
import dagger.Lazy
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

internal class ChunkDownloader(
  private val downloaderOkHttpClient: Lazy<RealDownloaderOkHttpClient>,
  private val siteResolver: SiteResolver,
  private val activeDownloads: ActiveDownloads,
  private val verboseLogs: Boolean,
  private val appConstants: AppConstants
) {

  fun downloadChunk(
    url: String,
    chunk: Chunk,
    totalChunksCount: Int
  ): Flowable<Response> {
    val request = activeDownloads.get(url)
      ?: activeDownloads.throwCancellationException(url)

    if (chunk.isWholeFile() && totalChunksCount > 1) {
      throw IllegalStateException("downloadChunk() Bad amount of chunks, " +
        "should be only one but actual = $totalChunksCount")
    }

    if (verboseLogs) {
      log(TAG, "Start downloading url=$url, chunk ${chunk.start}..${chunk.end}")
    }

    val requestBuilder = Request.Builder()
      .url(url)

    siteResolver.findSiteForUrl(url)?.let { site ->
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

    return Flowable.create<Response>({ emitter ->
      BackgroundUtils.ensureBackgroundThread()

      val serializedEmitter = emitter.serialize()
      val call = downloaderOkHttpClient.get().okHttpClient().newCall(httpRequest)

      // This function will be used to cancel a CHUNK (not the whole file) download upon
      // cancellation
      val disposeFunc = {
        BackgroundUtils.ensureBackgroundThread()

        if (!call.isCanceled()) {
          log(TAG, "Disposing OkHttp Call for CHUNKED request ${request} via " +
              "manual canceling (${chunk.start}..${chunk.end})")

          call.cancel()
        }
      }

      val downloadState = activeDownloads.addDisposeFunc(url, disposeFunc)
      if (downloadState != DownloadState.Running) {
        when (downloadState) {
          DownloadState.Canceled -> activeDownloads.get(url)?.cancelableDownload?.cancel()
          DownloadState.Stopped -> activeDownloads.get(url)?.cancelableDownload?.stop()
          else -> {
            serializedEmitter.tryOnError(
              RuntimeException("DownloadState must be either Stopped or Canceled")
            )
            return@create
          }
        }

        serializedEmitter.tryOnError(
          FileCacheException.CancellationException(
            activeDownloads.getState(url),
            url)
        )
        return@create
      }

      call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
          val diff = System.currentTimeMillis() - startTime
          val exceptionMessage = e.message ?: "No message"

          log(TAG, "Couldn't get chunk response, reason = ${e.javaClass.simpleName} ($exceptionMessage) " +
              "($url) ${chunk.start}..${chunk.end}, time = ${diff}ms")

          if (!isCancellationError(e)) {
            serializedEmitter.tryOnError(e)
          } else {
            serializedEmitter.tryOnError(
              FileCacheException.CancellationException(
                activeDownloads.getState(url),
                url
              )
            )
          }
        }

        override fun onResponse(call: Call, response: Response) {
          if (verboseLogs) {
            val diff = System.currentTimeMillis() - startTime
            log(TAG, "Got chunk response in ($url) ${chunk.start}..${chunk.end} in ${diff}ms")
          }

          serializedEmitter.onNext(response)
          serializedEmitter.onComplete()
        }
      })
    }, BackpressureStrategy.BUFFER)
  }

  companion object {
    private const val TAG = "ChunkDownloader"
  }
}