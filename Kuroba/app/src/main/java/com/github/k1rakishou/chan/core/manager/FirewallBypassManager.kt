package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.concurrency.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.features.webview.WebViewTaskResult
import com.github.k1rakishou.chan.features.webview.task.AbstractWebViewTask
import com.github.k1rakishou.chan.features.webview.task.CloudFlareTask
import com.github.k1rakishou.chan.features.webview.task.DvachAntispamTask
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.common.FirewallType
import com.github.k1rakishou.common.domainOrHost
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.rethrowCancellationException
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.atomic.AtomicBoolean

class FirewallBypassManager(
  private val appScope: CoroutineScope,
  private val siteManager: SiteManager,
  private val webViewTaskManager: WebViewTaskManager
) {
  @GuardedBy("itself")
  private val firewallSiteInfoMap = mutableMapOf<String, FirewallSiteInfo>()
  @GuardedBy("itself")
  private val hostLastTimeCheck = mutableMapOf<String, Long>()

  private val rendezvousCoroutineExecutor = RendezvousCoroutineExecutor(appScope)

  suspend fun removeHostTimeCheckByChanDescriptor(chanDescriptor: ChanDescriptor) {
    siteManager.awaitUntilInitialized()

    val domainOrHost = siteManager.bySiteDescriptorAndActive(chanDescriptor.siteDescriptor())
      ?.urlHandler
      ?.desktopUrl(chanDescriptor, null, null)
      ?.toHttpUrlOrNull()
      ?.domainOrHost()

    Logger.debug(TAG) { "removeHostTimeCheckByChanDescriptor(${chanDescriptor}) -> '${domainOrHost}'" }

    if (domainOrHost.isNullOrBlank()) {
      return
    }

    synchronized(hostLastTimeCheck) { hostLastTimeCheck.remove(domainOrHost) }
  }

  fun onFirewallDetected(
    firewallType: FirewallType,
    urlToOpen: HttpUrl,
    onFinished: (success: Boolean) -> Unit
  ) {
    val domainOrHost = urlToOpen.domainOrHost()

    val showShowFirewallBypassScreen = synchronized(firewallSiteInfoMap) {
      val isCurrentlyShowing = firewallSiteInfoMap.get(domainOrHost)?.currentlyShowing == true

      if (isCurrentlyShowing) {
        firewallSiteInfoMap.get(domainOrHost)?.addWaiter(onFinished)
        return@synchronized ShowShowFirewallBypassScreen.WaitForExistingOne
      }

      val firewallSiteInfo = FirewallSiteInfo(onFinished)
      firewallSiteInfoMap[domainOrHost] = firewallSiteInfo

      val now = System.currentTimeMillis()
      val lastTimeChecked = synchronized(hostLastTimeCheck) {
        hostLastTimeCheck[domainOrHost] ?: 0L
      }

      if (now - lastTimeChecked < FIREWALL_CHECK_TIMEOUT_MS) {
        Logger.verbose(TAG) {
          "onFirewallDetected(${firewallType}, ${urlToOpen}) skipping because screen was shown not long ago " +
                  "(timeDelta: ${now - lastTimeChecked})"
        }

        firewallSiteInfoMap.remove(domainOrHost)
        return@synchronized ShowShowFirewallBypassScreen.DoNotShow
      }

      return@synchronized ShowShowFirewallBypassScreen.Show
    }

    when (showShowFirewallBypassScreen) {
      ShowShowFirewallBypassScreen.WaitForExistingOne -> {
        return
      }
      ShowShowFirewallBypassScreen.DoNotShow -> {
        onFinished.invoke(false)
        return
      }
      ShowShowFirewallBypassScreen.Show -> {
        // no-op
      }
    }

    Logger.debug(TAG) {
      "onFirewallDetected(${firewallType}, '${urlToOpen}') Sending event to show SiteFirewallBypassController"
    }

    rendezvousCoroutineExecutor.post {
      var success = false

      try {
        synchronized(firewallSiteInfoMap) {
          firewallSiteInfoMap[domainOrHost]?.onStarted()
        }

        Logger.debug(TAG) {
          "onFirewallDetected(${firewallType}, '${urlToOpen}') Waiting for result from SiteFirewallBypassController..."
        }

        val resultWaiter = CompletableDeferred<WebViewTaskResult>()

        val webViewTask = when (firewallType) {
          FirewallType.Cloudflare -> {
            CloudFlareTask(
              headerTitleText = getString(R.string.firewall_check_header_title, firewallType.name),
              loadable = AbstractWebViewTask.Loadable.Url(urlToOpen),
              invokerWaiter = resultWaiter
            )
          }
          FirewallType.DvachAntiSpam -> {
            DvachAntispamTask(
              headerTitleText = getString(R.string.firewall_check_header_title, firewallType.name),
              loadable = AbstractWebViewTask.Loadable.Url(urlToOpen),
              invokerWaiter = resultWaiter
            )
          }
          FirewallType.YandexSmartCaptcha -> {
            error("Handled in ImageSearchController")
          }
        }

        success = webViewTaskManager.performWebViewTask(webViewTask).isSuccess

        Logger.debug(TAG) {
          "onFirewallDetected(${firewallType}, '${urlToOpen}') Waiting for result from " +
                  "SiteFirewallBypassController... done, success: ${success}"
        }
      } catch (error: Throwable) {
        Logger.error(TAG) {
          "onFirewallDetected(${firewallType}, '${urlToOpen}') Waiting for result from " +
                  "SiteFirewallBypassController... done, error: ${error.errorMessageOrClassName()}"
        }

        error.rethrowCancellationException()
      } finally {
        synchronized(firewallSiteInfoMap) {
          firewallSiteInfoMap.remove(domainOrHost)
            ?.onFinished(success)

          synchronized(hostLastTimeCheck) {
            hostLastTimeCheck[domainOrHost] = System.currentTimeMillis()
          }
        }
      }
    }
  }

  enum class ShowShowFirewallBypassScreen {
    Show,
    WaitForExistingOne,
    DoNotShow
  }

  class FirewallSiteInfo(
    waiter: (Boolean) -> Unit
  ) {
    @GuardedBy("this")
    private val waiters = mutableListOf<(Boolean) -> Unit>()

    private val _currentlyShowing = AtomicBoolean(false)
    val currentlyShowing: Boolean
      get() = _currentlyShowing.get()

    init {
      waiters += waiter
    }

    @Synchronized
    fun addWaiter(waiter: (Boolean) -> Unit) {
      waiters += waiter
    }

    @Synchronized
    fun onStarted() {
      _currentlyShowing.set(true)
    }

    @Synchronized
    fun onFinished(success: Boolean) {
      waiters.forEach { waiter -> waiter.invoke(success) }
      waiters.clear()
      _currentlyShowing.set(false)
    }

  }

  companion object {
    private const val TAG = "FirewallBypassManager"
    private const val FIREWALL_CHECK_TIMEOUT_MS = 10_000L
  }

}