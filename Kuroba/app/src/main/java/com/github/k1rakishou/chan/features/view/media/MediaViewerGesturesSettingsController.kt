package com.github.k1rakishou.chan.features.view.media

import android.content.Context
import android.widget.RadioGroup
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarScope
import com.github.k1rakishou.chan.ui.controller.base.BaseFloatingController
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton
import com.github.k1rakishou.v2.parameters.ImageGestureActionType

class MediaViewerGesturesSettingsController(context: Context) : BaseFloatingController(context) {
  private lateinit var outsideArea: ConstraintLayout
  private lateinit var cancel: ColorizableBarButton
  private lateinit var apply: ColorizableBarButton
  private lateinit var swipeUpGroup: RadioGroup
  private lateinit var swipeDownGroup: RadioGroup

  override fun getLayoutId(): Int = R.layout.controller_media_viewer_gestures_settings

  override val snackbarScope: SnackbarScope
    get() = SnackbarScope.MediaViewer()

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    outsideArea = view.findViewById(R.id.outside_area)
    cancel = view.findViewById(R.id.cancel_button)
    apply = view.findViewById(R.id.apply_button)
    swipeUpGroup = view.findViewById(R.id.image_viewer_gestures_swipe_up_group)
    swipeDownGroup = view.findViewById(R.id.image_viewer_gestures_swipe_down_group)

    cancel.setOnClickListener { pop() }
    outsideArea.setOnClickListener { pop() }

    when (kurobaSettings.application.mediaViewerTopGestureAction.readBlocking()) {
      ImageGestureActionType.SaveImage -> {
        swipeUpGroup.check(R.id.image_viewer_gestures_swipe_up_save_image)
      }
      ImageGestureActionType.CloseImage -> {
        swipeUpGroup.check(R.id.image_viewer_gestures_swipe_up_close_image)
      }
      ImageGestureActionType.OpenAlbum -> {
        swipeUpGroup.check(R.id.image_viewer_gestures_swipe_up_open_album)
      }
      ImageGestureActionType.Disabled -> {
        swipeUpGroup.check(R.id.image_viewer_gestures_swipe_up_disabled)
      }
      else -> swipeUpGroup.check(R.id.image_viewer_gestures_swipe_up_close_image)
    }

    when (kurobaSettings.application.mediaViewerBottomGestureAction.readBlocking()) {
      ImageGestureActionType.SaveImage -> {
        swipeDownGroup.check(R.id.image_viewer_gestures_swipe_down_save_image)
      }
      ImageGestureActionType.CloseImage -> {
        swipeDownGroup.check(R.id.image_viewer_gestures_swipe_down_close_image)
      }
      ImageGestureActionType.OpenAlbum -> {
        swipeDownGroup.check(R.id.image_viewer_gestures_swipe_down_open_album)
      }
      ImageGestureActionType.Disabled -> {
        swipeDownGroup.check(R.id.image_viewer_gestures_swipe_down_disabled)
      }
      else -> swipeDownGroup.check(R.id.image_viewer_gestures_swipe_down_close_image)
    }

    apply.setOnClickListener {
      val swipeUpGesture = when (swipeUpGroup.checkedRadioButtonId) {
        R.id.image_viewer_gestures_swipe_up_close_image -> {
          ImageGestureActionType.CloseImage
        }
        R.id.image_viewer_gestures_swipe_up_save_image -> {
          ImageGestureActionType.SaveImage
        }
        R.id.image_viewer_gestures_swipe_up_open_album -> {
          ImageGestureActionType.OpenAlbum
        }
        R.id.image_viewer_gestures_swipe_up_disabled -> {
          ImageGestureActionType.Disabled
        }
        else -> throw IllegalArgumentException("Unknown checkedRadioButtonId: " +
          "${swipeUpGroup.checkedRadioButtonId}")
      }

      val swipeDownGesture = when (swipeDownGroup.checkedRadioButtonId) {
        R.id.image_viewer_gestures_swipe_down_close_image -> {
          ImageGestureActionType.CloseImage
        }
        R.id.image_viewer_gestures_swipe_down_save_image -> {
          ImageGestureActionType.SaveImage
        }
        R.id.image_viewer_gestures_swipe_down_open_album -> {
          ImageGestureActionType.OpenAlbum
        }
        R.id.image_viewer_gestures_swipe_down_disabled -> {
          ImageGestureActionType.Disabled
        }
        else -> throw IllegalArgumentException("Unknown checkedRadioButtonId: " +
          "${swipeUpGroup.checkedRadioButtonId}")
      }

      kurobaSettings.application.mediaViewerTopGestureAction.writeAsync(swipeUpGesture)
      kurobaSettings.application.mediaViewerBottomGestureAction.writeAsync(swipeDownGesture)

      snackbarManager.toast(messageId = R.string.restart_the_media_viewer)
      pop()
    }
  }

}