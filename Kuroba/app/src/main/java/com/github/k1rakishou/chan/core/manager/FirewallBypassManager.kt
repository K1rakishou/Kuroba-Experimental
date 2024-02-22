package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.common.FirewallType
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
import java.util.concurrent.atomic.AtomicBoolean

class FirewallBypassManager(
  private val appScope: CoroutineScope,
  private val applicationVisibilityManager: ApplicationVisibilityManager
) {
  @GuardedBy("itself")
  private val firewallSiteInfoMap = mutableMapOf<String, FirewallSiteInfo>()
  @GuardedBy("itself")
  private val hostLastTimeCheck = mutableMapOf<String, Long>()

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
      val isCurrentlyShowing = firewallSiteInfoMap.get(host)?.currentlyShowing == true

      if (isCurrentlyShowing) {
        firewallSiteInfoMap.get(host)?.addWaiter(onFinished)
        return@synchronized ShowShowFirewallBypassScreen.WaitForExistingOne
      }

      val firewallSiteInfo = FirewallSiteInfo(onFinished)
      firewallSiteInfoMap[host] = firewallSiteInfo

      val now = System.currentTimeMillis()
      val lastTimeChecked = synchronized(hostLastTimeCheck) {
        hostLastTimeCheck[host] ?: 0L
      }

      if (now - lastTimeChecked < FIREWALL_CHECK_TIMEOUT_MS) {
        Logger.verbose(TAG) {
          "onFirewallDetected(${firewallType}, ${urlToOpen}) skipping because screen was shown not long ago " +
                  "(timeDelta: ${now - lastTimeChecked})"
        }

        firewallSiteInfoMap.remove(host)
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
          firewallSiteInfoMap[host]?.onStarted()
        }

        val completableDeferred = CompletableDeferred<Boolean>()

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

        success = try {
          completableDeferred.await()
        } catch (error: Throwable) {
          false
        }

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
          firewallSiteInfoMap.remove(host)?.onFinished(
            success = success
          )

          synchronized(hostLastTimeCheck) {
            hostLastTimeCheck[host] = System.currentTimeMillis()
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

  class ShowFirewallControllerInfo(
    val firewallType: FirewallType,
    val siteDescriptor: SiteDescriptor,
    val urlToOpen: HttpUrl,
    val onFinished: CompletableDeferred<Boolean>
  )

  companion object {
    private const val TAG = "FirewallBypassManager"
    private const val FIREWALL_CHECK_TIMEOUT_MS = 10_000L
  }

}