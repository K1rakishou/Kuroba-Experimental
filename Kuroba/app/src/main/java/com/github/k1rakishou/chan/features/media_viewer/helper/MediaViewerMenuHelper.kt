package com.github.k1rakishou.chan.features.media_viewer.helper

import ReorderableMediaViewerActions
import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerAdapter
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerGesturesSettingsController
import com.github.k1rakishou.chan.features.reordering.SimpleListItemsReorderingController
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.view.floating_menu.CheckableFloatingListMenuItem
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.persist_state.PersistableChanState

class MediaViewerMenuHelper(
  private val globalWindowInsetsManager: GlobalWindowInsetsManager,
  private val presentControllerFunc: (Controller) -> Unit,
  private val showToastFunc: (Int) -> Unit
) {

  fun onMediaViewerOptionsClick(
    context: Context,
    mediaViewerAdapter: MediaViewerAdapter,
    handleClickedOption: (Int) -> Unit,
  ) {
    val mediaLongClickOptions = buildMediaViewerOptions()
    if (mediaLongClickOptions.isEmpty()) {
      return
    }

    val floatingListMenuController = FloatingListMenuController(
      context,
      globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      mediaLongClickOptions,
      itemClickListener = { clickedItem ->
        handleMenuItemClick(context, mediaViewerAdapter, clickedItem, handleClickedOption)
      }
    )

    presentControllerFunc(floatingListMenuController)
  }

  private fun buildMediaViewerOptions(): List<FloatingListMenuItem> {
    val options = mutableListOf<FloatingListMenuItem>()

    options += CheckableFloatingListMenuItem(
      key = ACTION_DRAW_BEHIND_NOTCH,
      name = AppModuleAndroidUtils.getString(R.string.action_draw_behind_notch),
      isCurrentlySelected = ChanSettings.mediaViewerDrawBehindNotch.get()
    )

    options += CheckableFloatingListMenuItem(
      key = ACTION_ALLOW_IMAGE_TRANSPARENCY,
      name = AppModuleAndroidUtils.getString(R.string.action_allow_image_transparency),
      isCurrentlySelected = ChanSettings.transparencyOn.get()
    )

    options += CheckableFloatingListMenuItem(
      key = ACTION_AUTO_REVEAL_SPOILERS,
      name = AppModuleAndroidUtils.getString(R.string.settings_reveal_image_spoilers),
      isCurrentlySelected = ChanSettings.mediaViewerRevealImageSpoilers.get()
    )

    options += CheckableFloatingListMenuItem(
      key = ACTION_VIDEO_AUTO_LOOP,
      name = AppModuleAndroidUtils.getString(R.string.setting_video_auto_loop),
      isCurrentlySelected = ChanSettings.videoAutoLoop.get()
    )

    options += CheckableFloatingListMenuItem(
      key = ACTION_VIDEO_START_MUTED,
      name = AppModuleAndroidUtils.getString(R.string.setting_video_default_muted),
      isCurrentlySelected = ChanSettings.videoDefaultMuted.get()
    )

    options += CheckableFloatingListMenuItem(
      key = ACTION_VIDEO_START_MUTED_WITH_HEADSET,
      name = AppModuleAndroidUtils.getString(R.string.setting_headset_default_muted),
      isCurrentlySelected = ChanSettings.headsetDefaultMuted.get()
    )

    options += CheckableFloatingListMenuItem(
      key = ACTION_VIDEO_ALWAYS_RESET_TO_START,
      name = AppModuleAndroidUtils.getString(R.string.setting_video_always_reset_to_start),
      isCurrentlySelected = ChanSettings.videoAlwaysResetToStart.get()
    )

    options += CheckableFloatingListMenuItem(
      key = ACTION_AUTO_SWIPE_AFTER_DOWNLOAD,
      name = AppModuleAndroidUtils.getString(R.string.setting_auto_swipe_after_download),
      isCurrentlySelected = ChanSettings.mediaViewerAutoSwipeAfterDownload.get()
    )

    options += CheckableFloatingListMenuItem(
      key = ACTION_PAUSE_PLAYERS_WHEN_IN_BG,
      name = AppModuleAndroidUtils.getString(R.string.setting_pause_players_when_in_bg),
      isCurrentlySelected = ChanSettings.mediaViewerPausePlayersWhenInBackground.get()
    )

    options += CheckableFloatingListMenuItem(
      key = ACTION_ENABLE_SOUND_POSTS,
      name = AppModuleAndroidUtils.getString(R.string.setting_enable_sound_posts),
      isCurrentlySelected = ChanSettings.mediaViewerSoundPostsEnabled.get()
    )

    options += CheckableFloatingListMenuItem(
      key = ACTION_USE_MPV,
      name = AppModuleAndroidUtils.getString(R.string.settings_plugins_use_mpv),
      isCurrentlySelected = ChanSettings.useMpvVideoPlayer.get()
    )

    options += FloatingListMenuItem(
      key = ACTION_MEDIA_VIEWER_GESTURE_SETTINGS,
      name = AppModuleAndroidUtils.getString(R.string.setting_media_viewer_gesture_settings),
    )

    options += FloatingListMenuItem(
      key = ACTION_MAX_OFFSCREEN_PAGES_SETTING,
      name = AppModuleAndroidUtils.getString(
        R.string.setting_media_viewer_offscreen_pages_count,
        ChanSettings.mediaViewerOffscreenPagesCount()
      ),
      enabled = !ChanSettings.isLowRamDevice()
    )

    options += FloatingListMenuItem(
      key = ACTION_REORDER_MEDIA_VIEWER_ACTIONS,
      name = getString(R.string.setting_media_viewer_reorder_actions)
    )

    if (isDevBuild()) {
      options += FloatingListMenuItem(
        key = ACTION_VIEW_PAGER_AUTO_SWIPE,
        name = getString(R.string.setting_media_viewer_auto_swipe)
      )
    }

    return options
  }

  private fun handleMenuItemClick(
    context: Context,
    mediaViewerAdapter: MediaViewerAdapter,
    clickedItem: FloatingListMenuItem,
    handleClickedOption: (Int) -> Unit,
  ) {
    when (clickedItem.key as Int) {
      ACTION_DRAW_BEHIND_NOTCH -> {
        ChanSettings.mediaViewerDrawBehindNotch.toggle()
        showToastFunc(R.string.restart_the_media_viewer)
      }
      ACTION_ALLOW_IMAGE_TRANSPARENCY -> {
        ChanSettings.transparencyOn.toggle()
        mediaViewerAdapter.updateTransparency()
      }
      ACTION_AUTO_REVEAL_SPOILERS -> {
        ChanSettings.mediaViewerRevealImageSpoilers.toggle()
      }
      ACTION_VIDEO_AUTO_LOOP -> {
        ChanSettings.videoAutoLoop.toggle()
      }
      ACTION_VIDEO_START_MUTED -> {
        ChanSettings.videoDefaultMuted.toggle()
      }
      ACTION_VIDEO_START_MUTED_WITH_HEADSET -> {
        ChanSettings.headsetDefaultMuted.toggle()
      }
      ACTION_VIDEO_ALWAYS_RESET_TO_START -> {
        ChanSettings.videoAlwaysResetToStart.toggle()
      }
      ACTION_AUTO_SWIPE_AFTER_DOWNLOAD -> {
        ChanSettings.mediaViewerAutoSwipeAfterDownload.toggle()
      }
      ACTION_PAUSE_PLAYERS_WHEN_IN_BG -> {
        ChanSettings.mediaViewerPausePlayersWhenInBackground.toggle()
      }
      ACTION_ENABLE_SOUND_POSTS -> {
        ChanSettings.mediaViewerSoundPostsEnabled.toggle()
        showToastFunc(R.string.restart_the_media_viewer)
      }
      ACTION_USE_MPV -> {
        ChanSettings.useMpvVideoPlayer.toggle()
        handleClickedOption(ACTION_USE_MPV)
      }
      ACTION_MEDIA_VIEWER_GESTURE_SETTINGS -> {
        val mediaViewerGesturesSettingsController = MediaViewerGesturesSettingsController(context)
        presentControllerFunc(mediaViewerGesturesSettingsController)
      }
      ACTION_MAX_OFFSCREEN_PAGES_SETTING -> {
        showMediaViewerOffscreenPagesSelector(context)
      }
      ACTION_REORDER_MEDIA_VIEWER_ACTIONS -> {
        val reorderableMediaViewerActions = PersistableChanState.reorderableMediaViewerActions.get()

        val items = reorderableMediaViewerActions.mediaViewerActionButtons()
          .map { button -> SimpleListItemsReorderingController.SimpleReorderableItem(button.id, button.title) }

        val controller = SimpleListItemsReorderingController(
          context = context,
          items = items,
          onApplyClicked = { itemsReordered ->
            val reorderedButtons = ReorderableMediaViewerActions(itemsReordered.map { it.id })
            PersistableChanState.reorderableMediaViewerActions.set(reorderedButtons)

            AppModuleAndroidUtils.showToast(context, R.string.restart_the_media_viewer)
          }
        )

        presentControllerFunc(controller)
      }
      ACTION_VIEW_PAGER_AUTO_SWIPE -> {
        handleClickedOption(ACTION_VIEW_PAGER_AUTO_SWIPE)
      }
    }
  }

  private fun showMediaViewerOffscreenPagesSelector(context: Context) {
    val options = mutableListOf<FloatingListMenuItem>()

    options += CheckableFloatingListMenuItem(
      key = ACTION_MEDIA_VIEWER_ONE_OFFSCREEN_PAGE,
      name = AppModuleAndroidUtils.getString(R.string.setting_media_viewer_one_offscreen_page),
      isCurrentlySelected = ChanSettings.mediaViewerOffscreenPagesCount() == 1
    )

    options += CheckableFloatingListMenuItem(
      key = ACTION_MEDIA_VIEWER_TWO_OFFSCREEN_PAGES,
      name = AppModuleAndroidUtils.getString(R.string.setting_media_viewer_two_offscreen_page),
      isCurrentlySelected = ChanSettings.mediaViewerOffscreenPagesCount() == 2
    )

    val floatingListMenuController = FloatingListMenuController(
      context,
      globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      options,
      itemClickListener = { clickedItem ->
        var selectedPagesCount = when (clickedItem.key as Int) {
          ACTION_MEDIA_VIEWER_ONE_OFFSCREEN_PAGE -> 1
          ACTION_MEDIA_VIEWER_TWO_OFFSCREEN_PAGES -> 2
          else -> 1
        }

        if (selectedPagesCount < 1) {
          selectedPagesCount = 1
        }

        if (selectedPagesCount > 2) {
          selectedPagesCount = 2
        }

        ChanSettings.mediaViewerMaxOffscreenPages.set(selectedPagesCount)
        showToastFunc(R.string.restart_the_media_viewer)
      }
    )

    presentControllerFunc(floatingListMenuController)
  }

  companion object {
    const val ACTION_ALLOW_IMAGE_TRANSPARENCY = 100
    const val ACTION_AUTO_REVEAL_SPOILERS = 101
    const val ACTION_VIDEO_AUTO_LOOP = 102
    const val ACTION_VIDEO_START_MUTED = 103
    const val ACTION_VIDEO_START_MUTED_WITH_HEADSET = 104
    const val ACTION_VIDEO_ALWAYS_RESET_TO_START = 105
    const val ACTION_AUTO_SWIPE_AFTER_DOWNLOAD = 106
    const val ACTION_MEDIA_VIEWER_GESTURE_SETTINGS = 107
    const val ACTION_MAX_OFFSCREEN_PAGES_SETTING = 108
    const val ACTION_DRAW_BEHIND_NOTCH = 109
    const val ACTION_ENABLE_SOUND_POSTS = 110
    const val ACTION_USE_MPV = 111
    const val ACTION_PAUSE_PLAYERS_WHEN_IN_BG = 112
    const val ACTION_VIEW_PAGER_AUTO_SWIPE = 113
    const val ACTION_REORDER_MEDIA_VIEWER_ACTIONS = 114

    const val ACTION_MEDIA_VIEWER_ONE_OFFSCREEN_PAGE = 200
    const val ACTION_MEDIA_VIEWER_TWO_OFFSCREEN_PAGES = 201
  }

}