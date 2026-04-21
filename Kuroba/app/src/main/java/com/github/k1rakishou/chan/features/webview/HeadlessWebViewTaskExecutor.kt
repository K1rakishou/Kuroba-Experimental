package com.github.k1rakishou.chan.features.webview

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.features.webview.task.AbstractWebViewTask
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.v2.KurobaSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger

class HeadlessWebViewTaskExecutor(
  private val appContext: Context,
  private val kurobaSettings: KurobaSettings,
  private val appScope: CoroutineScope,
  private val globalUiStateHolder: GlobalUiStateHolder
) {
  private val _mutex = Mutex()

  @GuardedBy("_mutex")
  @Volatile
  private var _currentActiveWebView: WebView? = null
  @GuardedBy("_mutex")
  private var _destroyWebViewJob: Job? = null

  private val _currentActiveWebViewRefCount = AtomicInteger(0)

  val cookieManager by lazy { CookieManager.getInstance()!! }

  fun acquireWebView() {
    runBlocking {
      _mutex.withLock {
        _destroyWebViewJob?.cancel()
        _destroyWebViewJob = null
      }
    }

    val refCount = _currentActiveWebViewRefCount.incrementAndGet()
    Logger.verbose(TAG) { "acquireWebView() currentActiveWebViewRefCount: ${refCount}" }
  }

  fun releaseWebView() {
    val refCount = _currentActiveWebViewRefCount.decrementAndGet()
    Logger.verbose(TAG) { "releaseWebView() currentActiveWebViewRefCount: ${refCount}" }

    if (refCount != 0 || _currentActiveWebView == null) {
      return
    }

    val job = appScope.launch(start = CoroutineStart.LAZY) {
      Logger.debug(TAG) { "releaseWebView() attempt to destroy WebView started, waiting 30 seconds..." }

      try {
        delay(30_000L)
      } catch (error: Throwable) {
        Logger.debug(TAG) { "releaseWebView() attempt to destroy WebView started, waiting... canceled" }
        throw error
      }

      Logger.debug(TAG) { "releaseWebView() attempt to destroy WebView started, waiting... done!" }

      _mutex.withLock {
        if (_currentActiveWebViewRefCount.get() == 0) {
          Logger.debug(TAG) { "releaseWebView() no references to WebView detected, destroying WebView..." }
          _currentActiveWebView?.destroy()
          _currentActiveWebView = null
          Logger.debug(TAG) { "releaseWebView() no references to WebView detected, destroying WebView... done" }
        }
      }
    }

    runBlocking {
      _mutex.withLock {
        _destroyWebViewJob?.cancel()
        _destroyWebViewJob = job
        job.start()
      }
    }
  }

  suspend fun doWithWebView(func: suspend (WebView) -> Unit) {
    try {
      val webView = getOrCreateWebView()

      acquireWebView()
      func(webView)
    } finally {
      releaseWebView()
    }
  }

  suspend fun tryExecuteTaskHeadlessly(webViewTask: AbstractWebViewTask) {
    return withContext(Dispatchers.Main) {
      if (!webViewTask.canRunHeadlessly()) {
        return@withContext
      }

      doWithWebView { webView ->
        // Wrap the task-provided WebViewClient so that SSL errors are always rejected,
        // regardless of how individual tasks implement (or forget to implement)
        // onReceivedSslError. This prevents a malicious or misconfigured task from
        // silently accepting an invalid certificate (CWE-295).
        webView.webViewClient = SecureSslWebViewClient(webViewTask.webViewClient)
        webViewTask.init(webView)
        webViewTask.start(webView)

        try {
          Logger.debug(TAG) { "Task '${webViewTask.taskId}' started" }
          withTimeout(webViewTask.headlessMaxTime) { webViewTask.webViewClientResultWaiter.await() }
          Logger.debug(TAG) { "Task '${webViewTask.taskId}' ended normally" }
        } catch (ignored: Throwable) {
          Logger.debug(TAG) { "Task '${webViewTask.taskId}' timed out" }
          return@doWithWebView
        }

        webViewTask.waitForResult(webView)
        webViewTask.destroy()
      }
      return@withContext
    }
  }

  suspend fun getOrCreateWebView(): WebView {
    return withContext(Dispatchers.Main) {
      return@withContext _mutex.withLock {
        if (_currentActiveWebView == null) {
          Logger.debug(TAG) { "getOrCreateWebView() creating a new WebView" }
          _currentActiveWebView = createWebView()
        } else {
          Logger.verbose(TAG) {
            "getOrCreateWebView() WebView is already created, refCount: ${_currentActiveWebViewRefCount.get()}"
          }
        }

        return@withLock _currentActiveWebView!!
      }
    }
  }

  private suspend fun createWebView(): WebView {
    removeAllCookies()

    val webView = WebView(appContext, null, android.R.attr.webViewStyle)
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webView, true)

    val webSettings = webView.settings
    configureWebSettings(webSettings)

    kurobaSettings.application.customUserAgent.read()
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

  // JavaScript is required for the headless tasks (e.g. Cloudflare bypass) to work,
  // so the lint warning is intentionally suppressed. The remaining settings explicitly
  // disable risky capabilities that the WebView ships with by default.
  @SuppressLint("SetJavaScriptEnabled")
  private fun configureWebSettings(webSettings: WebSettings) {
    webSettings.javaScriptEnabled = true
    webSettings.domStorageEnabled = true
    webSettings.databaseEnabled = true
    webSettings.useWideViewPort = true
    webSettings.loadWithOverviewMode = true
    webSettings.cacheMode = WebSettings.LOAD_DEFAULT

    // Disable filesystem and content provider access from web content. The headless
    // tasks only ever load remote http(s) URLs, so granting them access to local
    // resources only widens the attack surface (CWE-200, CWE-919).
    webSettings.allowFileAccess = false
    webSettings.allowContentAccess = false
    @Suppress("DEPRECATION")
    webSettings.allowFileAccessFromFileURLs = false
    @Suppress("DEPRECATION")
    webSettings.allowUniversalAccessFromFileURLs = false

    // Block mixed content (http resources loaded from an https page). Allowing it
    // would defeat the purpose of using TLS in the first place (CWE-319).
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    }

    // Make sure Safe Browsing stays on where it is supported (it is on by default
    // on API 26+, but be explicit to guard against OEM customisations).
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      webSettings.safeBrowsingEnabled = true
    }
  }

  private suspend fun removeAllCookies() {
    suspendCancellableCoroutine { cont ->
      cookieManager.removeAllCookies {
        Logger.debug(TAG) { "cookieManager.removeAllCookies()" }
        cont.resumeValueSafe(Unit)
      }
    }
  }

  /**
   * A [WebViewClient] decorator that delegates every callback to a wrapped client
   * but enforces a strict SSL error policy: invalid certificates are always
   * rejected, the offending request is cancelled and the failure is logged.
   *
   * This guarantees that SSL validation cannot be silently bypassed by a task
   * that overrides [WebViewClient.onReceivedSslError] and calls
   * [SslErrorHandler.proceed] (which is a well known WebView anti-pattern,
   * CWE-295 "Improper Certificate Validation").
   */
  private class SecureSslWebViewClient(
    private val delegate: WebViewClient
  ) : WebViewClient() {

    override fun onReceivedSslError(
      view: WebView?,
      handler: SslErrorHandler?,
      error: SslError?
    ) {
      Logger.error(TAG) {
        "onReceivedSslError() rejecting connection to '${error?.url}', " +
          "primaryError=${error?.primaryError}"
      }
      handler?.cancel()
    }

    override fun shouldOverrideUrlLoading(
      view: WebView?,
      request: android.webkit.WebResourceRequest?
    ): Boolean {
      return delegate.shouldOverrideUrlLoading(view, request)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
      delegate.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
      delegate.onPageFinished(view, url)
    }

    override fun onReceivedError(
      view: WebView?,
      request: android.webkit.WebResourceRequest?,
      error: android.webkit.WebResourceError?
    ) {
      delegate.onReceivedError(view, request, error)
    }

    override fun onReceivedHttpError(
      view: WebView?,
      request: android.webkit.WebResourceRequest?,
      errorResponse: android.webkit.WebResourceResponse?
    ) {
      delegate.onReceivedHttpError(view, request, errorResponse)
    }

    override fun shouldInterceptRequest(
      view: WebView?,
      request: android.webkit.WebResourceRequest?
    ): android.webkit.WebResourceResponse? {
      return delegate.shouldInterceptRequest(view, request)
    }
  }

  companion object {
    private const val TAG = "HeadlessWebViewTaskExecutor"
  }
}