package com.github.k1rakishou.chan.features.bypass

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.ui.controller.BaseFloatingController
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableButton
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.prefs.StringSetting
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import javax.inject.Inject

class SiteFirewallBypassController(
  context: Context,
  private val firewallType: FirewallType,
  private val urlToOpen: String,
  private val onResult: (CookieResult) -> Unit
) : BaseFloatingController(context) {

  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var siteResolver: SiteResolver

  private lateinit var webView: WebView
  private lateinit var closeButton: ColorizableButton

  private var resultNotified = false

  private val cookieResultCompletableDeferred = CompletableDeferred<CookieResult>()
  private val cookieManager by lazy { CookieManager.getInstance() }
  private val webClient by lazy { createWebClient(firewallType) }

  private fun createWebClient(mode: FirewallType): BypassWebClient {
    return when (mode) {
      FirewallType.Cloudflare -> {
        CloudFlareCheckBypassWebClient(
          originalRequestUrlHost = urlToOpen,
          cookieManager = cookieManager,
          cookieResultCompletableDeferred = cookieResultCompletableDeferred
        )
      }
      FirewallType.DvachAntiSpam -> {
        DvachAntiSpamCheckBypassWebClient(
          originalRequestUrlHost = urlToOpen,
          cookieManager = cookieManager,
          cookieResultCompletableDeferred = cookieResultCompletableDeferred
        )
      }
    }
  }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun getLayoutId(): Int = R.layout.controller_firewall_bypass

  override fun onCreate() {
    try {
      // Some users may have no WebView installed so this two methods may throw an exception
      super.onCreate()

      onCreateInternal()
    } catch (error: Throwable) {
      onResult(CookieResult.Error(BypassExceptions(error.errorMessageOrClassName())))
      pop()
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    webClient.destroy()
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

    closeButton = view.findViewById(R.id.close_button)
    closeButton.setOnClickListener {
      pop()
    }

    webView.stopLoading()
    webView.clearCache(true)
    webView.clearFormData()
    webView.clearHistory()
    webView.clearMatches()

    val webViewDatabase = WebViewDatabase.getInstance(context.applicationContext)
    webViewDatabase.clearFormData()
    webViewDatabase.clearHttpAuthUsernamePassword()
    WebStorage.getInstance().deleteAllData()

    cookieManager.removeAllCookie()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webView, true)

    val webSettings: WebSettings = webView.settings
    webSettings.javaScriptEnabled = true
    webSettings.useWideViewPort = true
    webSettings.loadWithOverviewMode = true
    webSettings.userAgentString = appConstants.userAgent
    webSettings.cacheMode = WebSettings.LOAD_NO_CACHE
    webSettings.domStorageEnabled = true
    webSettings.databaseEnabled = true

    webView.webViewClient = webClient
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
      CookieResult.NotSupported -> {
        Logger.e(TAG, "NotSupported")
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

    when (firewallType) {
      FirewallType.Cloudflare -> {
        val cloudFlareClearanceCookieSetting = site.getSettingBySettingId<StringSetting>(
          SiteSetting.SiteSettingId.CloudFlareClearanceCookie
        )

        if (cloudFlareClearanceCookieSetting == null) {
          Logger.e(TAG, "Failed to find setting with key CloudFlareClearanceKey")
          return false
        }

        cloudFlareClearanceCookieSetting.set(cookie)
      }
      FirewallType.DvachAntiSpam -> {
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
    const val MAX_PAGE_LOADS_COUNT = 10
  }
}