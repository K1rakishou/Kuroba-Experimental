package com.github.k1rakishou.chan.features.webview.task

import android.webkit.CookieManager
import android.webkit.WebView
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.features.webview.WebViewTaskResult
import com.github.k1rakishou.chan.features.webview.client.AbstractCookieWebViewClient
import com.github.k1rakishou.chan.features.webview.client.AbstractWebViewClient
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.v2.parameters.RemoteImageSearchSettings
import kotlinx.coroutines.CompletableDeferred

class YandexCaptchaTask(
  headerTitleText: String?,
  loadable: AbstractWebViewTask.Loadable.Url,
  invokerWaiter: CompletableDeferred<WebViewTaskResult>
) : AbstractCookieWebViewTask(
  headerTitleText = headerTitleText,
  loadable = loadable,
  // IIRC, Yandex captcha might require user input
  headlessMaxTime = 5_000L,
  invisibleMaxTime = 0L,
  invokerWaiter = invokerWaiter
) {
  override val tag: String = TAG

  override fun createWebClient(): AbstractWebViewClient {
    return YandexCaptchaTaskWebViewClient(
      loadableUrl = loadable as Loadable.Url,
      cookieManager = cookieManager,
      webViewClientResultWaiter = this@YandexCaptchaTask.webViewClientResultWaiter
    )
  }

  override suspend fun persistCookies(site: Site, cookies: String, userData: Any?) {
    kurobaSettings.internal.remoteImageSearchSettings.read().update(
      internalSettings = kurobaSettings.internal,
      instanceType = RemoteImageSearchSettings.InstanceType.Yandex,
      updater = { settings -> settings.copy(cookies = cookies ) },
      creator = {
        RemoteImageSearchSettings.InstanceSettings(
          instanceType = RemoteImageSearchSettings.InstanceType.Yandex,
          baseUrl = (loadable as Loadable.Url).url.toString(),
          cookies = null
        )
      }
    )
  }

  private class YandexCaptchaTaskWebViewClient(
    private val loadableUrl: AbstractWebViewTask.Loadable.Url,
    private val cookieManager: CookieManager,
    webViewClientResultWaiter: CompletableDeferred<WebViewTaskResult>
  ) : AbstractCookieWebViewClient(webViewClientResultWaiter) {
    private var captchaPageLoaded = false

    override fun onPageFinished(view: WebView?, url: String?) {
      super.onPageFinished(view, url)

      if (url == null) {
        return
      }

      val cookie = cookieManager.getCookie(loadableUrl.url.toString())
        ?.takeIf { cookie -> cookie.isNotNullNorBlank() }
        ?: return

      if (url.contains("https://yandex.com/showcaptcha")) {
        captchaPageLoaded = true
      }

      if (captchaPageLoaded && url.contains("https://yandex.com/images/")) {
        success(cookie, null)
        return
      }
    }
  }

  companion object {
    private const val TAG = "DvachAntispamTask"
  }
}