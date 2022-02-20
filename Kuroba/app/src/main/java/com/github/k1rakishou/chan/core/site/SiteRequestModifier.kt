/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.core.site

import android.webkit.WebView
import androidx.annotation.CallSuper
import com.github.k1rakishou.chan.core.site.http.HttpCall
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.Request

abstract class SiteRequestModifier<T : Site>(
  protected val site: T,
  protected val appConstants: AppConstants
) {

  @CallSuper
  open fun modifyHttpCall(httpCall: HttpCall, requestBuilder: Request.Builder) {
    if (httpCall.site.siteDescriptor().is4chan()) {
      requestBuilder.addHeader(userAgentHeaderKey, appConstants.kurobaExCustomUserAgent)
    } else {
      requestBuilder.addHeader(userAgentHeaderKey, appConstants.userAgent)
    }

    requestBuilder.addHeader(acceptEncodingHeaderKey, gzipHeaderValue)
  }

  @CallSuper
  open fun modifyWebView(webView: WebView) {
  }

  @CallSuper
  open fun modifyThumbnailGetRequest(site: T, requestBuilder: Request.Builder) {
    requestBuilder.addHeader(userAgentHeaderKey, appConstants.userAgent)
    requestBuilder.addHeader(acceptEncodingHeaderKey, gzipHeaderValue)
  }

  @CallSuper
  open fun modifyCatalogOrThreadGetRequest(
    site: T,
    chanDescriptor: ChanDescriptor,
    requestBuilder: Request.Builder
  ) {
    requestBuilder.addHeader(userAgentHeaderKey, appConstants.userAgent)
    requestBuilder.addHeader(acceptEncodingHeaderKey, gzipHeaderValue)
  }

  @CallSuper
  open fun modifyFullImageHeadRequest(
    site: T,
    requestBuilder: Request.Builder
  ) {
    requestBuilder.addHeader(userAgentHeaderKey, appConstants.userAgent)
  }

  @CallSuper
  open fun modifyFullImageGetRequest(
    site: T,
    requestBuilder: Request.Builder
  ) {
    requestBuilder.addHeader(userAgentHeaderKey, appConstants.userAgent)
    requestBuilder.addHeader(acceptEncodingHeaderKey, gzipHeaderValue)
  }

  @CallSuper
  open fun modifyMediaDownloadRequest(
    site: T,
    requestBuilder: Request.Builder
  ) {
    requestBuilder.addHeader(userAgentHeaderKey, appConstants.userAgent)
    requestBuilder.addHeader(acceptEncodingHeaderKey, gzipHeaderValue)
  }

  @CallSuper
  open fun modifyVideoStreamRequest(
    site: T,
    requestProperties: MutableMap<String, String>
  ) {
    requestProperties.put(userAgentHeaderKey, appConstants.userAgent)
    requestProperties.put(acceptEncodingHeaderKey, gzipHeaderValue)
  }

  @CallSuper
  open fun modifyArchiveGetRequest(site: T, requestBuilder: Request.Builder) {
    requestBuilder.addHeader(userAgentHeaderKey, appConstants.userAgent)
    requestBuilder.addHeader(acceptEncodingHeaderKey, gzipHeaderValue)
  }

  @CallSuper
  open fun modifySearchGetRequest(site: T, requestBuilder: Request.Builder) {
    requestBuilder.addHeader(userAgentHeaderKey, appConstants.userAgent)
    requestBuilder.addHeader(acceptEncodingHeaderKey, gzipHeaderValue)
  }

  @CallSuper
  open fun modifyCaptchaGetRequest(site: T, requestBuilder: Request.Builder) {
    requestBuilder.addHeader(userAgentHeaderKey, appConstants.userAgent)
    requestBuilder.addHeader(acceptEncodingHeaderKey, gzipHeaderValue)
  }

  @CallSuper
  open fun modifyPostReportRequest(site: T, requestBuilder: Request.Builder) {
    requestBuilder.addHeader(userAgentHeaderKey, appConstants.userAgent)
    requestBuilder.addHeader(acceptEncodingHeaderKey, gzipHeaderValue)
  }

  @CallSuper
  open fun modifyLoginRequest(site: T, requestBuilder: Request.Builder) {
    requestBuilder.addHeader(userAgentHeaderKey, appConstants.userAgent)
    requestBuilder.addHeader(acceptEncodingHeaderKey, gzipHeaderValue)
  }

  @CallSuper
  open fun modifyGetPasscodeInfoRequest(site: T, requestBuilder: Request.Builder) {
    requestBuilder.addHeader(userAgentHeaderKey, appConstants.userAgent)
    requestBuilder.addHeader(acceptEncodingHeaderKey, gzipHeaderValue)
  }

  companion object {
    val userAgentHeaderKey = "User-Agent"
    val acceptEncodingHeaderKey = "Accept-Encoding"
    val gzipHeaderValue = "gzip"

    fun Request.Builder.addDefaultHeaders(appConstants: AppConstants): Request.Builder {
      this.addHeader(userAgentHeaderKey, appConstants.userAgent)
      this.addHeader(acceptEncodingHeaderKey, gzipHeaderValue)

      return this
    }
  }

}