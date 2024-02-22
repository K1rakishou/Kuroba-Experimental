package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.common.FirewallType
import com.github.k1rakishou.common.awaitSilently
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.rethrowCancellationException
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
    urlToOpen: HttpUrl,
    onFinished: (success: Boolean) -> Unit
  ) {
    if (!applicationVisibilityManager.isAppInForeground()) {
      // No point to do anything here since there is most likely no activity currently alive
      Logger.verbose(TAG) {
        "onFirewallDetected(${firewallType}, ${urlToOpen}) skipping because the app is in background"
      }

      onFinished.invoke(false)
      return
    }

    val host = urlToOpen.host

    val showShowFirewallBypassScreen = synchronized(firewallSiteInfoMap) {
      val isCurrentlyShowing = firewallSiteInfoMap.containsKey(host)

      val firewallSiteInfo = firewallSiteInfoMap.getOrPut(
        key = host,
        defaultValue = { FirewallSiteInfo(onFinished) }
      )

      if (isCurrentlyShowing) {
        firewallSiteInfo.addWaiter(onFinished)
        return@synchronized ShowShowFirewallBypassScreen.WaitForExistingOne
      }

      val now = System.currentTimeMillis()
      if (now - firewallSiteInfo.lastCheckTime < FIREWALL_CHECK_TIMEOUT_MS) {
        Logger.verbose(TAG) {
          "onFirewallDetected(${firewallType}, ${urlToOpen}) skipping because screen was shown not long ago " +
                  "(timeDelta: ${now - firewallSiteInfo.lastCheckTime})"
        }

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
      try {
        val completableDeferred = CompletableDeferred<Unit>()

        val showFirewallControllerInfo = ShowFirewallControllerInfo(
          firewallType = firewallType,
          siteDescriptor = siteDescriptor,
          urlToOpen = urlToOpen,
          onFinished = completableDeferred
        )

        _showFirewallControllerEvents.emit(showFirewallControllerInfo)

        Logger.debug(TAG) {
          "onFirewallDetected(${firewallType}, '${urlToOpen}') Waiting for result from SiteFirewallBypassController..."
        }

        val success = completableDeferred.awaitSilently()

        Logger.debug(TAG) {
          "onFirewallDetected(${firewallType}, '${urlToOpen}') Waiting for result from " +
                  "SiteFirewallBypassController... done, success: ${success}"
        }

        synchronized(firewallSiteInfoMap) {
          firewallSiteInfoMap.get(host)?.onFinished(
            success = success,
            lastCheckTime = System.currentTimeMillis()
          )
        }
      } catch (error: Throwable) {
        Logger.error(TAG) {
          "onFirewallDetected(${firewallType}, '${urlToOpen}') Waiting for result from " +
                  "SiteFirewallBypassController... done, error: ${error.errorMessageOrClassName()}"
        }

        onFinished.invoke(false)

        error.rethrowCancellationException()
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
    private var _lastCheckTime: Long = 0L
    @GuardedBy("this")
    private val waiters = mutableListOf<(Boolean) -> Unit>()

    init {
      waiters += waiter
    }

    val lastCheckTime: Long
      @Synchronized
      get() = _lastCheckTime

    @Synchronized
    fun addWaiter(waiter: (Boolean) -> Unit) {
      waiters += waiter
    }

    @Synchronized
    fun onFinished(success: Boolean, lastCheckTime: Long) {
      _lastCheckTime = lastCheckTime

      waiters.forEach { waiter -> waiter.invoke(success) }
      waiters.clear()
    }
  }

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