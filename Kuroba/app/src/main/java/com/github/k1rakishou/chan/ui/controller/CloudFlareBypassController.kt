package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.okhttp.CloudFlareHandlerInterceptor.Companion.CF_CLEARANCE
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.prefs.StringSetting
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import javax.inject.Inject

class CloudFlareBypassController(
  context: Context,
  private val originalRequestUrlHost: String,
  private val onResult: (CookieResult) -> Unit
) : BaseFloatingController(context) {

  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var siteResolver: SiteResolver

  private lateinit var webView: WebView

  private val cookieResultCompletableDeferred = CompletableDeferred<CookieResult>()

  private val cookieManager by lazy { CookieManager.getInstance() }
  private val webClient by lazy {
    CustomWebClient(originalRequestUrlHost, cookieManager, cookieResultCompletableDeferred)
  }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun getLayoutId(): Int = R.layout.controller_cloudflare_bypass

  override fun onCreate() {
    try {
      // Some users may have no WebView installed so this two methods may throw an exception
      super.onCreate()

      onCreateInternal()
    } catch (error: Throwable) {
      onResult(CookieResult.Error(CloudFlareBypassException(error.errorMessageOrClassName())))
      pop()
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    webView.stopLoading()

    if (!cookieResultCompletableDeferred.isCompleted) {
      cookieResultCompletableDeferred.complete(CookieResult.Canceled)
      notifyAboutResult(CookieResult.Canceled)
    }
  }

  private fun onCreateInternal() {
    webView = view.findViewById(R.id.web_view)

    val clickableArea = view.findViewById<ConstraintLayout>(R.id.clickable_area)
    clickableArea.setOnClickListener { pop() }

    val closableArea = view.findViewById<View>(R.id.closable_area)
    closableArea.setOnClickListener { pop() }

    val webSettings: WebSettings = webView.settings
    webSettings.javaScriptEnabled = true
    webSettings.useWideViewPort = true
    webSettings.loadWithOverviewMode = true
    webSettings.userAgentString = appConstants.userAgent
    webSettings.cacheMode = WebSettings.LOAD_DEFAULT
    webSettings.domStorageEnabled = true
    webSettings.databaseEnabled = true

    webView.webViewClient = webClient
    cookieManager.removeAllCookies(null)

    webView.loadUrl(originalRequestUrlHost)

    mainScope.launch {
      waitAndHandleResult()
    }
  }

  private suspend fun waitAndHandleResult() {
    val cookieResult = cookieResultCompletableDeferred.await()
    webView.stopLoading()

    when (cookieResult) {
      is CookieResult.CookieValue -> {
        Logger.d(TAG, "Success: ${cookieResult.cfClearanceValue}")

        addCookieToSiteSettings(cookieResult.cfClearanceValue)
      }
      is CookieResult.Error -> {
        Logger.e(TAG, "Error: ${cookieResult.exception.errorMessageOrClassName()}")
      }
      CookieResult.Canceled -> {
        Logger.e(TAG, "Canceled")
        return
      }
    }

    notifyAboutResult(cookieResult)
  }

  private fun addCookieToSiteSettings(cfClearanceValue: String): Boolean {
    val site = siteResolver.findSiteForUrl(originalRequestUrlHost)
    if (site == null) {
      Logger.e(TAG, "Failed to find site for url: '$originalRequestUrlHost'")
      return false
    }

    val cloudFlareClearanceCookieSetting = site.getSettingBySettingId<StringSetting>(
      SiteSetting.SiteSettingId.CloudFlareClearanceCookie
    )

    if (cloudFlareClearanceCookieSetting == null) {
      Logger.e(TAG, "Failed to find setting with key CloudFlareClearanceKey")
      return false
    }

    cloudFlareClearanceCookieSetting.set(cfClearanceValue)
    return true
  }

  private fun notifyAboutResult(cookieResult: CookieResult) {
    onResult(cookieResult)
    pop()
  }

  class CustomWebClient(
    private val originalRequestUrlHost: String,
    private val cookieManager: CookieManager,
    private val cookieResultCompletableDeferred: CompletableDeferred<CookieResult>
  ) : WebViewClient() {
    private var pageLoadsCounter = 0

    override fun onPageFinished(view: WebView?, url: String?) {
      super.onPageFinished(view, url)

      val cookies = cookieManager.getCookie(originalRequestUrlHost)
      if (!cookies.contains(CF_CLEARANCE)) {
        ++pageLoadsCounter

        if (pageLoadsCounter > MAX_PAGE_LOADS_COUNT) {
          fail(CloudFlareBypassException("Exceeded max page load limit"))
        }

        return
      }

      val actualCookie = cookies
        .split(";")
        .map { cookie -> cookie.trim() }
        .firstOrNull { cookie -> cookie.startsWith(CF_CLEARANCE) }
        ?.removePrefix("${CF_CLEARANCE}=")

      if (actualCookie == null) {
        fail(CloudFlareBypassException("No cf_clearance cookie found in result"))
        return
      }

      success(actualCookie)
    }

    override fun onReceivedError(
      view: WebView?,
      errorCode: Int,
      description: String?,
      failingUrl: String?
    ) {
      super.onReceivedError(view, errorCode, description, failingUrl)
      fail(CloudFlareBypassException(description ?: "Unknown error while trying to load CloudFlare page"))
    }

    private fun success(cookieValue: String) {
      if (cookieResultCompletableDeferred.isCompleted) {
        return
      }

      cookieResultCompletableDeferred.complete(CookieResult.CookieValue(cookieValue))
    }

    private fun fail(exception: CloudFlareBypassException) {
      if (cookieResultCompletableDeferred.isCompleted) {
        return
      }

      cookieResultCompletableDeferred.complete(CookieResult.Error(exception))
    }

  }

  class CloudFlareBypassException(message: String) : Exception(message)

  sealed class CookieResult {
    data class CookieValue(val cfClearanceValue: String) : CookieResult()
    data class Error(val exception: CloudFlareBypassException) : CookieResult()
    object Canceled : CookieResult()
  }

  companion object {
    private const val TAG = "CloudFlareBypassController"
    private const val MAX_PAGE_LOADS_COUNT = 5
  }
}