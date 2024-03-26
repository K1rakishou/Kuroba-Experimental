package com.github.k1rakishou.chan.core.base.okhttp

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.manager.FirewallBypassManager
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.utils.containsPattern
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.FirewallDetectedException
import com.github.k1rakishou.common.FirewallType
import com.github.k1rakishou.common.addOrReplaceCookieHeader
import com.github.k1rakishou.common.domain
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.prefs.MapSetting
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.closeQuietly
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class CloudFlareHandlerInterceptor(
  private val siteResolver: SiteResolver,
  private val firewallBypassManager: FirewallBypassManager,
  private val okHttpType: String
) : Interceptor {
  @GuardedBy("this")
  private val sitesThatRequireCloudFlareCache = mutableSetOf<String>()

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
    val host = request.url.host

    if (requireCloudFlareCookie(request)) {
      val updatedRequest = addCloudFlareCookie(chain.request())
      if (updatedRequest != null) {
        request = updatedRequest
      }
    }

    val response = chain.proceed(request)

    if (response.code == 503 || response.code == 403 && !ignoreCloudFlareBotDetectionErrors(request)) {
      val newResponse = processCloudflareRejectedRequest(
        response = response,
        host = host,
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
    host: String,
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

    synchronized(this) { sitesThatRequireCloudFlareCache.add(host) }

    if (canShowCloudFlareBypassScreen(retrying, request)) {
      siteResolver.waitUntilInitialized()

      val site = siteResolver.findSiteForUrl(request.url.toString())
      if (site != null) {
        val siteDescriptor = site.siteDescriptor()

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
          siteDescriptor = siteDescriptor,
          urlToOpen = request.url,
          onFinished = { success ->
            bypassSuccess.set(success)
            countDownLatch.countDown()
          }
        )

        val startTime = System.currentTimeMillis()
        val awaitSuccess = try {
          countDownLatch.await(AppConstants.CLOUDFLARE_INTERCEPTOR_FIREWALL_BYPASS_MAX_WAIT_TIME_MILLIS, TimeUnit.MILLISECONDS)
        } catch (error: Throwable) {
          false
        }

        val deltaTime = System.currentTimeMillis() - startTime

        if (awaitSuccess) {
          if (bypassSuccess.get()) {
            Logger.debug(TAG) {
              "[$okHttpType] firewallBypassManager.onFirewallDetected() endpoint '${request.url}'... success. (took: ${deltaTime}ms)"
            }

            response.closeQuietly()

            return interceptInternal(
              chain = chain,
              retryingAfterCloudFlareAuthorizationFinished = true
            )
          }

          Logger.debug(TAG) {
            "[$okHttpType] firewallBypassManager.onFirewallDetected() endpoint '${request.url}'... unsuccessful. (took: ${deltaTime}ms)"
          }
        }

        Logger.debug(TAG) {
          "[$okHttpType] firewallBypassManager.onFirewallDetected() endpoint '${request.url}'... timeout. (took: ${deltaTime}ms)"
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

  private fun requireCloudFlareCookie(request: Request): Boolean {
    val host = request.url.host

    val domainOrHost = request.url.domain()
      ?: request.url.host

    val alreadyCheckedSite = synchronized(this) { host in sitesThatRequireCloudFlareCache }
    if (alreadyCheckedSite) {
      return true
    }

    siteResolver.waitUntilInitialized()

    val url = request.url
    val site = siteResolver.findSiteForUrl(url.toString())

    if (site == null) {
      Logger.error(TAG) {
        "[$okHttpType] requireCloudFlareCookie() siteResolver.findSiteForUrl(${url}) returned null"
      }

      return false
    }

    val cloudFlareClearanceCookieSetting = site.getSettingBySettingId<MapSetting>(
      SiteSetting.SiteSettingId.CloudFlareClearanceCookie
    )

    if (cloudFlareClearanceCookieSetting == null) {
      Logger.error(TAG) {
        "[$okHttpType] requireCloudFlareCookie() CloudFlareClearanceCookie setting was not found (url: ${url}, site: ${site.name()})"
      }

      return false
    }

    return cloudFlareClearanceCookieSetting.get(domainOrHost).isNotNullNorEmpty()
  }

  private fun addCloudFlareCookie(prevRequest: Request): Request? {
    val url = prevRequest.url

    siteResolver.waitUntilInitialized()
    val site = siteResolver.findSiteForUrl(url.toString())

    val domainOrHost = prevRequest.url.domain()
      ?: prevRequest.url.host

    if (site == null) {
      Logger.e(TAG, "[$okHttpType] addCloudFlareCookie() siteResolver.findSiteForUrl(${url}) returned null")
      return null
    }

    val cloudFlareClearanceCookieSetting = site.getSettingBySettingId<MapSetting>(
      SiteSetting.SiteSettingId.CloudFlareClearanceCookie
    )

    if (cloudFlareClearanceCookieSetting == null) {
      Logger.e(TAG, "[$okHttpType] addCloudFlareCookie() CloudFlareClearanceCookie setting was not found")
      return null
    }

    val cookieValue = cloudFlareClearanceCookieSetting.get(domainOrHost)
    if (cookieValue.isNullOrEmpty()) {
      Logger.e(TAG, "[$okHttpType] addCloudFlareCookie() cookieValue is null or empty")
      return null
    }

    return prevRequest.newBuilder()
      .addOrReplaceCookieHeader("$CF_CLEARANCE=$cookieValue")
      .build()
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
      ?: return false

    return responseBody.use { body ->
      return@use body.byteStream().use { inputStream ->
        val bytes = ByteArray(READ_BYTES_COUNT) { 0x00 }
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

    const val CF_CLEARANCE = "cf_clearance"

    private val cloudFlareHeaders = arrayOf("cloudflare-nginx", "cloudflare")

    private val cloudflareNeedles = arrayOf(
      "<title>Just a moment".toByteArray(StandardCharsets.UTF_8),
      "<title>Please wait".toByteArray(StandardCharsets.UTF_8),
      "Checking your browser before accessing".toByteArray(StandardCharsets.UTF_8),
      "Browser Integrity Check".toByteArray(StandardCharsets.UTF_8)
    )
  }
}