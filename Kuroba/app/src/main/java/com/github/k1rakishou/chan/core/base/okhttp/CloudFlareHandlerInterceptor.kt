package com.github.k1rakishou.chan.core.base.okhttp

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.utils.containsPattern
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.prefs.StringSetting
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException
import java.nio.charset.StandardCharsets

class CloudFlareHandlerInterceptor(
  private val siteResolver: SiteResolver,
  private val isOkHttpClientForSiteRequests: Boolean,
  private val verboseLogs: Boolean,
  private val okHttpType: String
) : Interceptor {

  @GuardedBy("this")
  private val sitesThatRequireCloudFlareCache = mutableSetOf<String>()

  override fun intercept(chain: Interceptor.Chain): Response {
    var request = chain.request()
    var addedCookie = false
    val host = request.url.host

    if (requireCloudFlareCookie(request)) {
      val updatedRequest = addCloudFlareCookie(chain.request())
      if (updatedRequest != null) {
        request = updatedRequest
        addedCookie = true
      }
    }

    val response = chain.proceed(request)

    if (response.code == 503 || response.code == 403) {
      val body = response.body
      if (body != null && tryDetectCloudFlareNeedle(host, body)) {
        if (verboseLogs) {
          Logger.d(TAG, "[$okHttpType] Found CloudFlare needle in the page's body")
        }

        if (addedCookie && isOkHttpClientForSiteRequests) {
          if (verboseLogs) {
            Logger.d(TAG, "[$okHttpType] Cookie was already added and we still failed, " +
              "removing the old cookie")
          }

          // For some reason CloudFlare still rejected our request even though we added the cookie.
          // This may happen because of many reasons like the cookie expired or it was somehow
          // damaged so we need to delete it and re-request again.
          removeSiteClearanceCookie(chain.request())
        }

        synchronized(this) { sitesThatRequireCloudFlareCache.add(host) }

        // We only want to throw this exception when loading a site's thread endpoint. In any other
        // case (like when opening media files on that site) we only want to add the CloudFlare
        // CfClearance cookie to the headers.
        if (isOkHttpClientForSiteRequests) {
          throw CloudFlareDetectedException(request.url)
        }
      }
    }

    return response
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

  private fun tryDetectCloudFlareNeedle(host: String, responseBody: ResponseBody): Boolean {
    return responseBody.use { body ->
      return@use body.byteStream().use { inputStream ->
        val bytes = ByteArray(READ_BYTES_COUNT) { 0x00 }
        val read = inputStream.read(bytes)
        if (read <= 0) {
          return@use false
        }

        if (host.contains(CHAN4_SEARCH_URL, ignoreCase = true) || host.contains(CHANNEL4_SEARCH_URL, ignoreCase = true)) {
          if (!bytes.containsPattern(0, CLOUD_FLARE_NEEDLE_4CHAN_SEARCH)) {
            return@use false
          }
        } else {
          if (
            !bytes.containsPattern(0, CLOUD_FLARE_NEEDLE1) &&
            !bytes.containsPattern(0, CLOUD_FLARE_NEEDLE2) &&
            !bytes.containsPattern(0, CLOUD_FLARE_NEEDLE3)
          ) {
            return@use false
          }
        }

        return@use true
      }
    }
  }

  class CloudFlareDetectedException(
    val requestUrl: HttpUrl
  ) : IOException("Url '$requestUrl' cannot be opened without going through CloudFlare checks first!")

  companion object {
    private const val TAG = "CloudFlareHandlerInterceptor"
    private const val READ_BYTES_COUNT = 24 * 1024 // 24KB

    const val CF_CLEARANCE = "cf_clearance"

    private const val CHAN4_SEARCH_URL = "find.4chan.org"
    private const val CHANNEL4_SEARCH_URL = "find.4channel.org"

    private val CLOUD_FLARE_NEEDLE1 = "<title>Please Wait... | Cloudflare</title>".toByteArray(StandardCharsets.UTF_8)
    private val CLOUD_FLARE_NEEDLE2 = "Checking your browser before accessing".toByteArray(StandardCharsets.UTF_8)
    private val CLOUD_FLARE_NEEDLE3 = "<title>Just a moment...</title>".toByteArray(StandardCharsets.UTF_8)
    private val CLOUD_FLARE_NEEDLE_4CHAN_SEARCH = "Browser Integrity Check".toByteArray(StandardCharsets.UTF_8)
  }
}