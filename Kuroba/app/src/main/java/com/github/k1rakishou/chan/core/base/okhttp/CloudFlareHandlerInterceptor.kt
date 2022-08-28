package com.github.k1rakishou.chan.core.base.okhttp

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.manager.FirewallBypassManager
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.utils.containsPattern
import com.github.k1rakishou.common.FirewallDetectedException
import com.github.k1rakishou.common.FirewallType
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.prefs.StringSetting
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.nio.charset.StandardCharsets

class CloudFlareHandlerInterceptor(
  private val siteResolver: SiteResolver,
  private val firewallBypassManager: FirewallBypassManager,
  private val verboseLogs: Boolean,
  private val okHttpType: String
) : Interceptor {
  @GuardedBy("this")
  private val sitesThatRequireCloudFlareCache = mutableSetOf<String>()

  @GuardedBy("this")
  private val requestsWithAddedCookie = mutableMapOf<String, Request>()

  override fun intercept(chain: Interceptor.Chain): Response {
    var request = chain.request()
    var addedCookie = false
    val host = request.url.host

    if (requireCloudFlareCookie(request)) {
      val updatedRequest = addCloudFlareCookie(chain.request())
      if (updatedRequest != null) {
        request = updatedRequest
        addedCookie = true

        synchronized(this) {
          if (!requestsWithAddedCookie.containsKey(host)) {
            requestsWithAddedCookie[host] = request
          }
        }
      }
    }

    val response = chain.proceed(request)

    if (response.code == 503 || response.code == 403) {
      processCloudflareRejectedRequest(
        response = response,
        host = host,
        addedCookie = addedCookie,
        chain = chain,
        request = request
      )
    } else {
      synchronized(this) { requestsWithAddedCookie.remove(host) }
    }

    return response
  }

  private fun processCloudflareRejectedRequest(
    response: Response,
    host: String,
    addedCookie: Boolean,
    chain: Interceptor.Chain,
    request: Request
  ) {
    if (!tryDetectCloudFlareNeedle(response)) {
      return
    }

    if (verboseLogs) {
      Logger.d(TAG, "[$okHttpType] Found CloudFlare needle in the page's body")
    }

    // To avoid race conditions which could result in us ending up in a situation where a request
    // with an old cookie or no cookie at all causing us to remove the old cookie from the site
    // settings.
    val isExpectedRequestWithCookie = synchronized(this) { requestsWithAddedCookie[host] === request }
    if (addedCookie && isExpectedRequestWithCookie) {
      // For some reason CloudFlare still rejected our request even though we added the cookie.
      // This may happen because of many reasons like the cookie expired or it was somehow
      // damaged so we need to delete it and re-request again.

      if (verboseLogs) {
        Logger.d(
          TAG,
          "[$okHttpType] Cookie was already added and we still failed, removing the old cookie"
        )
      }

      removeSiteClearanceCookie(chain.request())
      synchronized(this) { requestsWithAddedCookie.remove(host) }
    }

    synchronized(this) { sitesThatRequireCloudFlareCache.add(host) }

    if (siteResolver.isInitialized() && request.method.equals("GET", ignoreCase = true)) {
      val site = siteResolver.findSiteForUrl(request.url.toString())
      if (site != null) {
        val siteDescriptor = site.siteDescriptor()

        val requestUrl = site
          .firewallChallengeEndpoint()
          ?: request.url

        firewallBypassManager.onFirewallDetected(
          firewallType = FirewallType.Cloudflare,
          siteDescriptor = siteDescriptor,
          urlToOpen = requestUrl
        )
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

    val alreadyCheckedSite = synchronized(this) { host in sitesThatRequireCloudFlareCache }
    if (alreadyCheckedSite) {
      return true
    }

    val url = request.url
    val site = siteResolver.findSiteForUrl(url.toString())

    if (site == null) {
      return false
    }

    val cloudFlareClearanceCookieSetting = site.getSettingBySettingId<StringSetting>(
      SiteSetting.SiteSettingId.CloudFlareClearanceCookie
    )

    if (cloudFlareClearanceCookieSetting == null) {
      Logger.e(TAG, "[$okHttpType] requireCloudFlareCookie() CloudFlareClearanceCookie setting was not found")
      return false
    }

    return cloudFlareClearanceCookieSetting.get().isNotEmpty()
  }

  private fun removeSiteClearanceCookie(request: Request) {
    val url = request.url
    val site = siteResolver.findSiteForUrl(url.toString())

    if (site == null) {
      return
    }

    val cloudFlareClearanceCookieSetting = site.getSettingBySettingId<StringSetting>(
      SiteSetting.SiteSettingId.CloudFlareClearanceCookie
    )

    if (cloudFlareClearanceCookieSetting == null) {
      Logger.e(TAG, "[$okHttpType] removeSiteClearanceCookie() CloudFlareClearanceCookie setting was not found")
      return
    }

    val prevValue = cloudFlareClearanceCookieSetting.get()
    if (prevValue.isEmpty()) {
      Logger.e(TAG, "[$okHttpType] removeSiteClearanceCookie() cookieValue is empty")
      return
    }

    cloudFlareClearanceCookieSetting.setSyncNoCheck("")
  }

  private fun addCloudFlareCookie(prevRequest: Request): Request? {
    val url = prevRequest.url
    val site = siteResolver.findSiteForUrl(url.toString())

    if (site == null) {
      return null
    }

    val cloudFlareClearanceCookieSetting = site.getSettingBySettingId<StringSetting>(
      SiteSetting.SiteSettingId.CloudFlareClearanceCookie
    )

    if (cloudFlareClearanceCookieSetting == null) {
      Logger.e(TAG, "[$okHttpType] addCloudFlareCookie() CloudFlareClearanceCookie setting was not found")
      return null
    }

    val cookieValue = cloudFlareClearanceCookieSetting.get()
    if (cookieValue.isEmpty()) {
      Logger.e(TAG, "[$okHttpType] addCloudFlareCookie() cookieValue is empty")
      return null
    }

    return prevRequest.newBuilder()
      .addHeader("Cookie", "$CF_CLEARANCE=$cookieValue")
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