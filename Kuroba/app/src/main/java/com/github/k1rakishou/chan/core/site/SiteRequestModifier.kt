package com.github.k1rakishou.chan.core.site

import android.webkit.WebView
import androidx.annotation.CallSuper
import com.github.k1rakishou.chan.core.site.http.HttpCall
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.addHeaderIfNotExists
import com.github.k1rakishou.common.addOrReplaceCookieHeader
import com.github.k1rakishou.common.domainOrHost
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.prefs.MapSetting
import okhttp3.HttpUrl
import okhttp3.Request

abstract class SiteRequestModifier<T : Site>(
  protected val site: T,
  protected val appConstants: AppConstants
) {

  @CallSuper
  open fun modifyHttpCall(httpCall: HttpCall, requestBuilder: Request.Builder) {
    requestBuilder.addDefaultHeaders(appConstants)

    addCloudFlareCookie(requestBuilder)
  }

  @CallSuper
  open fun modifyWebView(webView: WebView, urlToOpen: HttpUrl) {

  }

  @CallSuper
  open fun modifyThumbnailGetRequest(site: T, requestBuilder: Request.Builder) {
    requestBuilder.addDefaultHeaders(appConstants)
    addCloudFlareCookie(requestBuilder)
  }

  @CallSuper
  open fun modifyCatalogOrThreadGetRequest(
    site: T,
    chanDescriptor: ChanDescriptor,
    requestBuilder: Request.Builder
  ) {
    requestBuilder.addDefaultHeaders(appConstants)
    addCloudFlareCookie(requestBuilder)
  }

  @CallSuper
  open fun modifyFullImageHeadRequest(
    site: T,
    requestBuilder: Request.Builder
  ) {
    requestBuilder.addHeaderIfNotExists(UserAgentHeaderKey, appConstants.userAgentMightBeOverridden)
    addCloudFlareCookie(requestBuilder)
  }

  @CallSuper
  open fun modifyFullImageGetRequest(
    site: T,
    requestBuilder: Request.Builder
  ) {
    requestBuilder.addDefaultHeaders(appConstants)
    addCloudFlareCookie(requestBuilder)
  }

  @CallSuper
  open fun modifyMediaDownloadRequest(
    site: T,
    requestBuilder: Request.Builder
  ) {
    requestBuilder.addDefaultHeaders(appConstants)
    addCloudFlareCookie(requestBuilder)
  }

  @CallSuper
  open fun modifyVideoStreamRequest(
    site: T,
    requestProperties: MutableMap<String, String>,
    url: HttpUrl
  ) {
    requestProperties.put(UserAgentHeaderKey, appConstants.userAgentMightBeOverridden)
    requestProperties.put(AcceptEncodingHeaderKey, AcceptEncodingHeaderValue)
    requestProperties.put(AcceptLanagugeHeaderKey, AcceptLanagugeHeaderValue)
  }

  @CallSuper
  open fun modifyArchiveGetRequest(site: T, requestBuilder: Request.Builder) {
    requestBuilder.addDefaultHeaders(appConstants)
    addCloudFlareCookie(requestBuilder)
  }

  @CallSuper
  open fun modifySearchGetRequest(site: T, requestBuilder: Request.Builder) {
    requestBuilder.addDefaultHeaders(appConstants)
    addCloudFlareCookie(requestBuilder)
  }

  @CallSuper
  open fun modifyCaptchaGetRequest(site: T, requestBuilder: Request.Builder) {
    requestBuilder.addDefaultHeaders(appConstants)
    addCloudFlareCookie(requestBuilder)
  }

  @CallSuper
  open fun modifyPostReportRequest(site: T, requestBuilder: Request.Builder) {
    requestBuilder.addDefaultHeaders(appConstants)
    addCloudFlareCookie(requestBuilder)
  }

  @CallSuper
  open fun modifyLoginRequest(site: T, requestBuilder: Request.Builder) {
    requestBuilder.addDefaultHeaders(appConstants)
    addCloudFlareCookie(requestBuilder)
  }

  @CallSuper
  open fun modifyGetPasscodeInfoRequest(site: T, requestBuilder: Request.Builder) {
    requestBuilder.addDefaultHeaders(appConstants)
    addCloudFlareCookie(requestBuilder)
  }

  @CallSuper
  open fun modifyPagesRequest(site: T, requestBuilder: Request.Builder) {
    requestBuilder.addDefaultHeaders(appConstants)
    addCloudFlareCookie(requestBuilder)
  }

  @CallSuper
  open fun modifyBoardsGetRequest(requestBuilder: Request.Builder) {
    requestBuilder.addDefaultHeaders(appConstants)
    addCloudFlareCookie(requestBuilder)
  }

  private fun addCloudFlareCookie(requestBuilder: Request.Builder) {
    val url = requestBuilder.build().url
    val cloudFlareCookies = getCloudFlareCookies(url)

    Logger.d(TAG, "addCloudFlareCookie('${url}') '${cloudFlareCookies}'")

    if (cloudFlareCookies.isNotNullNorEmpty()) {
      requestBuilder.addOrReplaceCookieHeader(cloudFlareCookies)
    } else {
      Logger.w(TAG, "addCloudFlareCookie('${url}') cookie is null or empty: '${cloudFlareCookies}'")
    }
  }

  fun getCloudFlareCookies(url: HttpUrl): String? {
    val domainOrHost = url.domainOrHost()

    return site
      .getSettingBySettingId<MapSetting>(SiteSetting.SiteSettingId.CloudFlareClearanceCookie)
      ?.get(domainOrHost)
  }

  companion object {
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