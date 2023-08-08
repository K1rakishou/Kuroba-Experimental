package com.github.k1rakishou.chan.ui.controller

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewDatabase
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import kotlinx.coroutines.launch
import javax.inject.Inject

class OpenUrlInWebViewController(
    context: Context,
    val urlToOpen: String
) : BaseFloatingController(context), ThemeEngine.ThemeChangesListener {

    @Inject
    lateinit var appConstants: AppConstants
    @Inject
    lateinit var siteResolver: SiteResolver
    @Inject
    lateinit var themeEngine: ThemeEngine

    private lateinit var webView: WebView
    private lateinit var closeButton: ImageView

    private val cookieManager by lazy { CookieManager.getInstance() }
    private val webViewClient by lazy { WebViewClient() }

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

        webView.webViewClient = webViewClient
        webView.loadUrl(urlToOpen)

        onThemeChanged()
    }

    companion object {
        private const val TAG = "OpenUrlInWebViewController"
    }

}