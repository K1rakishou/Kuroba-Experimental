package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.common.FirewallType
import com.github.k1rakishou.common.awaitSilently
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.HttpUrl

class FirewallBypassManager(
  private val appScope: CoroutineScope,
  private val applicationVisibilityManager: ApplicationVisibilityManager
) {
  @GuardedBy("itself")
  private val firewallSiteInfoMap = mutableMapOf<String, FirewallSiteInfo>()

  private val rendezvousCoroutineExecutor = RendezvousCoroutineExecutor(appScope)

  private val _showFirewallControllerEvents = MutableSharedFlow<ShowFirewallControllerInfo>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_LATEST
  )
  val showFirewallControllerEvents: SharedFlow<ShowFirewallControllerInfo>
    get() = _showFirewallControllerEvents.asSharedFlow()

  fun onFirewallDetected(
    firewallType: FirewallType,
    siteDescriptor: SiteDescriptor,
    urlToOpen: HttpUrl
  ) {
    if (!applicationVisibilityManager.isAppInForeground()) {
      // No point to do anything here since there is most likely no activity currently alive
      return
    }

    val host = urlToOpen.host

    val notifyListeners = synchronized(firewallSiteInfoMap) {
      val firewallSiteInfo = firewallSiteInfoMap.getOrPut(
        key = host,
        defaultValue = { FirewallSiteInfo(lastCheckTime = 0) }
      )

      if (firewallSiteInfo.isCurrentlyShowing) {
        return@synchronized false
      }

      val now = System.currentTimeMillis()
      if (now - firewallSiteInfo.lastCheckTime < FIREWALL_CHECK_TIMEOUT_MS) {
        return@synchronized false
      }

      return@synchronized true
    }

    if (!notifyListeners) {
      return
    }

    Logger.d(TAG, "Sending event to show SiteFirewallBypassController")

    rendezvousCoroutineExecutor.post {
      val completableDeferred = CompletableDeferred(Unit)

      val showFirewallControllerInfo = ShowFirewallControllerInfo(
        firewallType = firewallType,
        siteDescriptor = siteDescriptor,
        urlToOpen = urlToOpen,
        onFinished = completableDeferred
      )

      _showFirewallControllerEvents.tryEmit(showFirewallControllerInfo)

      completableDeferred.awaitSilently()

      synchronized(firewallSiteInfoMap) {
        val firewallSiteInfo = firewallSiteInfoMap.getOrPut(
          key = host,
          defaultValue = { FirewallSiteInfo(lastCheckTime = 0) }
        )

        firewallSiteInfo.lastCheckTime = System.currentTimeMillis()
      }

    }
  }

  class FirewallSiteInfo(
    var lastCheckTime: Long = 0L,
    var isCurrentlyShowing: Boolean = false
  )

  class ShowFirewallControllerInfo(
    val firewallType: FirewallType,
    val siteDescriptor: SiteDescriptor,
    val urlToOpen: HttpUrl,
    val onFinished: CompletableDeferred<Unit>
  )

  companion object {
    private const val TAG = "FirewallBypassManager"
    private const val FIREWALL_CHECK_TIMEOUT_MS = 10_000L
  }

}