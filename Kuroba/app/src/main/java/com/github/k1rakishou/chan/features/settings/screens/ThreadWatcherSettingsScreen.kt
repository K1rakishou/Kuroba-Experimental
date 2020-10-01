package com.github.k1rakishou.chan.features.settings.screens

import android.content.Context
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager
import com.github.k1rakishou.chan.core.settings.ChanSettings
import com.github.k1rakishou.chan.core.settings.state.PersistableChanState
import com.github.k1rakishou.chan.features.settings.SettingsGroup
import com.github.k1rakishou.chan.features.settings.ThreadWatcherScreen
import com.github.k1rakishou.chan.features.settings.setting.BooleanSettingV2
import com.github.k1rakishou.chan.features.settings.setting.ListSettingV2
import com.github.k1rakishou.chan.utils.AndroidUtils.getString
import com.github.k1rakishou.chan.utils.PhoneWithBackgroundLimitationsHelper
import java.util.concurrent.TimeUnit


class ThreadWatcherSettingsScreen(
  context: Context,
  private val applicationVisibilityManager: ApplicationVisibilityManager
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
          identifier = ThreadWatcherScreen.MainGroup.EnableBackgroundThreadWatcher,
          topDescriptionIdFunc = { R.string.setting_watch_enable_background },
          bottomDescriptionIdFunc = { R.string.setting_watch_enable_background_description },
          checkChangedCallback = { checked -> showShittyPhonesBackgroundLimitationsExplanationDialog(checked) },
          setting = ChanSettings.watchBackground,
          dependsOnSetting = ChanSettings.watchEnabled
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

        group += ListSettingV2.createBuilder<Int>(
          context = context,
          identifier = ThreadWatcherScreen.MainGroup.ThreadWatcherTimeout,
          topDescriptionIdFunc = { R.string.setting_watch_background_timeout },
          bottomDescriptionStringFunc = { itemName ->
            getString(R.string.setting_watch_background_timeout_description).toString() + "\n\n" + itemName
          },
          items = INTERVALS,
          itemNameMapper = { timeout ->
            val timeoutString = getString(
              R.string.minutes,
              TimeUnit.MILLISECONDS.toMinutes(timeout.toLong()).toInt()
            )

            if (timeout == FIRST_TIMEOUT_OPTION) {
              return@createBuilder getString(
                R.string.setting_background_watcher_first_option,
                timeoutString
              )
            } else if (timeout == LAST_TIMEOUT_OPTION) {
              return@createBuilder getString(
                R.string.setting_background_watcher_last_option,
                timeoutString
              )
            }

            return@createBuilder timeoutString
          },
          setting = ChanSettings.watchBackgroundInterval,
          dependsOnSetting = ChanSettings.watchBackground
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

    Linkify.addLinks(descriptionText, Linkify.WEB_URLS)

    val dialog = AlertDialog.Builder(context)
      .setTitle(R.string.setting_watch_background_limitations_dialog_title)
      .setPositiveButton(R.string.ok) { _, _ ->
        PersistableChanState.shittyPhonesBackgroundLimitationsExplanationDialogShown.set(true)
      }
      .setMessage(descriptionText)
      .create()

    dialog.show()
    (dialog.findViewById<TextView>(android.R.id.message))?.movementMethod = LinkMovementMethod.getInstance()
  }

  companion object {
    private val INTERVALS = listOf(
      TimeUnit.MINUTES.toMillis(15).toInt(),
      TimeUnit.MINUTES.toMillis(30).toInt(),
      TimeUnit.MINUTES.toMillis(45).toInt(),
      TimeUnit.MINUTES.toMillis(60).toInt(),
      TimeUnit.MINUTES.toMillis(90).toInt(),
      TimeUnit.MINUTES.toMillis(120).toInt()
    )

    private val FIRST_TIMEOUT_OPTION = INTERVALS.first()
    private val LAST_TIMEOUT_OPTION = INTERVALS.last()
  }
}