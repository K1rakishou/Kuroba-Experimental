package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4CaptchaSettings
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.prefs.GsonJsonSetting
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.TimeUnit

class RefreshChan4CaptchaTicketUseCase(
    private val siteManager: SiteManager,
    private val loadChan4CaptchaUseCase: LoadChan4CaptchaUseCase
) {
    private val _lastRefreshedCaptchaStateFlow = MutableStateFlow<CaptchaResultWithTime?>(null)
    val lastRefreshedCaptcha: CaptchaResultWithTime?
        get() = checkCaptchaLifeTime(_lastRefreshedCaptchaStateFlow.value)

    suspend fun await(chanDescriptor: ChanDescriptor) {
        try {
            val chan4CaptchaSettingsSetting = siteManager.bySiteDescriptor(Chan4.SITE_DESCRIPTOR)!!
                .getSettingBySettingId<GsonJsonSetting<Chan4CaptchaSettings>>(SiteSetting.SiteSettingId.Chan4CaptchaSettings)!!

            val chan4CaptchaSettings = chan4CaptchaSettingsSetting.get()

            if (System.currentTimeMillis() - chan4CaptchaSettings.lastRefreshTime < ticketRefreshInterval) {
                val now = System.currentTimeMillis()
                val lastRefreshTime = chan4CaptchaSettings.lastRefreshTime
                val timeDelta = now - lastRefreshTime

                Logger.debug(TAG) {
                    "Skipping ticket refresh. " +
                            "currentTime: ${now}, " +
                            "lastRefreshTime: ${lastRefreshTime}, " +
                            "timeDelta: ${timeDelta}"
                }

                return
            }

            Logger.debug(TAG) { "Refreshing 4chan captcha ticket, chanDescriptor: ${chanDescriptor}" }

            val captchaResult = loadChan4CaptchaUseCase.await(
                chanDescriptor = chanDescriptor,
                ticket = chan4CaptchaSettings.captchaTicket,
                isRefreshing = true
            )
            .onError { error ->
                Logger.error(TAG) { "loadChan4CaptchaUseCase.await() error: ${error.errorMessageOrClassName()}" }
            }
            .valueOrNull()

            if (captchaResult != null) {
                _lastRefreshedCaptchaStateFlow.value = CaptchaResultWithTime(
                    chanDescriptor = chanDescriptor,
                    captchaResult = captchaResult,
                    refreshedAt = System.currentTimeMillis()
                )
            } else {
                _lastRefreshedCaptchaStateFlow.value = null
            }

            Logger.debug(TAG) { "Refreshing 4chan captcha ticket done, chanDescriptor: ${chanDescriptor}" }
        } catch (error: Throwable) {
            Logger.error(TAG) { "Failed to refresh 4chan captcha ticket, error: ${error.errorMessageOrClassName()}" }
        }
    }

    private fun checkCaptchaLifeTime(captchaResultWithTime: CaptchaResultWithTime?): CaptchaResultWithTime? {
        if (captchaResultWithTime == null) {
            return null
        }

        val captchaInfoRaw = captchaResultWithTime.captchaResult.captchaInfoRaw
        if (captchaInfoRaw.ttl == null || captchaInfoRaw.ttl <= 0) {
            return null
        }

        val now = System.currentTimeMillis()
        val refreshedAt = captchaResultWithTime.refreshedAt

        val safeTtl = (captchaInfoRaw.ttlMillis().toFloat() * .8f).toInt()
        if (now - refreshedAt > safeTtl) {
            return null
        }

        return captchaResultWithTime
    }

    data class CaptchaResultWithTime(
        val chanDescriptor: ChanDescriptor,
        val captchaResult: LoadChan4CaptchaUseCase.CaptchaResult,
        val refreshedAt: Long
    )

    companion object {
        private const val TAG = "RefreshChan4CaptchaTicketUseCase"

        // I have no idea how long this shit lives
        private val ticketRefreshInterval = TimeUnit.HOURS.toMillis(2)
    }

}