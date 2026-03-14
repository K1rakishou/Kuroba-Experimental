package com.github.k1rakishou.chan.core.site

import androidx.annotation.CallSuper
import com.github.k1rakishou.chan.core.site.http.HttpCall
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.COOKIE_HEADER_NAME
import com.github.k1rakishou.common.CookieBuilder
import com.github.k1rakishou.common.addHeaderIfNotExists
import com.github.k1rakishou.common.addOrReplaceCookieHeader
import com.github.k1rakishou.common.domainOrHost
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl
import okhttp3.Request

abstract class SiteRequestModifier(
  protected val site: SiteBase
) {
  private val appConstants: AppConstants
    get() = site.injectedSiteDependencies.get().appConstants

  @CallSuper
  open fun modifyHttpCall(httpCall: HttpCall, requestBuilder: Request.Builder) {
    requestBuilder.addDefaultHeaders(appConstants)

    addCloudFlareCookie(requestBuilder)
  }

  @CallSuper
  open fun modifyCookieBuilder(urlToOpen: HttpUrl, cookieBuilder: CookieBuilder) {

  }

  @CallSuper
  open fun modifyGenericRequest(site: Site, requestBuilder: Request.Builder) {
    requestBuilder.addDefaultHeaders(appConstants)
    addCloudFlareCookie(requestBuilder)
  }

  @CallSuper
  open fun modifyCatalogOrThreadGetRequest(
    site: Site,
    chanDescriptor: ChanDescriptor,
    requestBuilder: Request.Builder
  ) {
    requestBuilder.addDefaultHeaders(appConstants)
    addCloudFlareCookie(requestBuilder)
  }

  @CallSuper
  open fun modifyVideoStreamRequest(
    site: Site,
    requestProperties: MutableMap<String, String>,
    url: HttpUrl
  ) {
    requestProperties[UserAgentHeaderKey] = appConstants.userAgentMightBeOverridden
    requestProperties[AcceptEncodingHeaderKey] = AcceptEncodingHeaderValue
    requestProperties[AcceptLanagugeHeaderKey] = AcceptLanagugeHeaderValue

    addCloudFlareCookie(requestProperties, url)
  }

  @CallSuper
  open fun modifyPostReportRequest(site: Site, requestBuilder: Request.Builder) {
    requestBuilder.addDefaultHeaders(appConstants)
    addCloudFlareCookie(requestBuilder)
  }

  fun getCloudFlareCookies(url: HttpUrl): String? {
    val domainOrHost = url.domainOrHost()
    return site.commonSettings.cloudFlareClearanceCookieMap.readBlocking().get(domainOrHost)
  }

  private fun addCloudFlareCookie(
    requestProperties: MutableMap<String, String>,
    url: HttpUrl
  ) {
    val cloudflareCookies = getCloudFlareCookies(url)
    if (cloudflareCookies.isNotNullNorEmpty()) {
      requestProperties.updateCookieHeader(cloudflareCookies)
    }
  }

  private fun addCloudFlareCookie(requestBuilder: Request.Builder) {
    val url = requestBuilder.build().url
    val cloudFlareCookies = getCloudFlareCookies(url)

    if (cloudFlareCookies.isNotNullNorEmpty()) {
      requestBuilder.addOrReplaceCookieHeader(cloudFlareCookies)
    }
  }

  protected fun MutableMap<String, String>.updateCookieHeader(value: String) {
    val previous = this[COOKIE_HEADER_NAME]
    if (previous == null) {
      this[COOKIE_HEADER_NAME] = value
      return
    }

    val newCookies = with(CookieBuilder(value)) {
      addOrReplace(previous)
      build()
    }

    this[COOKIE_HEADER_NAME] = newCookies
  }

  companion object {
    @Suppress("UnusedPrivateProperty")
    private const val TAG = "SiteRequestModifier"

    const val UserAgentHeaderKey = "User-Agent"
    const val AcceptLanagugeHeaderKey = "Accept-Language"
    const val AcceptLanagugeHeaderValue = "en-US,en;q=0.5"
    const val AcceptEncodingHeaderKey = "Accept-Encoding"
    const val AcceptEncodingHeaderValue = "gzip"
    const val AcceptHeaderKey = "Accept"
    const val AcceptHeaderValue = "application/json"

    fun Request.Builder.addDefaultHeaders(appConstants: AppConstants): Request.Builder {
      this.addHeaderIfNotExists(UserAgentHeaderKey, appConstants.userAgentMightBeOverridden)
      this.addHeaderIfNotExists(AcceptLanagugeHeaderKey, AcceptLanagugeHeaderValue)
      this.addHeaderIfNotExists(AcceptEncodingHeaderKey, AcceptEncodingHeaderValue)
      this.addHeaderIfNotExists(AcceptHeaderKey, AcceptHeaderValue)

      return this
    }
  }

}