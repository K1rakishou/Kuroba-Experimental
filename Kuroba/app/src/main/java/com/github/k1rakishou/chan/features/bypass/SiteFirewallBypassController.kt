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
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.ui.controller.BaseFloatingController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.FirewallType
import com.github.k1rakishou.common.domain
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.prefs.MapSetting
import com.github.k1rakishou.prefs.StringSetting
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class SiteFirewallBypassController(
  context: Context,
  val firewallType: FirewallType,
  private val headerTitleText: String?,
  private val urlToOpen: String,
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
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun onCreateInternal() {
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

    val clickableArea = view.findViewById<ConstraintLayout>(R.id.clickable_area)
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

    cookieManager.removeAllCookie()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webView, true)

    val webSettings: WebSettings = webView.settings
    webSettings.javaScriptEnabled = true
    webSettings.domStorageEnabled = true
    webSettings.databaseEnabled = true
    webSettings.useWideViewPort = true
    webSettings.loadWithOverviewMode = true
    webSettings.cacheMode = WebSettings.LOAD_DEFAULT
    webSettings.userAgentString = appConstants.userAgent

    val siteRequestModifier = siteResolver.findSiteForUrl(urlToOpen)?.requestModifier()
    if (siteRequestModifier != null) {
      siteRequestModifier.modifyWebView(webView)
    }

    webView.webViewClient = webClient
    webView.loadUrl(urlToOpen)

    onThemeChanged()

    mainScope.launch {
      waitAndHandleResult()
    }
  }

  private suspend fun waitAndHandleResult() {
    val informationDialogJob = mainScope.launch {
      if (!informationDialogShown.compareAndSet(false, true)) {
        // Dialog was already shown during this app launch
        return@launch
      }

      delay(20_000L)
      ensureActive()

      dialogFactory.createSimpleInformationDialog(
        context = context,
        titleText = getString(R.string.firewall_check_takes_too_long_title),
        descriptionText = getString(R.string.firewall_check_takes_too_long_description)
      )
    }

    val autoCloseJob = mainScope.launch {
      delay(AppConstants.FIREWALL_SCREEN_AUTO_CLOSE_TIMEOUT_MILLIS)
      ensureActive()

      showToast(getString(R.string.firewall_check_autoclosed))
      notifyAboutResult(CookieResult.Canceled)
    }

    val cookieResult = try {
      cookieResultCompletableDeferred.await()
    } finally {
      informationDialogJob.cancel()
      autoCloseJob.cancel()
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

    private val informationDialogShown = AtomicBoolean(false)
  }
}