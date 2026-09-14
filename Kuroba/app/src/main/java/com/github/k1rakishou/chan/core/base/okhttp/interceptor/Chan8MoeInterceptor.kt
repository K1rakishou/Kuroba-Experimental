package com.github.k1rakishou.chan.core.base.okhttp.interceptor

import com.github.k1rakishou.chan.core.manager.FirewallBypassManager
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.site.sites.lynxchan.chan8.Chan8Moe
import com.github.k1rakishou.common.CookieBuilder
import com.github.k1rakishou.common.FirewallDetectedException
import com.github.k1rakishou.common.FirewallType
import com.github.k1rakishou.core_logger.Logger
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Detects 8chan.moe's POWBlock ('X-PoWBlock-Status: required' response header) and passes it using
 * [com.github.k1rakishou.chan.features.webview.task.Chan8MoePowBlockTask] which stores the POW_TOKEN/POW_ID cookies
 * that are then added to the requests by
 * [com.github.k1rakishou.chan.core.site.sites.lynxchan.chan8.Chan8MoeRequestModifier].
 * */
class Chan8MoeInterceptor(
  private val siteResolver: SiteResolver,
  private val firewallBypassManager: FirewallBypassManager
) : KurobaOkHttpInterceptor() {

  override fun intercept(chain: Interceptor.Chain): Response {
    val originalRequest = chain.request()

    val response = chain.proceed(originalRequest)
    if (!needPowBlockBypass(response)) {
      return response
    }

    siteResolver.waitUntilInitialized()

    val chan8Moe = siteResolver.findSiteForUrl(originalRequest.url.toString())
    if (chan8Moe !is Chan8Moe) {
      return response
    }

    if (originalRequest.tag(RequestTag::class) != null) {
      // We have already passed POWBlock for this request and the server still requires it, don't loop forever
      Logger.error(TAG) { "[$okHttpType] POWBlock is still required after bypass for '${originalRequest.url}'" }
      response.close()
      throw FirewallDetectedException(firewallType = FirewallType.Chan8MoePowBlock, requestUrl = originalRequest.url)
    }

    response.close()

    if (hasNewerPowCookies(originalRequest, chan8Moe)) {
      // POWBlock was passed (e.g. by another request) after this request was sent, just retry it with the new cookies
      Logger.debug(TAG) {
        "[$okHttpType] Request '${originalRequest.url}' was sent with outdated POW cookies, retrying"
      }
      return chain.proceed(updateRequest(originalRequest, chan8Moe))
    }

    val urlToOpen = chan8Moe.currentDomain
    Logger.debug(TAG) { "[$okHttpType] POWBlock detected for '${originalRequest.url}', opening '${urlToOpen}'" }

    val bypassSuccess = AtomicBoolean(false)
    val countDownLatch = CountDownLatch(1)

    firewallBypassManager.onFirewallDetected(
      firewallType = FirewallType.Chan8MoePowBlock,
      urlToOpen = urlToOpen,
      onFinished = { success ->
        bypassSuccess.set(success)
        countDownLatch.countDown()
      }
    )

    val awaitSuccess = try {
      countDownLatch.await(MAX_WAIT_TIME_MILLIS, TimeUnit.MILLISECONDS)
    } catch (error: InterruptedException) {
      throw IOException("Interrupted while waiting for POWBlock bypass", error)
    }

    if (!awaitSuccess || !bypassSuccess.get()) {
      Logger.error(TAG) {
        "[$okHttpType] POWBlock bypass failed for '${originalRequest.url}' " +
          "(timeout: ${!awaitSuccess}, success: ${bypassSuccess.get()})"
      }

      throw FirewallDetectedException(firewallType = FirewallType.Chan8MoePowBlock, requestUrl = originalRequest.url)
    }

    Logger.debug(TAG) { "[$okHttpType] POWBlock bypass succeeded, retrying '${originalRequest.url}'" }

    // Now perform the same request but with new cookies
    return chain.proceed(updateRequest(originalRequest, chan8Moe))
  }

  private fun updateRequest(
    prevRequest: Request,
    chan8Moe: Chan8Moe
  ): Request {
    val requestBuilder = prevRequest.newBuilder()

    chan8Moe.requestModifier
      .modifyGenericRequest(chan8Moe, requestBuilder)

    return requestBuilder
      .tag(RequestTag::class.java, RequestTag())
      .build()
  }

  private fun hasNewerPowCookies(request: Request, chan8Moe: Chan8Moe): Boolean {
    val currentPowToken = chan8Moe.settings.powToken.readBlocking()?.value
    if (currentPowToken.isNullOrBlank()) {
      return false
    }

    val sentPowToken = request.headers("Cookie")
      .firstNotNullOfOrNull { cookieHeader -> CookieBuilder(cookieHeader).get(Chan8Moe.POW_TOKEN)?.value }

    return sentPowToken != currentPowToken
  }

  private fun needPowBlockBypass(response: Response): Boolean {
    return when (val powBlockStatus = response.header("X-PoWBlock-Status")?.lowercase()) {
      null,
      PowBlockStatusCompleted -> false
      PowBlockStatusRequired -> true
      else -> {
        Logger.error(TAG) { "[$okHttpType] Unknown 'X-PoWBlock-Status' value: '${powBlockStatus}'" }
        false
      }
    }
  }

  private class RequestTag

  companion object {
    private const val TAG = "Chan8MoeInterceptor"
    private const val PowBlockStatusRequired = "required"
    private const val PowBlockStatusCompleted = "completed"

    // The challenge is solved by the site's JS in a visible WebView (the user has to be in the app) which may take a
    // while depending on the difficulty.
    private const val MAX_WAIT_TIME_MILLIS = 3 * 60 * 1000L
  }
}
