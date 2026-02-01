package com.github.k1rakishou.chan.features.webview

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.annotation.GuardedBy
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.features.webview.task.AbstractWebViewTask
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class HeadlessWebViewTaskExecutor(
  private val appContext: Context,
  private val globalUiStateHolder: GlobalUiStateHolder
) {
  private val _mutex = Mutex()

  @Suppress("ForbiddenComment")
  // TODO: automatically destroy WebView when nothing accesses it anymore
  @GuardedBy("mutex")
  private var _currentActiveWebView: WebView? = null

  val cookieManager by lazy { CookieManager.getInstance()!! }

  suspend fun getOrCreateWebView(): WebView {
    return withContext(Dispatchers.Main) {
      return@withContext _mutex.withLock {
        if (_currentActiveWebView == null) {
          _currentActiveWebView = createWebView()
        }

        return@withLock _currentActiveWebView!!
      }
    }
  }

  suspend fun tryExecuteTaskHeadlessly(webViewTask: AbstractWebViewTask) {
    return withContext(Dispatchers.Main) {
      if (!webViewTask.canRunHeadlessly()) {
        return@withContext
      }

      val webView = getOrCreateWebView()
      webView.webViewClient = webViewTask.webViewClient
      webViewTask.init(webView)
      webViewTask.start(webView)

      try {
        Logger.debug(TAG) { "Task '${webViewTask.taskId}' started" }
        withTimeout(webViewTask.headlessMaxTime) { webViewTask.webViewClientResultWaiter.await() }
        Logger.debug(TAG) { "Task '${webViewTask.taskId}' ended normally" }
      } catch (ignored: Throwable) {
        Logger.debug(TAG) { "Task '${webViewTask.taskId}' timed out" }
        return@withContext
      }

      webViewTask.waitForResult(webView)
      webViewTask.destroy()
      return@withContext
    }
  }

  private suspend fun createWebView(): WebView {
    removeAllCookies()

    val webView = WebView(appContext, null, android.R.attr.webViewStyle)
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webView, true)

    val webSettings = webView.settings
    webSettings.javaScriptEnabled = true
    webSettings.domStorageEnabled = true
    webSettings.databaseEnabled = true
    webSettings.useWideViewPort = true
    webSettings.loadWithOverviewMode = true
    webSettings.cacheMode = WebSettings.LOAD_DEFAULT

    ChanSettings.customUserAgent.get()
      .takeIf { customUserAgent -> customUserAgent.isNotBlank() }
      ?.let { customUserAgent -> webSettings.userAgentString = customUserAgent }

    val measureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    webView.measure(measureSpec, measureSpec)

    val windowSize = globalUiStateHolder.mainUi.windowSize.value
    if (windowSize == null) {
      error("windowSize is null!")
    }

    val width = (windowSize.width * 0.85f).toInt().coerceAtMost(1080)
    val height = (windowSize.height * 0.7f).toInt().coerceAtMost(1920)
    webView.layoutParams = ViewGroup.LayoutParams(width, height)
    webView.layout(0, 0, webView.layoutParams.width, webView.layoutParams.height)

    return webView
  }

  private suspend fun removeAllCookies() {
    suspendCancellableCoroutine { cont ->
      cookieManager.removeAllCookies {
        Logger.debug(TAG) { "cookieManager.removeAllCookies()" }
        cont.resumeValueSafe(Unit)
      }
    }
  }

  companion object {
    private const val TAG = "HeadlessWebViewTaskExecutor"
  }
}