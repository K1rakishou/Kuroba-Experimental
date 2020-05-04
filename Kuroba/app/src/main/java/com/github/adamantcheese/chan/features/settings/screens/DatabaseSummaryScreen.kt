package com.github.adamantcheese.chan.features.settings.screens

import android.content.Context
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.features.settings.SettingV2
import com.github.adamantcheese.chan.features.settings.SettingsGroup
import com.github.adamantcheese.chan.features.settings.SettingsIdentifier
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.model.repository.InlinedFileInfoRepository
import com.github.adamantcheese.model.repository.MediaServiceLinkExtraContentRepository
import com.github.adamantcheese.model.repository.SeenPostRepository
import kotlinx.coroutines.runBlocking
import java.util.*

class DatabaseSummaryScreen(
  context: Context,
  private val inlinedFileInfoRepository: InlinedFileInfoRepository,
  private val mediaServiceLinkExtraContentRepository: MediaServiceLinkExtraContentRepository,
  private val seenPostRepository: SeenPostRepository
) : BaseSettingsScreen(
  context,
  SettingsIdentifier.Screen.DatabaseSummaryScreen,
  R.string.settings_database_summary
) {

  override fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
    return listOf(
      buildMainSettingsGroup()
    )
  }

  private fun buildMainSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = SettingsIdentifier.Group.DatabaseSummaryGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupIdentifier = identifier
        )

        group += SettingV2.createBuilder(
          context = context,
          identifier = SettingsIdentifier.DatabaseSummary.ClearInlinedFilesTable,
          topDescriptionIdFunc = {
            R.string.settings_clear_inlined_files_table
          },
          bottomDescriptionStringFunc = {
            val count = runBlocking { inlinedFileInfoRepository.count().unwrap() }
            String.format(Locale.ENGLISH, "This table stores file size info for inlined files.\n\n" +
              "Inlined files info table rows count: $count")
          },
          callback = {
            val deleted = runBlocking { inlinedFileInfoRepository.deleteAll().unwrap() }
            AndroidUtils.showToast(context, "Done, deleted $deleted inlined file info rows")
          }
        )

        group += SettingV2.createBuilder(
          context = context,
          identifier = SettingsIdentifier.DatabaseSummary.ClearLinkExtraInfoTable,
          topDescriptionIdFunc = {
            R.string.settings_clear_link_info_table
          },
          bottomDescriptionStringFunc = {
            val count = runBlocking { mediaServiceLinkExtraContentRepository.count().unwrap() }
            String.format(Locale.ENGLISH, "This table stores title and durations for youtube (and not only) like.\n\n" +
              "Link extra info table rows count: $count")
          },
          callback = {
            val deleted = runBlocking { mediaServiceLinkExtraContentRepository.deleteAll().unwrap() }
            AndroidUtils.showToast(context, "Done, deleted $deleted extra link info rows")
          }
        )

        group += SettingV2.createBuilder(
          context = context,
          identifier = SettingsIdentifier.DatabaseSummary.ClearSeenPostsTable,
          topDescriptionIdFunc = {
            R.string.settings_clear_seen_posts_table
          },
          bottomDescriptionStringFunc = {
            val count = runBlocking { seenPostRepository.count().unwrap() }
            String.format(Locale.ENGLISH, "This table stores ids of already seen posts.\n\n" +
              "Seen posts table rows count: $count")
          },
          callback = {
            val deleted = runBlocking { seenPostRepository.deleteAll().unwrap() }
            AndroidUtils.showToast(context, "Done, deleted $deleted seen posts rows")
          }
        )

        return group
      }
    )
  }

}