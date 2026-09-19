package com.github.k1rakishou.chan.core.base.okhttp.interceptor

import com.github.k1rakishou.chan.core.base.okhttp.interceptor.CloudFlareInterceptor.Companion.COOKIE_CF_BM
import com.github.k1rakishou.chan.core.base.okhttp.interceptor.CloudFlareInterceptor.Companion.COOKIE_TCS
import com.github.k1rakishou.chan.core.manager.FirewallBypassManager
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.utils.containsPattern
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.COOKIE_HEADER_NAME
import com.github.k1rakishou.common.CookieBuilder
import com.github.k1rakishou.common.FirewallDetectedException
import com.github.k1rakishou.common.FirewallType
import com.github.k1rakishou.common.SET_COOKIE_HEADER_NAME
import com.github.k1rakishou.common.StringUtils.asFormattedToken
import com.github.k1rakishou.core_logger.Logger
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.closeQuietly
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class CloudFlareInterceptor(
  private val siteResolver: SiteResolver,
  private val firewallBypassManager: FirewallBypassManager
) : KurobaOkHttpInterceptor() {
  override fun intercept(chain: Interceptor.Chain): Response {
    return interceptInternal(
      chain = chain,
      retryingAfterCloudFlareAuthorizationFinished = false
    )
  }

  private fun interceptInternal(
    chain: Interceptor.Chain,
    retryingAfterCloudFlareAuthorizationFinished: Boolean
  ): Response {
    var request = chain.request()

    val updatedRequest = addCloudFlareCookie(chain.request())
    if (updatedRequest != null) {
      request = updatedRequest
    }

    val response = chain.proceed(request)

    storeRefreshedCloudFlareCookies(response)

    if ((response.code == 503 || response.code == 403) && !ignoreCloudFlareBotDetectionErrors(request)) {
      val newResponse = processCloudflareRejectedRequest(
        response = response,
        chain = chain,
        request = request,
        retrying = retryingAfterCloudFlareAuthorizationFinished
      )

      if (newResponse != null) {
        return newResponse
      }

      // Fallthrough
    }

    return response
  }

  private fun processCloudflareRejectedRequest(
    response: Response,
    chain: Interceptor.Chain,
    request: Request,
    retrying: Boolean
  ): Response? {
    if (!tryDetectCloudFlareNeedle(response)) {
      Logger.verbose(TAG) {
        "[$okHttpType] Couldn't find CloudFlare needle in the page's body for endpoint '${request.url}'"
      }

      return null
    }

    Logger.verbose(TAG) {
      "[$okHttpType] Found CloudFlare needle in the page's body for endpoint '${request.url}'"
    }

    logRejectedRequest(response, request)

    if (canShowCloudFlareBypassScreen(retrying, request)) {
      siteResolver.waitUntilInitialized()

      val site = siteResolver.findSiteForUrl(request.url.toString())
      if (site != null) {
        val bypassSuccess = AtomicBoolean(false)
        val countDownLatch = CountDownLatch(1)

        Logger.debug(TAG) {
          "[$okHttpType] retryingAfterCloudFlareAuthorizationFinished: ${retrying} endpoint '${request.url}'"
        }

        Logger.debug(TAG) {
          "[$okHttpType] firewallBypassManager.onFirewallDetected() endpoint '${request.url}'..."
        }

        firewallBypassManager.onFirewallDetected(
          firewallType = FirewallType.Cloudflare,
          urlToOpen = request.url,
          onFinished = { success ->
            bypassSuccess.set(success)
            countDownLatch.countDown()
          }
        )

        val startTime = System.currentTimeMillis()
        val awaitSuccess = try {
          countDownLatch.await(
            AppConstants.CLOUDFLARE_INTERCEPTOR_FIREWALL_BYPASS_MAX_WAIT_TIME_MILLIS,
            TimeUnit.MILLISECONDS
          )
        } catch (ignored: Throwable) {
          false
        }

        val deltaTime = System.currentTimeMillis() - startTime

        if (awaitSuccess) {
          if (bypassSuccess.get()) {
            Logger.debug(TAG) {
              "[$okHttpType] firewallBypassManager.onFirewallDetected() " +
                "endpoint '${request.url}'... success. (took: ${deltaTime}ms)"
            }

            response.closeQuietly()

            return interceptInternal(
              chain = chain,
              retryingAfterCloudFlareAuthorizationFinished = true
            )
          }

          Logger.debug(TAG) {
            "[$okHttpType] firewallBypassManager.onFirewallDetected() " +
              "endpoint '${request.url}'... unsuccessful. (took: ${deltaTime}ms)"
          }
        }

        Logger.debug(TAG) {
          "[$okHttpType] firewallBypassManager.onFirewallDetected() " +
            "endpoint '${request.url}'... timeout. (took: ${deltaTime}ms)"
        }

        // countDownLatch.await() reached zero which means CloudFlare bypass got stuck somewhere so we need to throw
        // the exception
      }
    }

    // We only want to throw this exception when loading a site's thread endpoint. In any other
    // case (like when opening media files on that site) we only want to add the CloudFlare
    // CfClearance cookie to the headers.
    throw FirewallDetectedException(
      firewallType = FirewallType.Cloudflare,
      requestUrl = request.url
    )
  }

  /**
   * CloudFlare refreshes the cookies it gave us through `Set-Cookie` of ordinary responses. Keeping them is what makes
   * a clearance last: the one the check produced is only honored for a while (see [SiteRequestModifier.storeCloudFlareCookies]).
   *
   * `cf_clearance` is only taken from a successful response — the challenge response sets one of its own, which would
   * overwrite the one that still works. The rest ([COOKIE_CF_BM], [COOKIE_TCS]) are taken from any response: they are
   * session cookies rather than a clearance, and CloudFlare hands them out on the challenge itself
   * (`https://sys.4chan.org/captcha`, the one 4chan endpoint that is behind the check, answers 403 *and* sets
   * `__cf_bm`), so dropping them means every request looks like a brand new session.
   * */
  private fun storeRefreshedCloudFlareCookies(response: Response) {
    val setCookieHeaders = response.headers(SET_COOKIE_HEADER_NAME)
    if (setCookieHeaders.isEmpty() || !siteResolver.isInitialized()) {
      return
    }

    val refreshedCookies = CookieBuilder()

    setCookieHeaders.forEach { setCookieHeader ->
      // "cf_clearance=<value>; path=/; expires=...; domain=.archived.moe; HttpOnly; Secure; SameSite=None"
      val nameAndValue = setCookieHeader.substringBefore(';')
      val name = nameAndValue.substringBefore('=').trim()

      if (name !in EXPECTED_CLOUDFLARE_COOKIES) {
        return@forEach
      }

      if (name == COOKIE_CF_CLEARANCE && !response.isSuccessful) {
        return@forEach
      }

      val value = nameAndValue.substringAfter('=', missingDelimiterValue = "").trim()
      if (value.isBlank() || value in DELETED_COOKIE_VALUES) {
        return@forEach
      }

      refreshedCookies.addOrReplace(name, value)
    }

    if (refreshedCookies.isEmpty()) {
      return
    }

    // Not the url of the request we sent: okhttp follows redirects internally and a cookie belongs to the domain that
    // actually set it (2ch.hk redirects to 2ch.su, and the two are different domains as far as CloudFlare is concerned)
    val url = response.request.url
    val site = siteResolver.findSiteForUrl(url.toString())
    if (site == null) {
      return
    }

    Logger.debug(TAG) {
      val cookieNames = refreshedCookies.cookieParts().map { cookie -> cookie.key }
      "[$okHttpType] storeRefreshedCloudFlareCookies() ${url} sent new CloudFlare cookies: ${cookieNames}"
    }

    site.requestModifier.storeCloudFlareCookies(url, refreshedCookies.build())
  }

  /**
   * Whether the request that CloudFlare rejected was carrying our stored cf_clearance cookie or not is the only thing
   * that tells apart "the cookie never reached the request" from "CloudFlare rejected the cookie we have".
   * */
  private fun logRejectedRequest(response: Response, request: Request) {
    Logger.debug(TAG) {
      val cfClearance = request.header(COOKIE_HEADER_NAME)
        ?.let { cookies -> CookieBuilder(cookies).get(COOKIE_CF_CLEARANCE)?.value }
        .asFormattedToken()

      "[$okHttpType] CloudFlare rejected '${request.url}'. code: ${response.code}, " +
        "request ${COOKIE_CF_CLEARANCE}: ${cfClearance}, " +
        "cf-mitigated: '${response.header("cf-mitigated")}', cf-ray: '${response.header("cf-ray")}'"
    }
  }

  private fun addCloudFlareCookie(prevRequest: Request): Request? {
    siteResolver.waitUntilInitialized()

    val url = prevRequest.url
    val site = siteResolver.findSiteForUrl(url.toString())
    if (site == null) {
      Logger.warning(TAG) { "[$okHttpType] addCloudFlareCookie() siteResolver.findSiteForUrl(${url}) returned null" }
      return null
    }

    // The same lookup the request modifier does when it adds the cookie to the request, it logs what was found
    val cookieValue = site.requestModifier.getCloudFlareCookies(url)
    if (cookieValue.isNullOrEmpty()) {
      Logger.verbose(TAG) { "[$okHttpType] addCloudFlareCookie() no CloudFlare cookies for ${url}" }
      return null
    }

    val newBuilder = prevRequest.newBuilder()
    site.requestModifier.modifyGenericRequest(site, newBuilder)

    val newRequest = newBuilder.build()
    Logger.verbose(TAG) {
      val cfClearance = newRequest.header(COOKIE_HEADER_NAME)
        ?.let { cookies -> CookieBuilder(cookies).get(COOKIE_CF_CLEARANCE)?.value }
        .asFormattedToken()

      "[$okHttpType] addCloudFlareCookie() ${url} now carries ${COOKIE_CF_CLEARANCE}: ${cfClearance}"
    }

    return newRequest
  }

  private fun tryDetectCloudFlareNeedle(response: Response): Boolean {
    // Fast path, check the "Server" header
    val serverHeader = response.header("Server")
    if (serverHeader != null) {
      val foundCloudFlareHeader = cloudFlareHeaders
        .any { cloudFlareHeader -> cloudFlareHeader.equals(serverHeader, ignoreCase = true) }

      if (foundCloudFlareHeader) {
        return true
      }
    }

    // Slow path, load first READ_BYTES_COUNT bytes of the body
    val responseBody = response.body

    return responseBody.use { body ->
      return@use body.byteStream().use { inputStream ->
        val bytes = ByteArray(READ_BYTES_COUNT)
        val read = inputStream.read(bytes)
        if (read <= 0) {
          return@use false
        }

        return@use cloudflareNeedles.any { needle -> bytes.containsPattern(0, needle) }
      }
    }
  }

  private fun canShowCloudFlareBypassScreen(
    retryingAfterCloudFlareAuthorizationFinished: Boolean,
    request: Request
  ): Boolean {
    if (retryingAfterCloudFlareAuthorizationFinished) {
      return false
    }

    if (!siteResolver.isInitialized()) {
      return false
    }

    return request.method.equals("GET", ignoreCase = true)
  }

  private fun ignoreCloudFlareBotDetectionErrors(request: Request): Boolean {
    val ignoringCloudFlareError = request.tag(IgnoreCloudFlareBotDetectionErrors::class.java) != null
    if (ignoringCloudFlareError) {
      Logger.debug(TAG) { "Ignoring CloudFlare bot detection errors for request '${request.url}'" }
    }

    return ignoringCloudFlareError
  }

  data object IgnoreCloudFlareBotDetectionErrors

  companion object {
    private const val TAG = "CloudFlareHandlerInterceptor"
    private const val READ_BYTES_COUNT = 24 * 1024 // 24KB

    const val COOKIE_CF_CLEARANCE = "cf_clearance"
    const val COOKIE_TCS = "_tcs"
    const val COOKIE_CF_BM = "__cf_bm"

    // What CloudFlare sends to remove a cookie instead of refreshing it
    private val DELETED_COOKIE_VALUES = setOf("deleted", "\"\"")

    val EXPECTED_CLOUDFLARE_COOKIES = listOf(
      COOKIE_CF_CLEARANCE,
      COOKIE_TCS,
      COOKIE_CF_BM,
    )

    private val cloudFlareHeaders = arrayOf("cloudflare-nginx", "cloudflare")

    private val cloudflareNeedles = arrayOf(
      "<title>Just a moment".toByteArray(StandardCharsets.UTF_8),
      "<title>Please wait".toByteArray(StandardCharsets.UTF_8),
      "Checking your browser before accessing".toByteArray(StandardCharsets.UTF_8),
      "Browser Integrity Check".toByteArray(StandardCharsets.UTF_8)
    )
  }
}