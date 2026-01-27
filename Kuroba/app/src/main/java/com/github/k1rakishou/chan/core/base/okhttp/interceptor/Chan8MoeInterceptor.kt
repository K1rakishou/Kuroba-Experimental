package com.github.k1rakishou.chan.core.base.okhttp.interceptor

import androidx.room.concurrent.AtomicBoolean
import com.github.k1rakishou.chan.core.base.okhttp.OkHttpClientForInterceptors
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.site.sites.lynxchan.chan8.Chan8Moe
import com.github.k1rakishou.chan.ui.captcha.lynxchan.pow.Chan8MoeProofOfWork
import com.github.k1rakishou.common.KurobaCookie
import com.github.k1rakishou.common.StringUtils.asFormattedToken
import com.github.k1rakishou.common.isContentTypeTextHtml
import com.github.k1rakishou.common.tryExtractMediaType
import com.github.k1rakishou.core_logger.Logger
import com.google.errorprone.annotations.concurrent.GuardedBy
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import kotlin.time.measureTimedValue


class Chan8MoeInterceptor(
  private val okHttpClient: OkHttpClientForInterceptors,
  private val siteResolver: SiteResolver
) : KurobaOkHttpInterceptor() {
  @GuardedBy("this")
  @Volatile
  private var _latch: CountDownLatch? = null
  private val _needsBypass = AtomicBoolean(true)
  
  private val _noRedirectHttpClient = OkHttpClient.Builder()
    .connectionPool(okHttpClient.okHttpClient().connectionPool)
    .dispatcher(okHttpClient.okHttpClient().dispatcher)
    .followRedirects(false)
    .build()

  override fun intercept(chain: Interceptor.Chain): Response {
    val originalRequest = chain.request()

    if (!_needsBypass.get()) {
      return chain.proceed(originalRequest)
    }

    siteResolver.waitUntilInitialized()

    val chan8Moe = siteResolver.findSiteForUrl(originalRequest.url.toString())
    if (chan8Moe !is Chan8Moe) {
      return chain.proceed(originalRequest)
    }

    if (originalRequest.tag(RequestTag::class) != null) {
      error("Infinite interception loop detected!")
    }

    val (localLatch, acquired) = synchronized(this) {
      if (_latch == null) {
        // Latch is null, we are the first so we need to create the new latch and proceed with POW bypass
        _latch = CountDownLatch(1)
        _latch!! to true
      } else {
        // We are not first, so we can't process the POW bypass and need to wait for the latch
        _latch!! to false
      }
    }

    val threadId = Thread.currentThread().id

    try {
      if (!acquired) {
        // Wait for the first thread to finish POW bypass
        Logger.verbose(TAG) { "[tid: ${threadId}] waiting for latch..." }
        localLatch.await()
        Logger.verbose(TAG) { "[tid: ${threadId}] waiting for latch... done" }

        val updatedRequest = updateRequest(originalRequest, chan8Moe)
        return chain.proceed(updatedRequest)
      }

      if (!tryPass8chanMoePOWBlock(chan8Moe)) {
        // We didn't need to pass the POW block, so just do the original request
        return chain.proceed(originalRequest)
      }

      _needsBypass.set(false)
      Logger.debug(TAG) { "[tid: ${threadId}] successfully performed 8chan.moe POW bypass" }
    } catch (error: Throwable) {
      if (error is IOException && error.message?.equals("Canceled", ignoreCase = true) == true) {
        Logger.error(TAG) { "[tid: ${threadId}] request was canceled" }
        throw error
      } else if (error is SocketTimeoutException) {
        Logger.error(TAG) { "[tid: ${threadId}] request was timed out" }
        throw error
      }

      _needsBypass.set(true)
      Logger.error(TAG, error) { "[tid: ${threadId}] failed to do POW bypass" }
    } finally {
      // Release all the other threads and set latch to null
      synchronized(this) {
        _latch?.countDown()
        _latch = null
      }
    }

    val updatedRequest = updateRequest(originalRequest, chan8Moe)
    return chain.proceed(updatedRequest)
  }

  private fun updateRequest(
    prevRequest: Request,
    chan8Moe: Chan8Moe
  ): Request {
    val requestBuilder = prevRequest.newBuilder()

    // Now perform the same request but with new cookies
    chan8Moe.requestModifier()
      .modifyGenericRequest(chan8Moe, requestBuilder)

    return requestBuilder
      .tag(RequestTag())
      .build()
  }

  @Suppress("ThrowsCount")
  private fun tryPass8chanMoePOWBlock(chan8Moe: Chan8Moe): Boolean {
    val threadId = Thread.currentThread().id
    val siteDescriptor = chan8Moe.siteDescriptor()

    val initialResponse = run {
      // Initial request to check whether or not we need to bypass the POW block
      val initialRequest = Request.Builder()
        .url("${chan8Moe.domainString}/".toHttpUrl())
        .get()

      chan8Moe.requestModifier()
        .modifyGenericRequest(chan8Moe, initialRequest)

      val response = _noRedirectHttpClient.newCall(initialRequest.build()).execute()
      if (!response.isSuccessful) {
        throw InterceptionException("Bad response: ${response.code}, message: '${response.message}'")
      }

      when (val powBlockStatus = response.header("X-PoWBlock-Status")?.lowercase()) {
        null,
        PowBlockStatusCompleted -> {
          Logger.debug(TAG) { "POW bypass is not required for ${siteDescriptor}, exiting" }
          return false
        }
        PowBlockStatusRequired -> {
          _needsBypass.set(true)
          Logger.debug(TAG) { "POW bypass is required for ${siteDescriptor}" }
        }
        else -> {
          throw InterceptionException("Unknown 'X-PoWBlock-Status' value: '${powBlockStatus}'")
        }
      }

      if (!response.isContentTypeTextHtml()) {
        throw InterceptionException("Expected text/html media type but got '${response.tryExtractMediaType()}'")
      }

      return@run response
    }

    chan8Moe.powToken.set(null)
    chan8Moe.powId.set(null)

    val (solution, token) = run {
      val htmlMaybe = initialResponse.body.string()
      val doc = Jsoup.parse(htmlMaybe)

      val tokenElement = doc.select("pre#c").first()
      val difficultyElement = doc.select("pre#d").first()

      val token = tokenElement?.text()
        ?.takeIf { token -> token.isNotBlank() }
        ?: throw InterceptionException("Token not found")
      val difficulty = difficultyElement
        ?.text()
        ?.toIntOrNull()
        ?.takeIf { difficulty -> difficulty >= 0 }
        ?: throw InterceptionException("Difficulty not found")

      Logger.debug(TAG) { "[tid: ${threadId}] starting Chan8MoeProofOfWork..." }

      val (solution, duration) = measureTimedValue {
        runBlocking {
          Chan8MoeProofOfWork(
            token = token,
            difficulty = difficulty
          ).find()
        }
      }

      Logger.debug(TAG) { "[tid: ${threadId}] starting Chan8MoeProofOfWork... done, took ${duration}" }

      if (solution == null) {
        throw InterceptionException("Failed to find POW solution")
      }

      Logger.debug(TAG) { "[tid: ${threadId}] Found solution: ${solution} for token: ${token.asFormattedToken()}" }

      return@run solution to token
    }

    // First solution submission request. This will return us POW_TOKEN and POW_ID cookies which we need to apply
    // for the second submission request. It will also return a 'X-PoWBlock-Status' with the solution status.
    val firstSubmitSolutionRequest = Request.Builder()
      .url("${chan8Moe.domainString}/?pow=${solution}&t=${token}")
      .header("Referer", chan8Moe.domainString)
      .tag(RequestTag())
      .get()

    chan8Moe.requestModifier()
      .modifyGenericRequest(chan8Moe, firstSubmitSolutionRequest)

    val firstSubmitSolutionResponse = _noRedirectHttpClient.newCall(firstSubmitSolutionRequest.build()).execute()
    if (!firstSubmitSolutionResponse.isRedirect) {
      val message = firstSubmitSolutionResponse.body.string()
      throw InterceptionException("Bad response: ${firstSubmitSolutionResponse.code}, message: '${message}'")
    }

    val powBlockStatus = firstSubmitSolutionResponse.header("X-PoWBlock-Status")
    if (powBlockStatus?.equals(PowBlockStatusCompleted, ignoreCase = true) != true) {
      throw InterceptionException("Didn't manage to complete POW block, got status: '${powBlockStatus}'")
    }

    val redirectPath = firstSubmitSolutionResponse.header("Location")
    if (redirectPath.isNullOrBlank()) {
      throw InterceptionException("redirectPath is null or blank")
    }

    val cookies = firstSubmitSolutionResponse.headers("Set-Cookie")

    val powTokenCookie = cookies
      .firstOrNull { cookie -> cookie.startsWith(Chan8Moe.POW_TOKEN) }
      ?.let { rawCookie -> KurobaCookie.fromRawCookie(rawCookie, Chan8Moe.POW_TOKEN) }
    if (powTokenCookie == null) {
      throw InterceptionException("Failed to find/parse powToken cookie " +
        "(cookies: ${cookies.joinToString(separator = "; ")})")
    }

    val powIdCookie = cookies
      .firstOrNull { cookie -> cookie.startsWith(Chan8Moe.POW_ID) }
      ?.let { rawCookie -> KurobaCookie.fromRawCookie(rawCookie, Chan8Moe.POW_ID) }
    if (powIdCookie == null) {
      throw InterceptionException("Failed to find/parse powIdCookie cookie " +
        "(cookies: ${cookies.joinToString(separator = "; ")})")
    }

    chan8Moe.powToken.setSync(powTokenCookie)
    chan8Moe.powId.setSync(powIdCookie)

    return true
  }

  private class RequestTag

  class InterceptionException(message: String) : IOException(message)

  companion object {
    private const val TAG = "Chan8MoeInterceptor"
    private const val PowBlockStatusRequired = "required"
    private const val PowBlockStatusCompleted = "completed"
  }
}