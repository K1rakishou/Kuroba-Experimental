package com.github.k1rakishou.chan.features.settings.screen

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.settings.SettingsScreen
import com.github.k1rakishou.chan.features.settings.setting.SettingUiElement
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.repository.MediaServiceLinkExtraContentRepository
import com.github.k1rakishou.model.repository.SeenPostRepository
import java.util.Locale

class DatabaseSummarySettingsScreenBuilder(
  private val appResources: AppResources,
  private val appConstants: AppConstants,
  private val mediaServiceLinkExtraContentRepository: MediaServiceLinkExtraContentRepository,
  private val seenPostRepository: SeenPostRepository,
  private val chanPostRepository: ChanPostRepository
) : SettingsScreenBuilder {

  override suspend fun build(
    context: Context,
    settingActions: SettingActions,
    settingsScreen: SettingsScreen
  ) {
    with(settingsScreen) {
      buildMainSettingsGroup(settingActions)
    }
  }

  private suspend fun SettingsScreen.buildMainSettingsGroup(
    settingActions: SettingActions,
  ) {
    addGroup(
      key = "main",
      title = appResources.string(R.string.settings_database_summary)
    ) {
      addSetting(
        SettingUiElement.Link(
          composeKey = "ClearLinkExtraInfoTable",
          title = { appResources.string(R.string.settings_clear_link_info_table) },
          description = {
            val count = mediaServiceLinkExtraContentRepository.count().unwrap()
            return@Link String.format(
              Locale.ENGLISH,
              "This table stores title and durations for youtube (and not only) like.\n\n" +
              "Link extra info table rows count: $count"
            )
          },
          callback = {
            val deleted = mediaServiceLinkExtraContentRepository.deleteAll().unwrap()
            settingActions.showToast("Done, deleted $deleted extra link info rows")
          }
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "ClearSeenPostsTable",
          title = { appResources.string(R.string.settings_clear_seen_posts_table) },
          description = {
            val count = seenPostRepository.count().unwrap()
            return@Link String.format(
              Locale.ENGLISH,
              "This table stores ids of already seen posts.\n\n" +
              "Seen posts table rows count: $count"
            )
          },
          callback = {
            val deleted = seenPostRepository.deleteAll().unwrap()
            settingActions.showToast("Done, deleted $deleted seen posts rows")
          }
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "ThreadsCleanup",
          title = { appResources.string(R.string.settings_trigger_thread_cleanup) },
          description = {
            val count = chanPostRepository.totalThreadsCount().unwrap()
            val maxCount = appConstants.maxAmountOfThreadsInDatabase
            return@Link String.format(
              Locale.ENGLISH,
              "Total threads count: ${count} out of ${maxCount} maximum allowed threads"
            )
          },
          callback = {
            val deleted = chanPostRepository.deleteOldThreadsIfNeeded(forced = true).unwrap()
            settingActions.showToast("Done, deleted ${deleted.deletedTotal} thread rows, " +
              "skipped ${deleted.skippedTotal} thread rows")
          }
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "PostsCleanup",
          title = { appResources.string(R.string.settings_trigger_post_cleanup) },
          description = {
            val count = chanPostRepository.totalPostsCount().unwrap()
            val maxCount = appConstants.maxAmountOfPostsInDatabase
            return@Link String.format(
              Locale.ENGLISH,
              "Total posts count: ${count} out of ${maxCount} maximum allowed posts"
            )
          },
          callback = {
            val deleted = chanPostRepository.deleteOldPostsIfNeeded(forced = true).unwrap()
            settingActions.showToast("Done, deleted ${deleted.deletedTotal} post rows, " +
              "skipped ${deleted.skippedTotal} post rows")
          }
        )
      )
    }
  }
}