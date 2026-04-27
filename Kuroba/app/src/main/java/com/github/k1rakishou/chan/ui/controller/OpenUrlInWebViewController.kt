package com.github.k1rakishou.chan.ui.controller

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.ProxyStorage
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.ui.controller.base.BaseFloatingController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.CookieBuilder
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl
import java.net.URI
import javax.inject.Inject

class OpenUrlInWebViewController(
  context: Context,
  val urlToOpen: HttpUrl
) : BaseFloatingController(context), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var appConstants: AppConstants

  @Inject
  lateinit var siteResolver: SiteResolver

  @Inject
  lateinit var themeEngine: ThemeEngine

  @Inject
  lateinit var proxyStorage: ProxyStorage

  private lateinit var webView: WebView
  private lateinit var closeButton: ImageView

  private val cookieManager by lazy { CookieManager.getInstance() }
  private val webViewClient by lazy { WebViewClient() }

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
        pop()
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    if (::webView.isInitialized) {
      webView.stopLoading()
    }

    if (::themeEngine.isInitialized) {
      themeEngine.removeListener(this)
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
  private suspend fun onCreateInternal() {
    // Issue #932: the Android WebView does not honor the proxies that
    // Kuroba routes its OkHttp traffic through. If a proxy is configured
    // for the site we are about to open, loading the URL in the WebView
    // would bypass that proxy and reveal the device IP. Warn the user
    // and let them choose to open it anyway or cancel.
    val proxiesForUrl = try {
      proxyStorage.getProxyByUri(URI(urlToOpen.toString()), ProxyStorage.ProxyActionType.SiteRequests)
    } catch (_: Throwable) {
      emptyList()
    }
    if (proxiesForUrl.isNotEmpty()) {
      Logger.warning(TAG) {
        "WebView for ${urlToOpen} would bypass the proxy configured for this site (issue #932)"
      }
      val openAnyway = suspendCancellableCoroutine<Boolean> { cont ->
        var resumed = false
        fun resumeOnce(value: Boolean) {
          if (resumed) {
            return
          }
          resumed = true
          cont.resumeValueSafe(value)
        }
        val handle = dialogFactory.createSimpleConfirmationDialog(
          context = context,
          titleTextId = R.string.open_url_in_webview_proxy_leak_title,
          descriptionTextId = R.string.open_url_in_webview_proxy_leak_description,
          positiveButtonText = getString(R.string.open_url_in_webview_proxy_leak_open_anyway),
          negativeButtonText = getString(R.string.cancel),
          onPositiveButtonClickListener = { resumeOnce(true) },
          onNegativeButtonClickListener = { resumeOnce(false) },
          onDismissListener = { resumeOnce(false) }
        )
        if (handle == null) {
          resumeOnce(false)
        }
      }
      if (!openAnyway) {
        pop()
        return
      }
    }

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

    val clickableArea = view.findViewById<View>(R.id.clickable_area)
    clickableArea.setOnClickListener { pop() }

    closeButton = view.findViewById(R.id.close_button)
    closeButton.setOnClickListener {
      pop()
    }

    webView.stopLoading()

    suspendCancellableCoroutine { cont ->
      cookieManager.removeAllCookies {
        Logger.debug(TAG) { "cookieManager.removeAllCookies()" }
        cont.resumeValueSafe(Unit)
      }
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

    kurobaSettings.application.customUserAgent.read()
      .takeIf { customUserAgent -> customUserAgent.isNotBlank() }
      ?.let { customUserAgent -> webSettings.userAgentString = customUserAgent }

    val urlToOpenString = urlToOpen.toString()

    val siteRequestModifier = siteResolver.findSiteForUrl(urlToOpenString)?.requestModifier
    if (siteRequestModifier != null) {
      val cookieManager = CookieManager.getInstance()
      val cookieBuilder = CookieBuilder()
      siteRequestModifier.modifyCookieBuilder(urlToOpen, cookieBuilder)

      val builtCookies = cookieBuilder.build()
      if (builtCookies.isNotBlank()) {
        cookieManager.setCookie(urlToOpen.toString(), builtCookies)
      }
    }

    webView.webViewClient = webViewClient
    webView.loadUrl(urlToOpenString)

    onThemeChanged()
  }

  companion object {
    private const val TAG = "OpenUrlInWebViewController"
  }

}