package com.github.k1rakishou.chan.features.settings.screens

import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.ReorderableBottomNavViewButtons
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.reordering.SimpleListItemsReorderingController
import com.github.k1rakishou.chan.features.settings.AppearanceScreen
import com.github.k1rakishou.chan.features.settings.SettingsGroup
import com.github.k1rakishou.chan.features.settings.setting.BooleanSettingV2
import com.github.k1rakishou.chan.features.settings.setting.LinkSettingV2
import com.github.k1rakishou.chan.features.settings.setting.ListSettingV2
import com.github.k1rakishou.chan.features.settings.setting.RangeSettingV2
import com.github.k1rakishou.chan.features.themes.ThemeSettingsController
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.chan.utils.TimeUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.persist_state.PersistableChanState

class AppearanceSettingsScreen(
  context: Context,
  private val navigationController: NavigationController,
  private val themeEngine: ThemeEngine
) : BaseSettingsScreen(
  context,
  AppearanceScreen,
  R.string.settings_screen_appearance
) {

  override suspend fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
    return listOf(
      buildAppearanceSettingsGroup(),
      buildLayoutSettingsGroup(),
      buildPostSettingsGroup(),
      buildPostLinksSettingsGroup(),
      buildImageSettingsGroup()
    )
  }

  private fun buildImageSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = AppearanceScreen.ImagesGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_group_images),
          groupIdentifier = identifier
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.ImagesGroup.HideImages,
          topDescriptionIdFunc = { R.string.setting_hide_images },
          bottomDescriptionIdFunc = { R.string.setting_hide_images_description },
          setting = ChanSettings.hideImages,
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.ImagesGroup.RemoveImageSpoilers,
          topDescriptionIdFunc = { R.string.settings_remove_image_spoilers },
          bottomDescriptionIdFunc = { R.string.settings_remove_image_spoilers_description },
          setting = ChanSettings.postThumbnailRemoveImageSpoilers
        )

        group
      }
    )

  }

  private fun buildPostSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = AppearanceScreen.PostGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_group_post),
          groupIdentifier = identifier
        )

        group += ListSettingV2.createBuilder<String>(
          context = context,
          identifier = AppearanceScreen.PostGroup.FontSize,
          topDescriptionIdFunc = { R.string.setting_font_size },
          bottomDescriptionStringFunc = { itemName -> itemName },
          items = SUPPORTED_FONT_SIZES.map { fontSize -> fontSize.toString() }.toList(),
          groupId = "font_size",
          itemNameMapper = { fontSize ->
            when (fontSize.toIntOrNull()) {
              in SUPPORTED_FONT_SIZES -> fontSize
              else -> throw IllegalArgumentException("Bad font size: $fontSize")
            }
          },
          setting = ChanSettings.fontSize,
          requiresUiRefresh = true
        )

        group += RangeSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.PostGroup.PostCellThumbnailSizePercent,
          topDescriptionIdFunc = { R.string.setting_post_cell_thumbnail_size },
          currentValueStringFunc = { "${ChanSettings.postCellThumbnailSizePercents.get()}%" },
          requiresUiRefresh = true,
          setting = ChanSettings.postCellThumbnailSizePercents
        )

        group += ListSettingV2.createBuilder<ChanSettings.PostThumbnailScaling>(
          context = context,
          identifier = AppearanceScreen.PostGroup.PostThumbnailScaling,
          topDescriptionIdFunc = { R.string.setting_post_thumbnail_scaling },
          bottomDescriptionStringFunc = { itemName -> itemName },
          items = ChanSettings.PostThumbnailScaling.values().toList(),
          groupId = "post_thumbnail_scaling",
          itemNameMapper = { postThumbnailScaling ->
            when (postThumbnailScaling) {
              ChanSettings.PostThumbnailScaling.FitCenter -> getString(R.string.setting_post_thumbnail_scaling_fit_center)
              ChanSettings.PostThumbnailScaling.CenterCrop -> getString(R.string.setting_post_thumbnail_scaling_center_crop)
            }
          },
          requiresUiRefresh = true,
          setting = ChanSettings.postThumbnailScaling
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.PostGroup.DrawPostThumbnailBackground,
          topDescriptionIdFunc = { R.string.setting_post_draw_post_thumbnail_background },
          setting = ChanSettings.drawPostThumbnailBackground,
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.PostGroup.PostFullDate,
          topDescriptionIdFunc = { R.string.setting_post_full_date },
          setting = ChanSettings.postFullDate,
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.PostGroup.PostFullDateUseLocalLocale,
          topDescriptionIdFunc = { R.string.setting_post_full_date_local_locale },
          bottomDescriptionIdFunc = { R.string.setting_post_full_date_local_locale_description },
          setting = ChanSettings.postFullDateUseLocalLocale,
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.PostGroup.PostFileInfo,
          topDescriptionIdFunc = { R.string.setting_post_file_info },
          setting = ChanSettings.postFileInfo,
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.PostGroup.ShiftPostComment,
          topDescriptionIdFunc = { R.string.setting_post_shift_post_comment },
          bottomDescriptionIdFunc = { R.string.setting_post_shift_post_comment_description },
          setting = ChanSettings.shiftPostComment,
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.PostGroup.ForceShiftPostComment,
          topDescriptionIdFunc = { R.string.setting_force_post_shift_post_comment },
          bottomDescriptionIdFunc = { R.string.setting_force_post_shift_post_comment_description },
          setting = ChanSettings.forceShiftPostComment,
          dependsOnSetting = ChanSettings.shiftPostComment,
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.PostGroup.PostMultipleImagesCompactMode,
          topDescriptionIdFunc = { R.string.setting_post_multiple_images_compact_mode },
          bottomDescriptionIdFunc = { R.string.setting_post_multiple_images_compact_mode_description },
          setting = ChanSettings.postMultipleImagesCompactMode,
          requiresUiRefresh = true
        )

        group += ListSettingV2.createBuilder<ChanSettings.PostAlignmentMode>(
          context = context,
          identifier = AppearanceScreen.LayoutGroup.CatalogPostAlignmentMode,
          topDescriptionIdFunc = { R.string.setting_catalog_post_alignment_mode },
          bottomDescriptionStringFunc = { itemName -> itemName },
          items = ChanSettings.PostAlignmentMode.values().toList(),
          groupId = "post_alignment_mode_catalog",
          itemNameMapper = { layoutMode ->
            when (layoutMode) {
              ChanSettings.PostAlignmentMode.AlignLeft -> context.getString(R.string.setting_post_alignment_mode_left)
              ChanSettings.PostAlignmentMode.AlignRight -> context.getString(R.string.setting_post_alignment_mode_right)
            }
          },
          requiresUiRefresh = true,
          setting = ChanSettings.catalogPostAlignmentMode
        )

        group += ListSettingV2.createBuilder<ChanSettings.PostAlignmentMode>(
          context = context,
          identifier = AppearanceScreen.LayoutGroup.ThreadPostAlignmentMode,
          topDescriptionIdFunc = { R.string.setting_thread_post_alignment_mode },
          bottomDescriptionStringFunc = { itemName -> itemName },
          items = ChanSettings.PostAlignmentMode.values().toList(),
          groupId = "post_alignment_mode_thread",
          itemNameMapper = { layoutMode ->
            when (layoutMode) {
              ChanSettings.PostAlignmentMode.AlignLeft -> context.getString(R.string.setting_post_alignment_mode_left)
              ChanSettings.PostAlignmentMode.AlignRight -> context.getString(R.string.setting_post_alignment_mode_right)
            }
          },
          requiresUiRefresh = true,
          setting = ChanSettings.threadPostAlignmentMode
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.PostGroup.TextOnly,
          topDescriptionIdFunc = { R.string.setting_text_only },
          bottomDescriptionIdFunc = { R.string.setting_text_only_description },
          setting = ChanSettings.textOnly,
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.PostGroup.RevealTextSpoilers,
          topDescriptionIdFunc = { R.string.settings_reveal_text_spoilers },
          bottomDescriptionIdFunc = { R.string.settings_reveal_text_spoilers_description },
          setting = ChanSettings.revealTextSpoilers,
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.PostGroup.Anonymize,
          topDescriptionIdFunc = { R.string.setting_anonymize },
          bottomDescriptionIdFunc = { R.string.setting_anonymize_description },
          setting = ChanSettings.anonymize,
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.PostGroup.ShowAnonymousName,
          topDescriptionIdFunc = { R.string.setting_show_anonymous_name },
          bottomDescriptionIdFunc = { R.string.setting_show_anonymous_name_description },
          setting = ChanSettings.showAnonymousName,
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.PostGroup.AnonymizeIds,
          topDescriptionIdFunc = { R.string.setting_anonymize_ids },
          setting = ChanSettings.anonymizeIds,
          requiresUiRefresh = true
        )

        group
      }
    )
  }

  private fun buildPostLinksSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = AppearanceScreen.PostLinksGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.setting_group_post_links),
          groupIdentifier = identifier
        )

        group += ListSettingV2.createBuilder<ChanSettings.NetworkContentAutoLoadMode>(
          context = context,
          identifier = AppearanceScreen.PostLinksGroup.ParseYoutubeTitlesAndDuration,
          topDescriptionIdFunc = { R.string.setting_youtube_title_and_durations },
          bottomDescriptionStringFunc = {
            context.getString(
              R.string.setting_youtube_title_and_durations_description,
              networkContentAutoLoadNameMapper(ChanSettings.parseYoutubeTitlesAndDuration.get())
            )
          },
          setting = ChanSettings.parseYoutubeTitlesAndDuration,
          items = ChanSettings.NetworkContentAutoLoadMode.values().toList(),
          groupId = "youtube_title_parsing",
          itemNameMapper = { item -> networkContentAutoLoadNameMapper(item) },
          requiresUiRefresh = true
        )

        group += ListSettingV2.createBuilder<ChanSettings.NetworkContentAutoLoadMode>(
          context = context,
          identifier = AppearanceScreen.PostLinksGroup.ParseSoundCloudTitlesAndDuration,
          topDescriptionIdFunc = { R.string.setting_soundcloud_title_and_durations },
          bottomDescriptionStringFunc = {
            context.getString(
              R.string.setting_soundcloud_title_and_durations_description,
              networkContentAutoLoadNameMapper(ChanSettings.parseSoundCloudTitlesAndDuration.get())
            )
          },
          setting = ChanSettings.parseSoundCloudTitlesAndDuration,
          items = ChanSettings.NetworkContentAutoLoadMode.values().toList(),
          groupId = "soundcloud_title_parsing",
          itemNameMapper = { item -> networkContentAutoLoadNameMapper(item) },
          requiresUiRefresh = true
        )

        group += ListSettingV2.createBuilder<ChanSettings.NetworkContentAutoLoadMode>(
          context = context,
          identifier = AppearanceScreen.PostLinksGroup.ParseStreamableTitlesAndDuration,
          topDescriptionIdFunc = { R.string.setting_streamable_title_and_durations },
          bottomDescriptionStringFunc = {
            context.getString(
              R.string.setting_streamable_title_and_durations_description,
              networkContentAutoLoadNameMapper(ChanSettings.parseStreamableTitlesAndDuration.get())
            )
          },
          setting = ChanSettings.parseStreamableTitlesAndDuration,
          items = ChanSettings.NetworkContentAutoLoadMode.values().toList(),
          groupId = "streamable_title_parsing",
          itemNameMapper = { item -> networkContentAutoLoadNameMapper(item) },
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.PostLinksGroup.ShowLinkAlongWithTitleAndDuration,
          topDescriptionIdFunc = { R.string.setting_show_link_along_with_title_and_duration_title },
          bottomDescriptionIdFunc = { R.string.setting_show_link_along_with_title_and_duration_description },
          setting = ChanSettings.showLinkAlongWithTitleAndDuration,
          requiresUiRefresh = true
        )

        group
      }
    )
  }

  private fun buildLayoutSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = AppearanceScreen.LayoutGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_group_layout),
          groupIdentifier = identifier
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.LayoutGroup.BottomNavigationMode,
          topDescriptionIdFunc = { R.string.setting_bottom_navigation_mode },
          bottomDescriptionIdFunc = { R.string.setting_bottom_navigation_mode_description },
          setting = ChanSettings.bottomNavigationViewEnabled,
          requiresRestart = true,
          isEnabledFunc = { ChanSettings.isSplitLayoutMode().not() }
        )

        group += ListSettingV2.createBuilder<ChanSettings.LayoutMode>(
          context = context,
          identifier = AppearanceScreen.LayoutGroup.LayoutMode,
          topDescriptionIdFunc = { R.string.setting_layout_mode },
          bottomDescriptionStringFunc = { itemName -> itemName },
          items = ChanSettings.LayoutMode.values().toList(),
          groupId = "layout_mode",
          itemNameMapper = { layoutMode ->
            when (layoutMode) {
              ChanSettings.LayoutMode.AUTO -> context.getString(R.string.setting_layout_mode_auto)
              ChanSettings.LayoutMode.SLIDE -> context.getString(R.string.setting_layout_mode_slide)
              ChanSettings.LayoutMode.PHONE -> context.getString(R.string.setting_layout_mode_phone)
              ChanSettings.LayoutMode.SPLIT -> context.getString(R.string.setting_layout_mode_split)
            }
          },
          requiresRestart = true,
          setting = ChanSettings.layoutMode
        )

        group += ListSettingV2.createBuilder<Int>(
          context = context,
          identifier = AppearanceScreen.LayoutGroup.CatalogColumnsCount,
          topDescriptionIdFunc = { R.string.setting_board_grid_span_count },
          bottomDescriptionStringFunc = { itemName -> itemName },
          items = ALL_COLUMNS,
          groupId = "catalog_column_count",
          itemNameMapper = { columnsCount ->
            when (columnsCount) {
              AUTO_COLUMN -> context.getString(R.string.setting_span_count_default)
              in ALL_COLUMNS_EXCLUDING_AUTO -> {
                context.getString(R.string.setting_span_count_item, columnsCount)
              }
              else -> throw IllegalArgumentException("Bad columns count: $columnsCount")
            }
          },
          requiresUiRefresh = true,
          setting = ChanSettings.catalogSpanCount
        )

        group += ListSettingV2.createBuilder<Int>(
          context = context,
          identifier = AppearanceScreen.LayoutGroup.AlbumColumnsCount,
          topDescriptionIdFunc = { R.string.setting_album_span_count },
          bottomDescriptionStringFunc = { itemName -> itemName },
          items = ALL_COLUMNS,
          groupId = "album_column_count",
          itemNameMapper = { columnsCount ->
            when (columnsCount) {
              AUTO_COLUMN -> context.getString(R.string.setting_span_count_default)
              in ALL_COLUMNS_EXCLUDING_AUTO -> {
                context.getString(R.string.setting_span_count_item, columnsCount)
              }
              else -> throw IllegalArgumentException("Bad columns count: $columnsCount")
            }
          },
          requiresUiRefresh = true,
          setting = ChanSettings.albumSpanCount
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.LayoutGroup.NeverHideToolbar,
          topDescriptionIdFunc = { R.string.setting_never_hide_toolbar },
          setting = ChanSettings.neverHideToolbar,
          requiresRestart = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.LayoutGroup.EnableReplyFAB,
          topDescriptionIdFunc = { R.string.setting_enable_reply_fab },
          bottomDescriptionIdFunc = { R.string.setting_enable_reply_fab_description },
          setting = ChanSettings.enableReplyFab,
          requiresRestart = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.LayoutGroup.BottomJsCaptcha,
          topDescriptionIdFunc = { R.string.setting_bottom_js_captcha },
          bottomDescriptionIdFunc = { R.string.setting_bottom_js_captcha_description },
          setting = ChanSettings.captchaOnBottom,
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.LayoutGroup.NeverShowPages,
          topDescriptionIdFunc = { R.string.setting_never_show_pages },
          bottomDescriptionIdFunc = { R.string.setting_never_show_pages_bottom },
          setting = ChanSettings.neverShowPages
        )

        group += ListSettingV2.createBuilder<ChanSettings.FastScrollerType>(
          context = context,
          identifier = AppearanceScreen.LayoutGroup.EnableDraggableScrollbars,
          topDescriptionIdFunc = { R.string.setting_enable_draggable_scrollbars },
          bottomDescriptionIdFunc = { R.string.setting_enable_draggable_scrollbars_bottom },
          items = ChanSettings.FastScrollerType.values().toList(),
          groupId = "fast_scroller_type",
          itemNameMapper = { fastScrollerType ->
            when (fastScrollerType) {
              ChanSettings.FastScrollerType.Disabled -> {
                context.getString(R.string.setting_enable_draggable_scrollbars_disabled)
              }
              ChanSettings.FastScrollerType.ScrollByDraggingThumb -> {
                context.getString(R.string.setting_enable_draggable_scrollbars_scroll_by_dragging_thumb)
              }
              ChanSettings.FastScrollerType.ScrollByClickingAnyPointOfTrack -> {
                context.getString(R.string.setting_enable_draggable_scrollbars_scroll_by_clicking_track)
              }
            }
          },
          setting = ChanSettings.draggableScrollbars,
          requiresUiRefresh = true
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.LayoutGroup.ReorderableBottomNavViewButtonsSetting,
          topDescriptionIdFunc = { R.string.setting_reorder_bottom_nav_view_buttons },
          callback = {
            val reorderableBottomNavViewButtons = PersistableChanState.reorderableBottomNavViewButtons.get()

            val items = reorderableBottomNavViewButtons.bottomNavViewButtons()
              .map { button -> SimpleListItemsReorderingController.SimpleReorderableItem(button.id, button.title) }

            val controller = SimpleListItemsReorderingController(
              context = context,
              items = items,
              onApplyClicked = { itemsReordered ->
                val reorderedButtons = ReorderableBottomNavViewButtons(itemsReordered.map { it.id })
                PersistableChanState.reorderableBottomNavViewButtons.set(reorderedButtons)

                showToast(context, R.string.restart_the_app)
              }
            )

            navigationController.presentController(controller)
          }
        )

        group
      }
    )
  }

  private suspend fun buildAppearanceSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = AppearanceScreen.MainGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_group_appearance),
          groupIdentifier = identifier
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.MainGroup.ThemeCustomization,
          topDescriptionIdFunc = { R.string.setting_theme },
          bottomDescriptionStringFunc = { themeEngine.chanTheme.name },
          callback = {
            if (TimeUtils.isHalloweenToday()) {
              showToast(context, R.string.not_allowed_during_halloween)
              return@createBuilder
            }

            navigationController.pushController(ThemeSettingsController(context))
          }
        )

        group
      }
    )
  }

  private fun networkContentAutoLoadNameMapper(item: ChanSettings.NetworkContentAutoLoadMode): String {
    return when (item) {
      ChanSettings.NetworkContentAutoLoadMode.ALL -> {
        context.getString(R.string.setting_image_auto_load_all)
      }
      ChanSettings.NetworkContentAutoLoadMode.UNMETERED -> {
        context.getString(R.string.setting_image_auto_load_unmetered)
      }
      ChanSettings.NetworkContentAutoLoadMode.NONE -> {
        context.getString(R.string.setting_image_auto_load_none)
      }
    }
  }

  companion object {
    private val SUPPORTED_FONT_SIZES = (10..19)
    private const val AUTO_COLUMN = 0
    private val ALL_COLUMNS = listOf(AUTO_COLUMN, 1, 2, 3, 4, 5)
    private val ALL_COLUMNS_EXCLUDING_AUTO = setOf(1, 2, 3, 4, 5)

    @JvmStatic
    fun clampColumnsCount(columns: Int): Int {
      return columns.coerceIn(1, ALL_COLUMNS.last())
    }

  }
}