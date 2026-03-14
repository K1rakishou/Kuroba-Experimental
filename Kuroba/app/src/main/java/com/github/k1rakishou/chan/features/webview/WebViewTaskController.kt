package com.github.k1rakishou.chan.features.webview

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.features.webview.task.AbstractWebViewTask
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeClickableIcon
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.controller.base.BaseFloatingComposeController
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.resolveTextColor
import kotlinx.coroutines.delay
import javax.inject.Inject

class WebViewTaskController(
  context: Context,
  val webViewTask: AbstractWebViewTask,
) : BaseFloatingComposeController(context) {

  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var siteResolver: SiteResolver
  @Inject
  lateinit var headlessWebViewTaskExecutor: HeadlessWebViewTaskExecutor
  @Inject
  lateinit var webViewLastTouchPositionHolder: WebViewLastTouchPositionHolder

  override val currentlyInvisible: MutableState<Boolean> = mutableStateOf(webViewTask.canRunInvisibly())

  private var _webViewRef: WebView? = null

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    headlessWebViewTaskExecutor.acquireWebView()
  }

  override fun onDestroy() {
    super.onDestroy()

    _webViewRef?.let { webView -> (webView.parent as? ViewGroup)?.removeView(webView) }
    _webViewRef = null

    webViewTask.destroy()
    headlessWebViewTaskExecutor.releaseWebView()
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (currentlyInvisible.value) {
      return false
    }

    return super.onTouchEvent(event)
  }

  @Composable
  override fun BoxScope.BuildContent() {
    val chanTheme = LocalChanTheme.current
    val density = LocalDensity.current

    var currentWebViewMut by remember { mutableStateOf<WebView?>(null) }
    val currentWebView = currentWebViewMut

    var webViewSizeMut by remember { mutableStateOf<DpSize?>(null) }
    val webViewSize = webViewSizeMut

    LaunchedEffect(key1 = Unit) {
      val webView = try {
        headlessWebViewTaskExecutor.getOrCreateWebView()
      } catch (error: Throwable) {
        Logger.error(TAG, error) { "Error when trying to create the view" }

        val result = WebViewTaskResult.Error(WebViewTaskException(error.errorMessageOrClassName()))
        webViewTask.finishWithResult(result)

        pop()
        return@LaunchedEffect
      }

      webView.webViewClient = webViewTask.webViewClient
      _webViewRef = webView

      currentWebViewMut = webView
      webViewSizeMut = with(density) {
        DpSize(
          width = webView.layoutParams.width.toDp(),
          height = webView.layoutParams.height.toDp()
        )
      }
    }

    if (currentWebView == null || webViewSize == null) {
      return
    }

    var touchPositionMut by remember { mutableStateOf<WebViewLastTouchPositionHolder.TouchPosition?>(null) }
    val touchPosition = touchPositionMut

    LaunchedEffect(key1 = Unit) {
      val siteName = webViewTask.extractSiteNameFromLoadable()
      if (siteName.isNullOrBlank()) {
        return@LaunchedEffect
      }

      touchPositionMut = webViewLastTouchPositionHolder.get(webViewTask.taskId, siteName)
    }

    Column(
      modifier = Modifier
        .size(webViewSize)
        .pointerInteropFilter(
          onTouchEvent = {
            if (!webViewTask.performingAutoClick && currentlyInvisible.value) {
              currentlyInvisible.value = false
            }

            return@pointerInteropFilter false
          }
        )
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .drawWithContent {
          drawContent()

          if (touchPosition != null) {
            translate(left = -(size.width / 2f), top = -(size.height / 2f)) {
              translate(left = touchPosition.x, top = touchPosition.y) {
                drawCircle(color = Color.Magenta.copy(alpha = 0.75f), radius = 16f)
              }
            }
          }
        }
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .background(color = chanTheme.backColorSecondaryCompose),
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (webViewTask.headerTitleText.isNotNullNorBlank()) {
          Spacer(modifier = Modifier.size(8.dp))

          KurobaComposeText(
            text = webViewTask.headerTitleText,
            fontSize = 16.ktu,
            color = chanTheme.backColorSecondaryCompose.resolveTextColor()
          )

          Spacer(modifier = Modifier.size(8.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        KurobaComposeClickableIcon(
          modifier = Modifier.size(38.dp),
          drawableId = R.drawable.ic_clear_white_24dp,
          onClick = { pop() }
        )
      }

      AndroidView(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        factory = { currentWebView },
        update = { webView ->
          @SuppressLint("ClickableViewAccessibility")
          webView.setOnTouchListener { _, event ->
            if (!webViewTask.performingAutoClick && currentlyInvisible.value) {
              currentlyInvisible.value = false
              return@setOnTouchListener false
            }

            webViewTask.onWebViewTouchAction(event)
            return@setOnTouchListener false
          }
        }
      )
    }

    LaunchedEffect(key1 = Unit) {
      try {
        webViewTask.init(currentWebView)
        webViewTask.start(currentWebView)

        val id = -1234L
        currentWebView.postVisualStateCallback(id, object : WebView.VisualStateCallback() {
          override fun onComplete(requestId: Long) {
            if (id == requestId) {
              webViewTask.webViewAttachedToViewAndDrawn()
            }
          }
        })

        webViewTask.waitForResult(currentWebView)
      } finally {
        pop()
      }
    }

    if (webViewTask.canRunInvisibly()) {
      LaunchedEffect(key1 = Unit) {
        try {
          Logger.debug(TAG) { "Starting WebView in invisible mode..." }
          delay(webViewTask.invisibleMaxTime)
        } finally {
          currentlyInvisible.value = false
          Logger.debug(TAG) { "Starting WebView in invisible mode... done, switched to visible mode" }
        }
      }
    }
  }

  companion object {
    private const val TAG = "WebViewTaskController"
  }
}