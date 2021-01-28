package com.github.k1rakishou.chan.features.settings.screens

import android.content.Context
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.widget.TextView
import androidx.core.text.util.LinkifyCompat
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.PersistableChanState
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager
import com.github.k1rakishou.chan.core.manager.watcher.BookmarkForegroundWatcher
import com.github.k1rakishou.chan.features.settings.SettingsGroup
import com.github.k1rakishou.chan.features.settings.ThreadWatcherScreen
import com.github.k1rakishou.chan.features.settings.setting.BooleanSettingV2
import com.github.k1rakishou.chan.features.settings.setting.ListSettingV2
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.PhoneWithBackgroundLimitationsHelper
import com.github.k1rakishou.core_themes.ThemeEngine
import java.util.concurrent.TimeUnit


class ThreadWatcherSettingsScreen(
  context: Context,
  private val applicationVisibilityManager: ApplicationVisibilityManager,
  private val themeEngine: ThemeEngine,
  private val dialogFactory: DialogFactory
) : BaseSettingsScreen(
  context,
  ThreadWatcherScreen,
  R.string.settings_screen_watch
) {

  override fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
    return listOf(buildMainSettingsGroup())
  }

  private fun buildMainSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = ThreadWatcherScreen.MainGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_group_watch),
          groupIdentifier = identifier
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = ThreadWatcherScreen.MainGroup.EnableThreadWatcher,
          topDescriptionIdFunc = { R.string.setting_watch_enable_watcher },
          bottomDescriptionIdFunc = { R.string.setting_watch_enable_watcher_description },
          setting = ChanSettings.watchEnabled
        )

        group += ListSettingV2.createBuilder<Int>(
          context = context,
          identifier = ThreadWatcherScreen.MainGroup.ThreadWatcherForegroundUpdateInterval,
          topDescriptionIdFunc = { R.string.setting_watch_foreground_timeout },
          bottomDescriptionStringFunc = { itemName ->
            getString(R.string.setting_watch_foreground_timeout_description).toString() + "\n\n" + itemName
          },
          items = FOREGROUND_INTERVALS,
          itemNameMapper = { timeout ->
            return@createBuilder getString(
              R.string.seconds,
              TimeUnit.MILLISECONDS.toSeconds(timeout.toLong()).toInt()
            )
          },
          setting = ChanSettings.watchForegroundInterval,
          dependsOnSetting = ChanSettings.watchEnabled
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = ThreadWatcherScreen.MainGroup.AdaptiveForegroundWatcherInterval,
          topDescriptionIdFunc = { R.string.setting_watch_foreground_adaptive_timer },
          bottomDescriptionStringFunc = {
            val seconds = TimeUnit.MILLISECONDS.toSeconds(
              BookmarkForegroundWatcher.ADDITIONAL_INTERVAL_INCREMENT_MS
            )
            return@createBuilder getString(
              R.string.setting_watch_foreground_adaptive_timer_description,
              seconds
            )
          },
          setting = ChanSettings.watchForegroundAdaptiveInterval,
          dependsOnSetting =  ChanSettings.watchEnabled
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = ThreadWatcherScreen.MainGroup.EnableBackgroundThreadWatcher,
          topDescriptionIdFunc = { R.string.setting_watch_enable_background },
          bottomDescriptionIdFunc = { R.string.setting_watch_enable_background_description },
          checkChangedCallback = { checked ->
            showShittyPhonesBackgroundLimitationsExplanationDialog(
              checked
            )
          },
          setting = ChanSettings.watchBackground,
          dependsOnSetting = ChanSettings.watchEnabled
        )

        group += ListSettingV2.createBuilder<Int>(
          context = context,
          identifier = ThreadWatcherScreen.MainGroup.ThreadWatcherBackgroundUpdateInterval,
          topDescriptionIdFunc = { R.string.setting_watch_background_timeout },
          bottomDescriptionStringFunc = { itemName ->
            getString(R.string.setting_watch_background_timeout_description).toString() + "\n\n" + itemName
          },
          items = kotlin.run {
            if (AppModuleAndroidUtils.isDevBuild()) {
              BACKGROUND_INTERVALS
            } else {
              BACKGROUND_INTERVALS.drop(1)
            }
          },
          itemNameMapper = { timeout ->
            val timeoutString = getString(
              R.string.minutes,
              TimeUnit.MILLISECONDS.toMinutes(timeout.toLong()).toInt()
            )

            val testOptionThreshold = TimeUnit.MINUTES.toMillis(1).toInt()
            if (timeout <= testOptionThreshold) {
              return@createBuilder getString(
                R.string.setting_background_watcher_test_option,
                timeoutString
              )
            }

            val optimalTimeoutThreshold = TimeUnit.MINUTES.toMillis(30).toInt()
            if (timeout >= optimalTimeoutThreshold) {
              return@createBuilder getString(
                R.string.setting_background_watcher_optimal_option,
                timeoutString
              )
            }

            val nonOptimalTimeoutsThreshold = TimeUnit.MINUTES.toMillis(10).toInt()
            if (timeout >= nonOptimalTimeoutsThreshold) {
              return@createBuilder getString(
                R.string.setting_background_watcher_non_optimal_option,
                timeoutString
              )
            }

            return@createBuilder getString(
              R.string.setting_background_watcher_very_bad_option,
              timeoutString
            )
          },
          setting = ChanSettings.watchBackgroundInterval,
          dependsOnSetting = ChanSettings.watchBackground
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = ThreadWatcherScreen.MainGroup.ReplyNotifications,
          topDescriptionIdFunc = { R.string.setting_reply_notifications },
          bottomDescriptionIdFunc = { R.string.setting_reply_notifications_description },
          setting = ChanSettings.replyNotifications,
          dependsOnSetting = ChanSettings.watchEnabled
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = ThreadWatcherScreen.MainGroup.UseSoundForReplyNotifications,
          topDescriptionIdFunc = { R.string.setting_reply_notifications_use_sound },
          setting = ChanSettings.useSoundForReplyNotifications,
          dependsOnSetting = ChanSettings.replyNotifications
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = ThreadWatcherScreen.MainGroup.WatchLastPageNotify,
          topDescriptionIdFunc = { R.string.setting_thread_page_limit_notify },
          bottomDescriptionIdFunc = { R.string.setting_thread_page_limit_notify_description },
          setting = ChanSettings.watchLastPageNotify,
          dependsOnSetting = ChanSettings.watchBackground
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = ThreadWatcherScreen.MainGroup.UseSoundForLastPageNotifications,
          topDescriptionIdFunc = { R.string.setting_thread_page_limit_notify_use_sound },
          setting = ChanSettings.useSoundForLastPageNotifications,
          dependsOnSetting = ChanSettings.watchLastPageNotify
        )

        return group
      }
    )
  }

  private fun showShittyPhonesBackgroundLimitationsExplanationDialog(checked: Boolean) {
    if (!PhoneWithBackgroundLimitationsHelper.isPhoneWithPossibleBackgroundLimitations()) {
      return
    }

    if (!checked) {
      return
    }

    if (PersistableChanState.shittyPhonesBackgroundLimitationsExplanationDialogShown.get()) {
      return
    }

    if (!applicationVisibilityManager.isAppInForeground()) {
      return
    }

    val descriptionText = SpannableString(
      context.getString(
        R.string.setting_watch_background_limitations_dialog_description,
        PhoneWithBackgroundLimitationsHelper.getFormattedLink()
      )
    )

    LinkifyCompat.addLinks(descriptionText, Linkify.WEB_URLS)

    dialogFactory.createSimpleConfirmationDialog(
      context = context,
      titleTextId = R.string.setting_watch_background_limitations_dialog_title,
      descriptionText = descriptionText,
      dialogModifier = { dialog ->
        (dialog.findViewById<TextView>(android.R.id.message))?.apply {
          setLinkTextColor(themeEngine.chanTheme.postLinkColor)
          movementMethod = LinkMovementMethod.getInstance()
        }
      }
    )

    PersistableChanState.shittyPhonesBackgroundLimitationsExplanationDialogShown.set(true)
  }

  companion object {
    private val BACKGROUND_INTERVALS = listOf(
      TimeUnit.MINUTES.toMillis(1).toInt(),
      TimeUnit.MINUTES.toMillis(5).toInt(),
      TimeUnit.MINUTES.toMillis(10).toInt(),
      TimeUnit.MINUTES.toMillis(15).toInt(),
      TimeUnit.MINUTES.toMillis(30).toInt(),
      TimeUnit.MINUTES.toMillis(45).toInt(),
      TimeUnit.MINUTES.toMillis(60).toInt(),
      TimeUnit.MINUTES.toMillis(90).toInt(),
      TimeUnit.MINUTES.toMillis(120).toInt()
    )

    private val FOREGROUND_INTERVALS = listOf(
      TimeUnit.SECONDS.toMillis(30).toInt(),
      TimeUnit.MINUTES.toMillis(1).toInt(),
      TimeUnit.MINUTES.toMillis(2).toInt(),
      TimeUnit.MINUTES.toMillis(5).toInt(),
      TimeUnit.MINUTES.toMillis(10).toInt(),
    )
  }
}