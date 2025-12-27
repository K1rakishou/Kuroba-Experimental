package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.KurobaSystemNotifications
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class Chan4CaptchaNotifierManager(
  private val appScope: CoroutineScope,
  private val appResources: AppResources,
  private val kurobaSystemNotifications: KurobaSystemNotifications,
  private val siteManager: SiteManager,
  private val chanThreadManager: ChanThreadManager
) {
  private var _waitJob: Job? = null
  private var _waiter = CompletableDeferred<Unit>()
  private var _captchaViewShown = false

  fun onCaptchaViewInitialized() {
    _captchaViewShown = true
    Logger.debug(TAG) { "onCaptchaViewInitialized()" }
  }

  fun onCaptchaViewDestroyed() {
    _captchaViewShown = false
    Logger.debug(TAG) { "onCaptchaViewDestroyed()" }
  }

  fun start(
    waitDescriptor: ChanDescriptor,
    initialCooldownMs: Long,
    onTick: WeakReference<(Long) -> Boolean>
  ) {
    if (waitDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      // Shouldn't be possible
      return
    }

    Logger.debug(TAG) { "start() waitDescriptor: ${waitDescriptor}, initialCooldownMs: ${initialCooldownMs}" }

    _waitJob?.cancel()
    _waiter.cancel()
    _waiter = CompletableDeferred()

    _waitJob = appScope.launch {
      var currentTime = System.currentTimeMillis()
      val endTime = currentTime + (initialCooldownMs.coerceAtLeast(0))

      try {
        while (isActive) {
          if (currentTime >= endTime) {
            break
          }

          delay(1000L)

          currentTime = System.currentTimeMillis()
          val remainingCooldownMs = (endTime - currentTime).coerceAtLeast(0)

          val func = onTick.get()
            ?: continue

          if (func(remainingCooldownMs)) {
            break
          }
        }

        if (isActive && !_captchaViewShown) {
          val chanDescriptorReadable = waitDescriptor.userReadableString()

          val largeIconUrl = when (waitDescriptor) {
            is ChanDescriptor.CatalogDescriptor -> {
              siteManager.bySiteDescriptor(waitDescriptor.siteDescriptor())?.icon()?.url
            }
            is ChanDescriptor.ThreadDescriptor -> {
              chanThreadManager.getChanThread(waitDescriptor)?.getOriginalPostSafe()?.firstImage()?.actualThumbnailUrl
            }
          }

          kurobaSystemNotifications.showNotification(
            KurobaSystemNotifications.NotificationData(
              id = chanDescriptorReadable,
              style = KurobaSystemNotifications.NotificationData.Style.Default(
                title = appResources.string(R.string.captcha_layout_captcha_is_ready_title),
                content = appResources.string(R.string.captcha_layout_captcha_is_ready_description, chanDescriptorReadable)
              ),
              priority = KurobaSystemNotifications.NotificationData.Priority.High,
              largeIcon = largeIconUrl?.let { url -> KurobaSystemNotifications.NotificationData.LargeIcon.RemoteUrl(url) }
            )
          )
        }

        _waiter.complete(Unit)
      } catch (error: Throwable) {
        _waiter.cancel()
        throw error
      }
    }
  }

  suspend fun wait(): Boolean {
    try {
      _waiter.await()
      return true
    } catch (ignored: Throwable) {
      return false
    }
  }

  companion object {
    private const val TAG = "Chan4CaptchaNotifierManager"
  }

}