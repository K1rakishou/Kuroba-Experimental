package com.github.k1rakishou.chan.features.media_viewer.helper

import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerAdapter
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.view.floating_menu.CheckableFloatingListMenuItem
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils

class MediaViewerMenuHelper(
  private val globalWindowInsetsManager: GlobalWindowInsetsManager,
  private val presentControllerFunc: (Controller) -> Unit,
  private val showToastFunc: (Int) -> Unit
) {

  fun onMediaViewerOptionsClick(context: Context, mediaViewerAdapter: MediaViewerAdapter) {
    val mediaLongClickOptions = buildMediaViewerOptions()
    if (mediaLongClickOptions.isEmpty()) {
      return
    }

    val floatingListMenuController = FloatingListMenuController(
      context,
      globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      mediaLongClickOptions,
      itemClickListener = { clickedItem ->
        handleMenuItemClick(context, mediaViewerAdapter, clickedItem)
      }
    )

    presentControllerFunc(floatingListMenuController)
  }

  private fun buildMediaViewerOptions(): List<FloatingListMenuItem> {
    val options = mutableListOf<FloatingListMenuItem>()

    options += CheckableFloatingListMenuItem(
      key = ACTION_ALLOW_IMAGE_TRANSPARENCY,
      name = AppModuleAndroidUtils.getString(R.string.action_allow_image_transparency),
      isCurrentlySelected = ChanSettings.transparencyOn.get()
    )

    options += CheckableFloatingListMenuItem(
      key = ACTION_AUTO_REVEAL_SPOILERS,
      name = AppModuleAndroidUtils.getString(R.string.settings_reveal_image_spoilers),
      isCurrentlySelected = ChanSettings.revealImageSpoilers.get()
    )

    return options
  }

  private fun handleMenuItemClick(
    context: Context,
    mediaViewerAdapter: MediaViewerAdapter,
    clickedItem: FloatingListMenuItem
  ) {
    when (clickedItem.key as Int) {
      ACTION_ALLOW_IMAGE_TRANSPARENCY -> {
        ChanSettings.transparencyOn.toggle()
        mediaViewerAdapter.updateTransparency()
      }
      ACTION_AUTO_REVEAL_SPOILERS -> {
        ChanSettings.revealImageSpoilers.toggle()
      }
    }
  }

  companion object {
    const val ACTION_ALLOW_IMAGE_TRANSPARENCY = 100
    const val ACTION_AUTO_REVEAL_SPOILERS = 101
  }

}