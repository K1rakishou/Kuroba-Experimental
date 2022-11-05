package com.github.k1rakishou.chan.features.bypass

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.ui.controller.BaseFloatingController
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableButton
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.FirewallType
import com.github.k1rakishou.common.domain
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.prefs.MapSetting
import com.github.k1rakishou.prefs.StringSetting
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

class SiteFirewallBypassController(
  context: Context,
  val firewallType: FirewallType,
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
      FirewallType.YandexSmartCaptcha -> {
        YandexSmartCaptchaCheckBypassWebClient(
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
    super.onCreate()

    // Add a frame delay for the navigation stuff to completely load
    mainScope.launch {
      try {
        // Some users may have no WebView installed
        onCreateInternal()
      } catch (error: Throwable) {
        Logger.e(TAG, "Error when trying to create the view", error)

        onResult(CookieResult.Error(BypassException(error.errorMessageOrClassName())))
        pop()
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    webClient.destroy()

    if (::webView.isInitialized) {
      webView.stopLoading()
    }

    if (!cookieResultCompletableDeferred.isCompleted) {
      cookieResultCompletableDeferred.complete(CookieResult.Canceled)
      notifyAboutResult(CookieResult.Canceled)
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun onCreateInternal() {
    val webViewContainer = view.findViewById<FrameLayout>(R.id.web_view_container)

    webView = WebView(context, null, android.R.attr.webViewStyle).apply {
      layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )

      isClickable = false
      isFocusable = false
    }

    webViewContainer.addView(webView)

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
    val job = mainScope.launch {
      delay(15_000L)
      showToast(R.string.firewall_check_takes_too_long, Toast.LENGTH_LONG)
    }

    val cookieResult = try {
      cookieResultCompletableDeferred.await()
    } finally {
      job.cancel()
    }

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
        val cloudFlareClearanceCookieSetting = site.getSettingBySettingId<MapSetting>(
          SiteSetting.SiteSettingId.CloudFlareClearanceCookie
        )

        if (cloudFlareClearanceCookieSetting == null) {
          Logger.e(TAG, "Failed to find setting with key CloudFlareClearanceKey")
          return false
        }

        val domainOrHost = urlToOpen.toHttpUrlOrNull()?.let { httpUrl ->
          httpUrl.domain() ?: httpUrl.host
        }

        if (domainOrHost.isNullOrEmpty()) {
          Logger.e(TAG, "Failed to extract neither domain not host from url '${urlToOpen}'")
          return false
        }

        cloudFlareClearanceCookieSetting.put(domainOrHost, cookie)
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
      FirewallType.YandexSmartCaptcha -> {
        // no-op
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
    private const val TAG = "SiteFirewallBypassController"
    const val MAX_PAGE_LOADS_COUNT = 10
  }
}