package com.github.k1rakishou.chan.features.settings.screens

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.settings.DatabaseSummaryScreen
import com.github.k1rakishou.chan.features.settings.SettingsGroup
import com.github.k1rakishou.chan.features.settings.setting.LinkSettingV2
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.repository.MediaServiceLinkExtraContentRepository
import com.github.k1rakishou.model.repository.SeenPostRepository
import java.util.*

class DatabaseSettingsSummaryScreen(
  context: Context,
  private val appConstants: AppConstants,
  private val mediaServiceLinkExtraContentRepository: MediaServiceLinkExtraContentRepository,
  private val seenPostRepository: SeenPostRepository,
  private val chanPostRepository: ChanPostRepository
) : BaseSettingsScreen(
  context,
  DatabaseSummaryScreen,
  R.string.settings_database_summary
) {

  override suspend fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
    return listOf(
      buildMainSettingsGroup()
    )
  }

  private fun buildMainSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = DatabaseSummaryScreen.MainGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = {
        val group = SettingsGroup(
          groupIdentifier = identifier
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = DatabaseSummaryScreen.MainGroup.ClearLinkExtraInfoTable,
          topDescriptionIdFunc = { R.string.settings_clear_link_info_table },
          bottomDescriptionStringFunc = {
            val count = mediaServiceLinkExtraContentRepository.count().unwrap()
            String.format(Locale.ENGLISH, "This table stores title and durations for youtube (and not only) like.\n\n" +
              "Link extra info table rows count: $count")
          },
          callback = {
            val deleted = mediaServiceLinkExtraContentRepository.deleteAll().unwrap()
            showToast(context, "Done, deleted $deleted extra link info rows")
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = DatabaseSummaryScreen.MainGroup.ClearSeenPostsTable,
          topDescriptionIdFunc = { R.string.settings_clear_seen_posts_table },
          bottomDescriptionStringFunc = {
            val count = seenPostRepository.count().unwrap()
            String.format(Locale.ENGLISH, "This table stores ids of already seen posts.\n\n" +
              "Seen posts table rows count: $count")
          },
          callback = {
            val deleted = seenPostRepository.deleteAll().unwrap()
            showToast(context, "Done, deleted $deleted seen posts rows")
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = DatabaseSummaryScreen.MainGroup.ThreadsTable,
          topDescriptionIdFunc = { R.string.settings_trigger_thread_cleanup },
          bottomDescriptionStringFunc = {
            val count = chanPostRepository.totalThreadsCount().unwrap()
            val maxCount = appConstants.maxAmountOfThreadsInDatabase

            return@createBuilder String.format(
              Locale.ENGLISH,
              "Total threads count: ${count} out of ${maxCount} maximum allowed threads"
            )
          },
          callback = {
            val deleted = chanPostRepository.deleteOldThreadsIfNeeded(forced = true).unwrap()

            showToast(context, "Done, deleted ${deleted.deletedTotal} thread rows, " +
              "skipped ${deleted.skippedTotal} thread rows")
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = DatabaseSummaryScreen.MainGroup.PostsTable,
          topDescriptionIdFunc = { R.string.settings_trigger_post_cleanup },
          bottomDescriptionStringFunc = {
            val count = chanPostRepository.totalPostsCount().unwrap()
            val maxCount = appConstants.maxAmountOfPostsInDatabase

            return@createBuilder String.format(
              Locale.ENGLISH,
              "Total posts count: ${count} out of ${maxCount} maximum allowed posts"
            )
          },
          callback = {
            val deleted = chanPostRepository.deleteOldPostsIfNeeded(forced = true).unwrap()

            showToast(context, "Done, deleted ${deleted.deletedTotal} post rows, " +
              "skipped ${deleted.skippedTotal} post rows")
          }
        )

        group
      }
    )
  }

}