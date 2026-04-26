package com.github.k1rakishou.chan.features

import com.github.k1rakishou.chan.core.concurrency.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.HttpUrl

class KurobaWebUrlRouter(
  private val appScope: CoroutineScope,
  private val siteResolver: SiteResolver
) {
  private val _duplicates = mutableSetOf<HttpUrl>()
  private val _executor = SerializedCoroutineExecutor(appScope)

  private val _routerEventFlow = MutableSharedFlow<Event>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.SUSPEND
  )
  val routerEventFlow: SharedFlow<Event>
    get() = _routerEventFlow.asSharedFlow()

  fun onUrlClicked(url: HttpUrl) {
    _executor.post {
      if (!_duplicates.add(url)) {
        // The same url was passed while the other was already being processed
        return@post
      }

      try {
        Logger.d(TAG, "onUrlClicked url: ${url}")

        val site = siteResolver.findSiteForUrl(url.toString())
        val fullPath = url.pathSegments.joinToString(separator = "/")

        val event = try {
          handleUrl(url, site, fullPath)
        } catch (error: Throwable) {
          Logger.error(TAG, error) { "Failed to handle url '${url}'" }
          return@post
        }

        _routerEventFlow.emit(event)
      } finally {
        _duplicates.remove(url)
      }
    }
  }

  private fun handleUrl(
    url: HttpUrl,
    site: Site?,
    fullPath: String
  ): Event {
    return when (site) {
      is Chan4 -> handle4chanUrl(url, fullPath)
      else -> {
        Logger.debug(TAG) { "Unknown site: ${site?.descriptor?.siteName}" }
        Event.OpenUrlInWebViewController(url)
      }
    }
  }

  private fun handle4chanUrl(
    url: HttpUrl,
    fullPath: String
  ): Event {
    return when {
      // https://4chan.org/faq#blocked
      fullPath.startsWith("faq") -> {
        Event.OpenUrlInWebViewController(url)
      }
      // https://sys.4chan.org/signin
      fullPath.startsWith("signin") -> {
        Event.OpenEmailVerificationController
      }
      // Issue #673: oekaki replays use a Flash based player that the
      // Android WebView cannot run. Hand the URL to the system browser
      // instead of opening a blank in-app WebView.
      isOekakiReplayUrl(url, fullPath) -> {
        Logger.debug(TAG) { "[4chan] Oekaki replay url, opening externally: ${url}" }
        Event.OpenInExternalBrowser(url)
      }
      else -> {
        Logger.debug(TAG) { "[4chan] Unknown url: ${url}" }
        Event.OpenUrlInWebViewController(url)
      }
    }
  }

  private fun isOekakiReplayUrl(url: HttpUrl, fullPath: String): Boolean {
    if (fullPath.contains("oekaki", ignoreCase = true)) {
      return true
    }
    if (fullPath.contains("replay", ignoreCase = true)) {
      return true
    }
    val host = url.host.lowercase()
    if (host.contains("oekaki")) {
      return true
    }
    return url.encodedPath.endsWith(".swf", ignoreCase = true)
  }

  sealed interface Event {
    data class OpenUrlInWebViewController(val url: HttpUrl) : Event
    data class OpenInExternalBrowser(val url: HttpUrl) : Event
    data object OpenEmailVerificationController : Event
  }

  companion object {
    private const val TAG = "KurobaWebUrlRouter"
  }
}