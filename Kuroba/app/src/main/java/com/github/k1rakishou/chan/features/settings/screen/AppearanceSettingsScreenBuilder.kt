package com.github.k1rakishou.chan.features.settings.screen

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.reordering.SimpleListItemsReorderingController
import com.github.k1rakishou.chan.features.settings.SettingsScreen
import com.github.k1rakishou.chan.features.settings.setting.SettingUiElement
import com.github.k1rakishou.chan.features.themes.ThemeSettingsController
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.utils.TimeUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.v2.KurobaSettings
import com.github.k1rakishou.v2.parameters.LayoutMode
import com.github.k1rakishou.v2.parameters.NetworkContentAutoLoadMode
import com.github.k1rakishou.v2.parameters.PostAlignmentMode
import com.github.k1rakishou.v2.parameters.PostThumbnailScaling
import com.github.k1rakishou.v2.parameters.ReorderableBottomNavViewButtons
import com.github.k1rakishou.v2.settings.AbstractKurobaSetting

class AppearanceSettingsScreenBuilder(
  private val kurobaSettings: KurobaSettings,
  private val appResources: AppResources,
  private val themeEngine: ThemeEngine
) : SettingsScreenBuilder {

  override suspend fun build(context: Context, settingActions: SettingActions, settingsScreen: SettingsScreen) {
    with(settingsScreen) {
      buildAppearanceSettingsGroup(context, settingActions)
      buildLayoutSettingsGroup(context, settingActions)
      buildPostSettingsGroup()
      buildPostLinksSettingsGroup()
      buildImageSettingsGroup()
    }
  }

  private suspend fun SettingsScreen.buildAppearanceSettingsGroup(context: Context, settingActions: SettingActions) {
    addGroup(
      key = "appearance",
      title = appResources.string(R.string.settings_group_appearance)
    ) {
      addSetting(
        SettingUiElement.Link(
          composeKey = "ThemeCustomization",
          title = { appResources.string(R.string.setting_theme) },
          currentValue = { themeEngine.chanTheme.name },
          callback = {
            if (TimeUtils.isHalloweenToday()) {
              settingActions.showToast(appResources.string(R.string.not_allowed_during_halloween))
              return@Link
            }

            settingActions.pushController(ThemeSettingsController(context))
          }
        )
      )
    }
  }

  private suspend fun SettingsScreen.buildLayoutSettingsGroup(context: Context, settingActions: SettingActions) {
    addGroup(
      key = "layout",
      title = appResources.string(R.string.settings_group_layout)
    ) {
      addSetting(
        SettingUiElement.Items(
          title = { appResources.string(R.string.setting_layout_mode) },
          items = LayoutMode.entries.toList(),
          selectionType = SettingUiElement.SelectionType.Multiple("layout_mode"),
          itemNameMapper = { layoutMode ->
            when (layoutMode) {
              LayoutMode.Auto -> appResources.string(R.string.setting_layout_mode_auto)
              LayoutMode.Slide -> appResources.string(R.string.setting_layout_mode_slide)
              LayoutMode.Phone -> appResources.string(R.string.setting_layout_mode_phone)
              LayoutMode.Split -> appResources.string(R.string.setting_layout_mode_split)
            }
          },
          setting = kurobaSettings.application.layoutMode
            as AbstractKurobaSetting<LayoutMode, LayoutMode>,
          requiresPostListRefresh = true
        )
      )

      addSetting(
        SettingUiElement.Items(
          title = { appResources.string(R.string.setting_board_grid_span_count) },
          items = ALL_COLUMNS,
          selectionType = SettingUiElement.SelectionType.Multiple("catalog_column_count"),
          itemNameMapper = { columnsCount ->
            when (columnsCount) {
              AUTO_COLUMN -> appResources.string(R.string.setting_span_count_default)
              in ALL_COLUMNS_EXCLUDING_AUTO -> {
                appResources.string(R.string.setting_span_count_item, columnsCount)
              }
              else -> throw IllegalArgumentException("Bad columns count: $columnsCount")
            }
          },
          setting = kurobaSettings.application.catalogSpanCount,
          requiresPostListRefresh = true
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "ReorderableBottomNavViewButtonsSetting",
          title = { appResources.string(R.string.setting_reorder_bottom_nav_view_buttons) },
          callback = {
            val reorderableBottomNavViewButtons = kurobaSettings.internal.reorderableBottomNavViewButtons.read()
            val items = reorderableBottomNavViewButtons.bottomNavViewButtons()
              .map { button -> SimpleListItemsReorderingController.SimpleListReorderableItem(button.id, button.title) }

            val controller = SimpleListItemsReorderingController(
              context = context,
              items = items,
              onApplyClicked = { itemsReordered ->
                val reorderedButtons = ReorderableBottomNavViewButtons(itemsReordered.map { it.id })
                kurobaSettings.internal.reorderableBottomNavViewButtons.writeBlocking(reorderedButtons)
                settingActions.showToast(appResources.string(R.string.restart_the_app))
              }
            )

            settingActions.presentController(controller)
          }
        )
      )
    }
  }

  private suspend fun SettingsScreen.buildPostSettingsGroup() {
    addGroup(
      key = "post",
      title = appResources.string(R.string.settings_group_post)
    ) {
      addSetting(
        SettingUiElement.Items(
          title = { appResources.string(R.string.setting_font_size) },
          items = kurobaSettings.application.supportedFontSizes()
            .map { fontSize -> fontSize.toString() }
            .toList(),
          selectionType = SettingUiElement.SelectionType.Multiple("font_size"),
          itemNameMapper = { fontSize -> fontSize },
          setting = kurobaSettings.application.fontSize,
          requiresPostListRefresh = true
        )
      )

      addSetting(
        SettingUiElement.Range(
          title = { appResources.string(R.string.setting_post_cell_thumbnail_size) },
          currentValue = { "${kurobaSettings.application.postCellThumbnailSizePercents.read()}%" },
          setting = kurobaSettings.application.postCellThumbnailSizePercents,
          requiresPostListRefresh = true
        )
      )

      addSetting(
        SettingUiElement.Items(
          title = { appResources.string(R.string.setting_post_thumbnail_scaling) },
          items = PostThumbnailScaling.entries.toList(),
          selectionType = SettingUiElement.SelectionType.Multiple("post_thumbnail_scaling"),
          itemNameMapper = { postThumbnailScaling ->
            when (postThumbnailScaling) {
              PostThumbnailScaling.FitCenter -> {
                appResources.string(R.string.setting_post_thumbnail_scaling_fit_center)
              }
              PostThumbnailScaling.CenterCrop -> {
                appResources.string(R.string.setting_post_thumbnail_scaling_center_crop)
              }
            }
          },
          setting = kurobaSettings.application.postThumbnailScaling
            as AbstractKurobaSetting<PostThumbnailScaling, PostThumbnailScaling>,
          requiresPostListRefresh = true
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_post_draw_post_thumbnail_background) },
          setting = kurobaSettings.application.drawPostThumbnailBackground,
          requiresPostListRefresh = true
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_post_full_date) },
          setting = kurobaSettings.application.postFullDate,
          requiresPostListRefresh = true
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_post_full_date_local_locale) },
          description = { appResources.string(R.string.setting_post_full_date_local_locale_description) },
          setting = kurobaSettings.application.postFullDateUseLocalLocale,
          requiresPostListRefresh = true
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_post_file_info) },
          setting = kurobaSettings.application.postFileInfo,
          requiresPostListRefresh = true
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_post_shift_post_comment) },
          description = { appResources.string(R.string.setting_post_shift_post_comment_description) },
          setting = kurobaSettings.application.shiftPostComment,
          requiresPostListRefresh = true
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_force_post_shift_post_comment) },
          description = { appResources.string(R.string.setting_force_post_shift_post_comment_description) },
          setting = kurobaSettings.application.forceShiftPostComment,
          dependencies = listOf(kurobaSettings.application.shiftPostComment),
          requiresPostListRefresh = true
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_post_multiple_images_compact_mode) },
          description = { appResources.string(R.string.setting_post_multiple_images_compact_mode_description) },
          setting = kurobaSettings.application.postMultipleImagesCompactMode,
          requiresPostListRefresh = true
        )
      )

      addSetting(
        SettingUiElement.Items(
          title = { appResources.string(R.string.setting_catalog_post_alignment_mode) },
          items = PostAlignmentMode.entries.toList(),
          selectionType = SettingUiElement.SelectionType.Multiple("post_alignment_mode_catalog"),
          itemNameMapper = { layoutMode ->
            when (layoutMode) {
              PostAlignmentMode.AlignLeft -> appResources.string(R.string.setting_post_alignment_mode_left)
              PostAlignmentMode.AlignRight -> appResources.string(R.string.setting_post_alignment_mode_right)
            }
          },
          setting = kurobaSettings.application.catalogPostAlignmentMode
            as AbstractKurobaSetting<PostAlignmentMode, PostAlignmentMode>,
          requiresPostListRefresh = true
        )
      )

      addSetting(
        SettingUiElement.Items(
          title = { appResources.string(R.string.setting_thread_post_alignment_mode) },
          items = PostAlignmentMode.entries.toList(),
          selectionType = SettingUiElement.SelectionType.Multiple("post_alignment_mode_thread"),
          itemNameMapper = { layoutMode ->
            when (layoutMode) {
              PostAlignmentMode.AlignLeft -> appResources.string(R.string.setting_post_alignment_mode_left)
              PostAlignmentMode.AlignRight -> appResources.string(R.string.setting_post_alignment_mode_right)
            }
          },
          setting = kurobaSettings.application.threadPostAlignmentMode
            as AbstractKurobaSetting<PostAlignmentMode, PostAlignmentMode>,
          requiresPostListRefresh = true
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_text_only) },
          description = { appResources.string(R.string.setting_text_only_description) },
          setting = kurobaSettings.application.textOnly,
          requiresPostListRefresh = true
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.settings_reveal_text_spoilers) },
          description = { appResources.string(R.string.settings_reveal_text_spoilers_description) },
          setting = kurobaSettings.application.revealTextSpoilers,
          requiresPostListRefresh = true
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_anonymize) },
          description = { appResources.string(R.string.setting_anonymize_description) },
          setting = kurobaSettings.application.anonymize,
          requiresPostListRefresh = true
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_show_anonymous_name) },
          description = { appResources.string(R.string.setting_show_anonymous_name_description) },
          setting = kurobaSettings.application.showAnonymousName,
          requiresPostListRefresh = true
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_anonymize_ids) },
          setting = kurobaSettings.application.anonymizeIds,
          requiresPostListRefresh = true
        )
      )
    }
  }

  private suspend fun SettingsScreen.buildPostLinksSettingsGroup() {
    addGroup(
      key = "post_links",
      title = appResources.string(R.string.setting_group_post_links)
    ) {
      addSetting(
        SettingUiElement.Items(
          title = { appResources.string(R.string.setting_youtube_title_and_durations) },
          description = { appResources.string(R.string.setting_youtube_title_and_durations_description) },
          items = NetworkContentAutoLoadMode.entries.toList(),
          selectionType = SettingUiElement.SelectionType.Multiple("youtube_title_parsing"),
          itemNameMapper = { loadMode -> networkContentAutoLoadNameMapper(loadMode) },
          setting = kurobaSettings.application.parseYoutubeTitlesAndDuration
            as AbstractKurobaSetting<NetworkContentAutoLoadMode, NetworkContentAutoLoadMode>,
          requiresPostListRefresh = true
        )
      )

      addSetting(
        SettingUiElement.Items(
          title = { appResources.string(R.string.setting_soundcloud_title_and_durations) },
          description = { appResources.string(R.string.setting_soundcloud_title_and_durations_description) },
          items = NetworkContentAutoLoadMode.entries.toList(),
          selectionType = SettingUiElement.SelectionType.Multiple("soundcloud_title_parsing"),
          itemNameMapper = { loadMode -> networkContentAutoLoadNameMapper(loadMode) },
          setting = kurobaSettings.application.parseSoundCloudTitlesAndDuration
            as AbstractKurobaSetting<NetworkContentAutoLoadMode, NetworkContentAutoLoadMode>,
          requiresPostListRefresh = true
        )
      )

      addSetting(
        SettingUiElement.Items(
          title = { appResources.string(R.string.setting_streamable_title_and_durations) },
          description = { appResources.string(R.string.setting_streamable_title_and_durations_description) },
          items = NetworkContentAutoLoadMode.entries.toList(),
          selectionType = SettingUiElement.SelectionType.Multiple("streamable_title_parsing"),
          itemNameMapper = { loadMode -> networkContentAutoLoadNameMapper(loadMode) },
          setting = kurobaSettings.application.parseStreamableTitlesAndDuration
            as AbstractKurobaSetting<NetworkContentAutoLoadMode, NetworkContentAutoLoadMode>,
          requiresPostListRefresh = true
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_show_link_along_with_title_and_duration_title) },
          description = { appResources.string(R.string.setting_show_link_along_with_title_and_duration_description) },
          setting = kurobaSettings.application.showLinkAlongWithTitleAndDuration,
          requiresPostListRefresh = true
        )
      )
    }
  }

  private suspend fun SettingsScreen.buildImageSettingsGroup() {
    addGroup(
      key = "images",
      title = appResources.string(R.string.settings_group_images)
    ) {
      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_hide_images) },
          description = { appResources.string(R.string.setting_hide_images_description) },
          setting = kurobaSettings.application.hideImages,
          requiresPostListRefresh = true
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.settings_remove_image_spoilers) },
          description = { appResources.string(R.string.settings_remove_image_spoilers_description) },
          setting = kurobaSettings.application.postThumbnailRemoveImageSpoilers
        )
      )
    }
  }

  private fun networkContentAutoLoadNameMapper(item: NetworkContentAutoLoadMode): String {
    return when (item) {
      NetworkContentAutoLoadMode.All -> {
        appResources.string(R.string.setting_image_auto_load_all)
      }
      NetworkContentAutoLoadMode.Unmetered -> {
        appResources.string(R.string.setting_image_auto_load_unmetered)
      }
      NetworkContentAutoLoadMode.None -> {
        appResources.string(R.string.setting_image_auto_load_none)
      }
    }
  }

  companion object {
    const val AUTO_COLUMN = 0
    val ALL_COLUMNS = listOf(AUTO_COLUMN, 1, 2, 3, 4, 5)
    val ALL_COLUMNS_EXCLUDING_AUTO = setOf(1, 2, 3, 4, 5)

    @JvmStatic
    fun clampColumnsCount(columns: Int): Int {
      return columns.coerceIn(1, ALL_COLUMNS.last())
    }
  }

}