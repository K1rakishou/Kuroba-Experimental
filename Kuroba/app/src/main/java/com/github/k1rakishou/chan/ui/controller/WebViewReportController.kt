package com.github.k1rakishou.chan.ui.controller

import android.annotation.SuppressLint
import android.content.Context
import android.util.AndroidRuntimeException
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.features.toolbar.BackArrowMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMiddleContent
import com.github.k1rakishou.chan.features.toolbar.ToolbarText
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.CookieBuilder
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.util.ChanPostUtils.getTitle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject

class WebViewReportController(
  context: Context,
  private val post: ChanPost,
  private val site: Site
) : Controller(context), WindowInsetsListener {

  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private val cookieManager by lazy { CookieManager.getInstance() }

  private lateinit var frameLayout: FrameLayout

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate() {
    super.onCreate()

    toolbarState.enterDefaultMode(
      leftItem = BackArrowMenuItem(
        onClick = { requireNavController().popController() }
      ),
      middleContent = ToolbarMiddleContent.Title(
        title = ToolbarText.String(appResources.string(R.string.report_screen, getTitle(post, null)))
      )
    )

    controllerScope.launch {
      try {
        // Some users may have no WebView installed
        initUi()
      } catch (error: Throwable) {
        Logger.e(TAG, "Error when trying to create the view", error)
        requireNavController().popController()
        return@launch
      }

      controllerScope.launch {
        combine(
          globalUiStateHolder.toolbar.toolbarHeight,
          globalUiStateHolder.bottomPanel.bottomPanelHeight
        ) { t1, t2 -> t1 to t2 }
          .onEach { onInsetsChanged() }
          .collect()
      }

      onInsetsChanged()
      globalWindowInsetsManager.addInsetsUpdatesListener(this@WebViewReportController)
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
  }

  override fun onInsetsChanged() {
    val bottomPadding = with(appResources.composeDensity) {
      maxOf(
        globalWindowInsetsManager.bottom(),
        globalUiStateHolder.bottomPanel.bottomPanelHeight.value.roundToPx()
      )
    }

    val topPadding = with(appResources.composeDensity) {
      maxOf(
        globalWindowInsetsManager.top(),
        globalUiStateHolder.toolbar.toolbarHeight.value.roundToPx()
      )
    }

    frameLayout.updatePaddings(
      top = topPadding,
      bottom = bottomPadding
    )
  }

  private suspend fun initUi() {
    val urlToOpen = site.endpoints().report(post)
    if (urlToOpen == null) {
      requireNavController().popController()
      return
    }

    frameLayout = FrameLayout(context)
    frameLayout.setLayoutParams(
      LinearLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
    )

    try {
      val webView = WebView(context)
      val siteRequestModifier = site.requestModifier()

      suspendCancellableCoroutine { cont ->
        cookieManager.removeAllCookies {
          Logger.debug(TAG) { "cookieManager.removeAllCookies()" }
          cont.resumeValueSafe(Unit)
        }
      }

      if (siteRequestModifier != null) {
        val cookieManager = CookieManager.getInstance()
        val cookieBuilder = CookieBuilder()
        siteRequestModifier.modifyCookieBuilder(urlToOpen, cookieBuilder)

        val builtCookies = cookieBuilder.build()
        if (builtCookies.isNotBlank()) {
          cookieManager.setCookie(urlToOpen.toString(), builtCookies)
        }
      }

      val settings = webView.getSettings()
      settings.javaScriptEnabled = true
      settings.domStorageEnabled = true
      webView.loadUrl(urlToOpen.toString())

      frameLayout.addView(
        webView,
        FrameLayout.LayoutParams(
          FrameLayout.LayoutParams.MATCH_PARENT,
          FrameLayout.LayoutParams.MATCH_PARENT
        )
      )

      view = frameLayout
      onInsetsChanged()
    } catch (error: Throwable) {
      var errmsg = ""

      if (
        error is AndroidRuntimeException &&
        error.message != null &&
        error.message?.contains("MissingWebViewPackageException") == true
      ) {
        errmsg = appResources.string(R.string.fail_reason_webview_is_not_installed)
      } else {
        errmsg = appResources.string(
          R.string.fail_reason_some_part_of_webview_not_initialized,
          error.message ?: "Unknown error"
        )
      }

      view = AppModuleAndroidUtils.inflate(context, R.layout.layout_webview_error)
      view.findViewById<TextView>(R.id.text).text = errmsg
    }
  }

  companion object {
    private const val TAG = "WebViewReportController"
  }

}
