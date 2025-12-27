package com.github.k1rakishou.chan.features.bypass

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.ui.controller.BaseFloatingController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.CookieBuilder
import com.github.k1rakishou.common.FirewallType
import com.github.k1rakishou.common.domainOrHost
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.prefs.MapSetting
import com.github.k1rakishou.prefs.StringSetting
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

class SiteFirewallBypassController(
  context: Context,
  val firewallType: FirewallType,
  private val headerTitleText: String?,
  private val urlToOpen: HttpUrl,
  private val onResult: (CookieResult) -> Unit
) : BaseFloatingController(context), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var siteResolver: SiteResolver
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var dialogFactory: DialogFactory

  private lateinit var webView: WebView
  private lateinit var closeButton: ImageView
  private lateinit var headerTitle: TextView

  private var resultNotified = false

  private val cookieResultCompletableDeferred = CompletableDeferred<CookieResult>()
  private val cookieManager by lazy { CookieManager.getInstance() }
  private val webClient by lazy { createWebClient(firewallType) }
  private val initialCookies = AtomicReference<String>("")
  private val originalRequestUrlString = urlToOpen.toString()

  private fun createWebClient(mode: FirewallType): BypassWebClient {
    return when (mode) {
      FirewallType.Cloudflare -> {
        CloudFlareCheckBypassWebClient(
          originalRequestUrl = originalRequestUrlString,
          cookieManager = cookieManager,
          cookieResultCompletableDeferred = cookieResultCompletableDeferred,
          initialCookies = initialCookies
        )
      }
      FirewallType.YandexSmartCaptcha -> {
        YandexSmartCaptchaCheckBypassWebClient(
          originalRequestUrlHost = originalRequestUrlString,
          cookieManager = cookieManager,
          cookieResultCompletableDeferred = cookieResultCompletableDeferred
        )
      }
      FirewallType.DvachAntiSpam -> {
        DvachAntiSpamCheckBypassWebClient(
          cookieManager = cookieManager,
          cookieResultCompletableDeferred = cookieResultCompletableDeferred
        )
      }
    }
  }

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun getLayoutId(): Int = R.layout.controller_firewall_bypass

  override fun onCreate() {
    super.onCreate()

    // Add a frame delay for the navigation stuff to completely load
    controllerScope.launch {
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

    if (::themeEngine.isInitialized) {
      themeEngine.removeListener(this)
    }

    if (!cookieResultCompletableDeferred.isCompleted) {
      cookieResultCompletableDeferred.complete(CookieResult.Canceled)
      notifyAboutResult(CookieResult.Canceled)
    }
  }

  override fun onThemeChanged() {
    val tintedDrawable = themeEngine.tintDrawable(
      drawable = closeButton.drawable,
      isCurrentColorDark = ThemeEngine.isDarkColor(themeEngine.chanTheme.backColor)
    )

    closeButton.setImageDrawable(tintedDrawable)

    val textColor = ThemeEngine.resolveTextColor(themeEngine.chanTheme)
    headerTitle.setTextColor(textColor)
  }

  @SuppressLint("SetJavaScriptEnabled")
  private suspend fun onCreateInternal() {
    val webViewContainer = view.findViewById<FrameLayout>(R.id.web_view_container)

    themeEngine.addListener(this)

    webView = WebView(context, null, android.R.attr.webViewStyle).apply {
      layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )

      isClickable = false
      isFocusable = false
    }

    webViewContainer.addView(webView)

    val clickableArea = view.findViewById<FrameLayout>(R.id.clickable_area)
    clickableArea.setOnClickListener { pop() }

    closeButton = view.findViewById(R.id.close_button)
    closeButton.setOnClickListener {
      pop()
    }

    headerTitle = view.findViewById(R.id.header_title)
    if (headerTitleText.isNullOrBlank()) {
      headerTitle.visibility = View.INVISIBLE
    } else {
      headerTitle.text = headerTitleText
      headerTitle.visibility = View.VISIBLE
    }

    webView.stopLoading()

    if (ChanSettings.onlyRemoveExpiredWebviewCookies.get()) {
      Logger.debug(TAG) { "Removing expired cookies" }
      cookieManager.removeExpiredCookie()
    } else {
      Logger.debug(TAG) { "Removing all cookies" }
      suspendCancellableCoroutine { cont ->
        cookieManager.removeAllCookies { removed ->
          Logger.debug(TAG) { "cookieManager.removeAllCookies -> ${removed}" }
          cont.resumeValueSafe(Unit)
        }
      }
    }

    val siteRequestModifier = siteResolver.findSiteForUrl(originalRequestUrlString)?.requestModifier()
    if (siteRequestModifier != null) {
      siteRequestModifier.modifyWebView(webView, urlToOpen)
      initialCookies.set(cookieManager.getCookie(urlToOpen.toString()))
    }

    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webView, true)

    val webSettings: WebSettings = webView.settings
    webSettings.javaScriptEnabled = true
    webSettings.domStorageEnabled = true
    webSettings.databaseEnabled = true
    webSettings.useWideViewPort = true
    webSettings.loadWithOverviewMode = true
    webSettings.cacheMode = WebSettings.LOAD_DEFAULT

    ChanSettings.customUserAgent.get()
      .takeIf { customUserAgent -> customUserAgent.isNotBlank() }
      ?.let { customUserAgent -> webSettings.userAgentString = customUserAgent }

    webView.webViewClient = webClient
    webView.loadUrl(originalRequestUrlString)

    onThemeChanged()

    controllerScope.launch {
      waitAndHandleResult()
    }
  }

  private suspend fun waitAndHandleResult() {
    val autoCloseJob = controllerScope.launch {
      delay(AppConstants.FIREWALL_SCREEN_AUTO_CLOSE_TIMEOUT_MILLIS)
      ensureActive()

      showToast(getString(R.string.firewall_check_autoclosed))
      notifyAboutResult(CookieResult.Canceled)
    }

    val cookieResult = try {
      cookieResultCompletableDeferred.await()
    } finally {
      autoCloseJob.cancel()
    }

    webView.stopLoading()

    when (cookieResult) {
      is CookieResult.CookieValue -> {
        val cookieBuilder = CookieBuilder(cookieResult.cookie)

        val cookieParts = cookieBuilder.cookieParts()
        Logger.debug(TAG) { "waitAndHandleResult('${urlToOpen}') Success. cookieParts size: '${cookieParts.size}'" }

        cookieParts.forEach { cookiePart ->
          Logger.debug(TAG) { "waitAndHandleResult('${urlToOpen}') '${cookiePart.key}'='${cookiePart.value}'" }
        }

        addCookieToSiteSettings(cookieResult.cookie)
        delay(1000)
      }
      is CookieResult.Error -> {
        Logger.e(TAG, "waitAndHandleResult('${urlToOpen}') Error: ${cookieResult.exception.errorMessageOrClassName()}")
      }
      CookieResult.Canceled -> {
        Logger.e(TAG, "waitAndHandleResult('${urlToOpen}') Canceled")
      }
      CookieResult.NotSupported -> {
        Logger.e(TAG, "waitAndHandleResult('${urlToOpen}') NotSupported")
      }
    }

    notifyAboutResult(cookieResult)
  }

  private fun addCookieToSiteSettings(cookie: String): Boolean {
    val site = siteResolver.findSiteForUrl(urlToOpen.toString())
    if (site == null) {
      Logger.e(TAG, "Failed to find site for url: '${urlToOpen}'")
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

        if (originalRequestUrlString.isEmpty()) {
          Logger.e(TAG, "Failed to extract neither domain not host from url '${urlToOpen}'")
          return false
        }

        cloudFlareClearanceCookieSetting.put(key = urlToOpen.domainOrHost(), value = cookie, sync = true)
      }
      FirewallType.DvachAntiSpam -> {
        val dvachAntiSpamCookieSetting = site.getSettingBySettingId<StringSetting>(
          SiteSetting.SiteSettingId.DvachAntiSpamCookie
        )

        if (dvachAntiSpamCookieSetting == null) {
          Logger.e(TAG, "Failed to find setting with key DvachAntiSpamCookie")
          return false
        }

        dvachAntiSpamCookieSetting.setSync(cookie)
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