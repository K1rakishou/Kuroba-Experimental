package com.github.k1rakishou.chan.features.webview

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.features.webview.task.AbstractWebViewTask
import com.github.k1rakishou.chan.ui.controller.BaseFloatingController
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import kotlinx.coroutines.launch
import javax.inject.Inject

class WebViewTaskController(
  context: Context,
  val webViewTask: AbstractWebViewTask,
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

        val result = WebViewTaskResult.Error(WebViewTaskException(error.errorMessageOrClassName()))
        webViewTask.finishWithResult(result)

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

    webViewTask.destroy()
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
    if (webViewTask.headerTitleText.isNullOrBlank()) {
      headerTitle.visibility = View.INVISIBLE
    } else {
      headerTitle.text = webViewTask.headerTitleText
      headerTitle.visibility = View.VISIBLE
    }

    webView.stopLoading()
    webViewTask.init(webView)
    webViewTask.start(webView)

    onThemeChanged()

    controllerScope.launch {
      webViewTask.waitForResult(webView)
      pop()
    }
  }

  companion object {
    private const val TAG = "WebViewTaskController"
  }
}