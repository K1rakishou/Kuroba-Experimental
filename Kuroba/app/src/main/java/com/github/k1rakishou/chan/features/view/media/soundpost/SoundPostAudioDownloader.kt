package com.github.k1rakishou.chan.features.view.media.soundpost

import com.github.k1rakishou.chan.core.base.okhttp.DownloaderOkHttpClient
import com.github.k1rakishou.chan.core.base.okhttp.interceptor.KurobaOkHttpInterceptor
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class SoundPostAudioDownloader(
  private val appConstants: AppConstants,
  private val cacheHandlerLazy: Lazy<CacheHandler>,
  private val downloaderOkHttpClientLazy: Lazy<DownloaderOkHttpClient>
) {
  private val cacheHandler: CacheHandler
    get() = cacheHandlerLazy.get()

  private val perUrlMutexes = ConcurrentHashMap<String, Mutex>()

  private val anonymousOkHttpClient: OkHttpClient by lazy {
    val builder = downloaderOkHttpClientLazy.get().okHttpClient().newBuilder()
      .proxy(Proxy.NO_PROXY)
      .cookieJar(CookieJar.NO_COOKIES)
      .readTimeout(30, TimeUnit.SECONDS)

    builder.interceptors().removeAll { interceptor -> interceptor is KurobaOkHttpInterceptor }
    builder.networkInterceptors().removeAll { interceptor -> interceptor is KurobaOkHttpInterceptor }

    return@lazy builder.build()
  }

  suspend fun download(
    url: HttpUrl,
    onProgress: (Float?) -> Unit
  ): File {
    return withContext(Dispatchers.IO) {
      val urlString = url.toString()
      val mutex = perUrlMutexes.getOrPut(urlString) { Mutex() }

      // The same sound may be linked in multiple posts, don't download it concurrently
      return@withContext mutex.withLock {
        val cacheFile = cacheHandler.getOrCreateCacheFile(CacheFileType.PostMediaFull, urlString)
          ?: throw IOException("Failed to create cache file for '${url}'")

        if (cacheHandler.isAlreadyDownloaded(CacheFileType.PostMediaFull, cacheFile)) {
          Logger.d(TAG, "download('${url}') already downloaded")
          return@withLock cacheFile
        }

        try {
          downloadInto(url, cacheFile, onProgress)

          if (!cacheHandler.markFileDownloaded(CacheFileType.PostMediaFull, cacheFile)) {
            throw IOException("Failed to mark file '${cacheFile.absolutePath}' as downloaded")
          }

          cacheHandler.fileWasAdded(CacheFileType.PostMediaFull, cacheFile.length())
          Logger.d(TAG, "download('${url}') success, size: ${cacheFile.length()}")

          return@withLock cacheFile
        } catch (error: Throwable) {
          cacheHandler.deleteCacheFile(CacheFileType.PostMediaFull, cacheFile)
          throw error
        }
      }
    }
  }

  private suspend fun downloadInto(
    url: HttpUrl,
    outputFile: File,
    onProgress: (Float?) -> Unit
  ) {
    val request = Request.Builder()
      .url(url)
      .get()
      .header("User-Agent", appConstants.userAgentMightBeOverridden)
      .build()

    anonymousOkHttpClient.suspendCall(request).use { response ->
      if (!response.isSuccessful) {
        throw IOException("Bad response code: ${response.code}")
      }

      val body = response.body
      if (body.contentType()?.type == "text") {
        // Most likely an error page
        throw IOException("Unexpected content type: ${body.contentType()}")
      }

      val contentLength = body.contentLength()
      if (contentLength > MAX_SOUND_FILE_SIZE) {
        throw IOException("Sound file is too big: ${contentLength} bytes")
      }

      onProgress(if (contentLength > 0) 0f else null)

      body.byteStream().use { inputStream ->
        outputFile.outputStream().use { outputStream ->
          val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
          var totalRead = 0L
          var lastReportedPercent = 0

          while (true) {
            currentCoroutineContext().ensureActive()

            val read = inputStream.read(buffer)
            if (read < 0) {
              break
            }

            outputStream.write(buffer, 0, read)
            totalRead += read

            if (totalRead > MAX_SOUND_FILE_SIZE) {
              throw IOException("Sound file is too big: more than ${MAX_SOUND_FILE_SIZE} bytes")
            }

            if (contentLength > 0) {
              val progress = (totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
              val percent = (progress * 100f).toInt()

              if (percent != lastReportedPercent) {
                lastReportedPercent = percent
                onProgress(progress)
              }
            }
          }
        }
      }
    }
  }

  companion object {
    private const val TAG = "SoundPostAudioDownloader"
    private const val MAX_SOUND_FILE_SIZE = 100L * 1024 * 1024
  }
}
