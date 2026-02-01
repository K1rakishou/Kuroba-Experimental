package com.github.k1rakishou.chan.features.webview.task

import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.annotation.CallSuper
import com.github.k1rakishou.chan.core.concurrency.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.features.webview.WebViewLastTouchPositionHolder
import com.github.k1rakishou.chan.features.webview.WebViewTaskResult
import com.github.k1rakishou.chan.features.webview.client.AbstractWebViewClient
import com.github.k1rakishou.chan.utils.ViewUtils.emulateMotionEvent
import com.github.k1rakishou.chan.utils.appDependencies
import com.github.k1rakishou.common.CookieBuilder
import com.github.k1rakishou.common.awaitSilently
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import okhttp3.HttpUrl
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

abstract class AbstractWebViewTask(
  val headerTitleText: String?,
  val loadable: Loadable,
  val headlessMaxTime: Long,
  val invisibleMaxTime: Long,
  val invokerWaiter: CompletableDeferred<WebViewTaskResult>
) {
  val cookieManager by lazy { CookieManager.getInstance()!! }
  val webViewClient by lazy { createWebClient() }
  val appScope by lazy { appDependencies().appScope }
  val siteResolver by lazy { appDependencies().siteResolver }
  val webViewLastTouchPositionHolder by lazy { appDependencies().webViewLastTouchPositionHolder }

  val webViewClientResultWaiter = CompletableDeferred<WebViewTaskResult>()
  private val _webViewAttachedToViewAndDrawnWaiter = CompletableDeferred<Unit>()
  private val _performClickExecutor = RendezvousCoroutineExecutor(appScope)

  private val _initialized = AtomicBoolean(false)
  private val _destroyed = AtomicBoolean(false)

  private val _started = AtomicBoolean(false)
  val started: Boolean
    get() = _started.get()

  private val _performingAutoClick = AtomicBoolean(false)

  // Cookies before loading the page. Once the page loads, there is no way to get the cookies from WebView, and it's
  // impossible to tell if cookies were updated in CookieManager. But in some cases we need to know when the cookies
  // were updated (like when doing CloudFlare check). So the solution is to compare the initial cookies and cookies
  // after the page is loaded and retry until they are different.
  protected val initialCookies = AtomicReference<String>("")

  protected abstract val tag: String
  protected open val doAutoClickLastTouchPosition: Boolean = false

  val taskId: String = this::class.java.simpleName

  // Unique WebView task means that when launched, we can't group them together by domain. Which means that
  // when one task is running and another launched at this time, only one will be actually performed but they both
  // will share the same result. For example, CloudFlare check is not unique because it's result (cookie) is shared for
  // the whole website domain, not just a single url.
  open val uniqueTask: Boolean = false

  fun canRunHeadlessly(): Boolean = headlessMaxTime > 0L
  fun canRunInvisibly(): Boolean = invisibleMaxTime > 0L

  @CallSuper
  open suspend fun init(webView: WebView) {
    if (!_initialized.compareAndSet(false, true)) {
      return
    }

    setupCookies()
  }

  suspend fun start(webView: WebView) {
    if (!_started.compareAndSet(false, true)) {
      return
    }

    webView.stopLoading()
    startTask(webView)
  }

  fun webViewAttachedToViewAndDrawn() {
    _webViewAttachedToViewAndDrawnWaiter.complete(Unit)
  }

  @CallSuper
  open fun destroy() {
    if (!_destroyed.compareAndSet(false, true)) {
      return
    }

    webViewLastTouchPositionHolder.persist()

    _performClickExecutor.stop()
    webViewClient.destroy()
    finishWithResult(WebViewTaskResult.Canceled)
  }

  suspend fun waitForResult(webView: WebView) {
    val taskResult = webViewClientResultWaiter.awaitSilently(WebViewTaskResult.Canceled)
    webView.stopLoading()

    try {
      handleResult(taskResult)
    } finally {
      finishWithResult(taskResult)
    }
  }

  fun finishWithResult(taskResult: WebViewTaskResult) {
    if (!webViewClientResultWaiter.isCompleted) {
      webViewClientResultWaiter.complete(taskResult)
    }

    if (!invokerWaiter.isCompleted) {
      invokerWaiter.complete(taskResult)
    }
  }

  abstract fun createWebClient(): AbstractWebViewClient
  abstract suspend fun startTask(webView: WebView)
  abstract suspend fun handleResult(taskResult: WebViewTaskResult)

  @CallSuper
  open fun onWebViewTouchAction(event: MotionEvent) {
    if (!doAutoClickLastTouchPosition) {
      Logger.verbose(tag) {
        "onWebViewTouchAction() taskId: '${taskId}', doAutoClickLastTouchPosition is false"
      }

      return
    }

    if (_performingAutoClick.get()) {
      Logger.verbose(tag) {
        "onWebViewTouchAction() skipping event because currently performing autoclick, taskId: '${taskId}'"
      }

      return
    }

    if (
      webViewClient.taskCompleted ||
      webViewClient.pageLoadState != AbstractWebViewClient.PageLoadState.Visible
    ) {
      Logger.verbose(tag) { "onWebViewTouchAction() skipping event " +
        "webViewClient.taskCompleted: ${webViewClient.taskCompleted}, " +
        "webViewClient.pageLoadState: ${webViewClient.pageLoadState}, " +
        "taskId: '${taskId}'"
      }
      return
    }

    if (event.action != MotionEvent.ACTION_DOWN && event.action != MotionEvent.ACTION_UP) {
      return
    }

    val siteName = extractSiteNameFromLoadable()
    if (siteName.isNullOrBlank()) {
      return
    }

    val touchPosition = WebViewLastTouchPositionHolder.TouchPosition(event.x, event.y)
    Logger.debug(tag) {
      "onWebViewTouchAction() updating last touch position (touchPosition: ${touchPosition}) for task '${taskId}'"
    }

    webViewLastTouchPositionHolder.update(
      taskId = taskId,
      siteName = siteName,
      touchPosition = touchPosition
    )
  }

  fun extractSiteNameFromLoadable(): String? {
    when (loadable) {
      is Loadable.Html -> {
        Logger.debug(tag) { "extractSiteNameFromLoadable() Loadable.HTML not supported" }
        return null
      }
      is Loadable.Url -> {
        val siteName = siteResolver.findSiteForUrl(loadable.url.toString())
          ?.siteDescriptor()
          ?.siteName

        if (siteName.isNullOrBlank()) {
          Logger.debug(tag) { "extractSiteNameFromLoadable() url: ${loadable.url} not supported" }
          return null
        }

        return siteName
      }
    }
  }

  protected fun performAutoClick(view: WebView) {
    if (!doAutoClickLastTouchPosition) {
      Logger.debug(tag) {
        "performAutoClick() taskId: '${taskId}', doAutoClickLastTouchPosition is false"
      }

      return
    }

    val siteName = extractSiteNameFromLoadable()
    if (siteName.isNullOrBlank()) {
      return
    }

    val touchPosition = webViewLastTouchPositionHolder.get(taskId, siteName)
      ?: return

    _performClickExecutor.post {
      Logger.debug(tag) {
        "performAutoClick() taskId: '${taskId}', siteName: ${siteName}, " +
          "touchPosition: ${touchPosition} waiting for webview to be attached to view and drawn"
      }

      _webViewAttachedToViewAndDrawnWaiter.awaitSilently()
      delay(100L)

      Logger.debug(tag) {
        "performAutoClick() taskId: '${taskId}', " +
          "siteName: ${siteName}, " +
          "touchPosition: ${touchPosition} is attached to view and drawn, performing click"
      }

      // Do 5 attempts
      repeat(5) {
        try {
          val downSuccess = run {
            _performingAutoClick.set(true)

            // -5px..+5px
            val positionDeltaX = (Random.nextFloat() - 0.5f) * 2f * 5f
            val positionDeltaY = (Random.nextFloat() - 0.5f) * 2f * 5f

            view.emulateMotionEvent(
              downTime = SystemClock.uptimeMillis(),
              action = MotionEvent.ACTION_DOWN,
              x = touchPosition.x + positionDeltaX,
              y = touchPosition.y + positionDeltaY
            )

            awaitFrame()
            _performingAutoClick.set(false)
          }

          val pressDuration = ViewConfiguration.getPressedStateDuration()
          delay(Random.nextLong(pressDuration + 20L, pressDuration + 100L))

          val upSuccess = run {
            _performingAutoClick.set(true)

            // -5px..+5px
            val positionDeltaX = (Random.nextFloat() - 0.5f) * 2f * 5f
            val positionDeltaY = (Random.nextFloat() - 0.5f) * 2f * 5f

            view.emulateMotionEvent(
              downTime = SystemClock.uptimeMillis(),
              action = MotionEvent.ACTION_UP,
              x = touchPosition.x + positionDeltaX,
              y = touchPosition.y + positionDeltaY
            )

            awaitFrame()
            _performingAutoClick.set(false)
          }

          Logger.debug(tag) {
            "performAutoClick() taskId: '${taskId}, siteName: ${siteName} " +
              "downSuccess: ${downSuccess}, upSuccess: ${upSuccess}"
          }
        } finally {
          _performingAutoClick.set(false)
        }

        delay(Random.nextLong(300, 500))
      }
    }
  }

  private fun setupCookies() {
    when (loadable) {
      is Loadable.Url -> {
        val urlToOpenString = loadable.url.toString()

        val siteRequestModifier = siteResolver.findSiteForUrl(urlToOpenString)?.requestModifier()
        if (siteRequestModifier != null) {
          val cookieBuilder = CookieBuilder()
          siteRequestModifier.modifyCookieBuilder(loadable.url, cookieBuilder)

          val builtCookies = cookieBuilder.build()
          if (builtCookies.isNotBlank()) {
            cookieManager.setCookie(urlToOpenString, builtCookies)
          }

          initialCookies.set(builtCookies)
        }
      }
      is Loadable.Html -> {
        // no-op
      }
    }
  }

  sealed interface Loadable {
    val readableDescription: String
      get() {
        return when (this) {
          is Url -> url.toString()
          is Html -> "HTML"
        }
      }

    data class Url(
      val url: HttpUrl
    ) : Loadable

    data class Html(
      val baseUrl: HttpUrl,
      val html: String
    ) : Loadable
  }
}