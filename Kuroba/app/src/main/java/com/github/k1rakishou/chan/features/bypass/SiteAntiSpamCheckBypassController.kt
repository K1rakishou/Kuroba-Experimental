package com.github.k1rakishou.chan.features.bypass

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.ui.controller.BaseFloatingController
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.prefs.StringSetting
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import javax.inject.Inject

class SiteAntiSpamCheckBypassController(
  context: Context,
  private val bypassMode: BypassMode,
  private val urlToOpen: String,
  private val onResult: (CookieResult) -> Unit
) : BaseFloatingController(context) {

  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var siteResolver: SiteResolver

  private lateinit var webView: WebView

  private var resultNotified = false

  private val cookieResultCompletableDeferred = CompletableDeferred<CookieResult>()

  private val cookieManager by lazy { CookieManager.getInstance() }
  private val webClient by lazy { createWebClient(bypassMode) }

  private fun createWebClient(mode: BypassMode): WebViewClient {
    return when (mode) {
      BypassMode.BypassCloudflare -> {
        CloudFlareCheckBypassWebClient(urlToOpen, cookieManager, cookieResultCompletableDeferred)
      }
      BypassMode.Bypass2chAntiSpamCheck -> {
        DvachAntiSpamCheckBypassWebClient(urlToOpen, cookieManager, cookieResultCompletableDeferred)
      }
    }
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

  @SuppressLint("SetJavaScriptEnabled")
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

    webView.loadUrl(urlToOpen)

    mainScope.launch {
      waitAndHandleResult()
    }
  }

  private suspend fun waitAndHandleResult() {
    val cookieResult = cookieResultCompletableDeferred.await()
    webView.stopLoading()

    when (cookieResult) {
      is CookieResult.CookieValue -> {
        Logger.d(TAG, "Success: ${cookieResult.cookie}")
        addCookieToSiteSettings(cookieResult.cookie)
      }
      is CookieResult.Error -> {
        Logger.e(TAG, "Error: ${cookieResult.exception.errorMessageOrClassName()}")
      }
      CookieResult.Canceled -> {
        Logger.e(TAG, "Canceled")
      }
    }

    notifyAboutResult(cookieResult)
  }

  private fun addCookieToSiteSettings(cookie: String): Boolean {
    val site = siteResolver.findSiteForUrl(urlToOpen)
    if (site == null) {
      Logger.e(TAG, "Failed to find site for url: '$urlToOpen'")
      return false
    }

    when (bypassMode) {
      BypassMode.BypassCloudflare -> {
        val cloudFlareClearanceCookieSetting = site.getSettingBySettingId<StringSetting>(
          SiteSetting.SiteSettingId.CloudFlareClearanceCookie
        )

        if (cloudFlareClearanceCookieSetting == null) {
          Logger.e(TAG, "Failed to find setting with key CloudFlareClearanceKey")
          return false
        }

        cloudFlareClearanceCookieSetting.set(cookie)
      }
      BypassMode.Bypass2chAntiSpamCheck -> {
        val dvachAntiSpamCookieSetting = site.getSettingBySettingId<StringSetting>(
          SiteSetting.SiteSettingId.DvachAntiSpamCookie
        )

        if (dvachAntiSpamCookieSetting == null) {
          Logger.e(TAG, "Failed to find setting with key DvachAntiSpamCookie")
          return false
        }

        dvachAntiSpamCookieSetting.set(cookie)
      }
    }

    return true
  }

  private fun notifyAboutResult(cookieResult: CookieResult) {
    if (!resultNotified) {
      resultNotified = true

      onResult(cookieResult)
      pop()
    }
  }

  companion object {
    private const val TAG = "CloudFlareBypassController"
    const val MAX_PAGE_LOADS_COUNT = 5
  }
}