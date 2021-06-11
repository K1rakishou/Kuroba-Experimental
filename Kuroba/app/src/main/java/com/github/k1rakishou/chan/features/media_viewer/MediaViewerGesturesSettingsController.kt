package com.github.k1rakishou.chan.features.media_viewer

import android.content.Context
import android.widget.RadioGroup
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.controller.BaseFloatingController
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton

class MediaViewerGesturesSettingsController(context: Context) : BaseFloatingController(context) {
  private lateinit var outsideArea: ConstraintLayout
  private lateinit var cancel: ColorizableBarButton
  private lateinit var apply: ColorizableBarButton
  private lateinit var swipeUpGroup: RadioGroup
  private lateinit var swipeDownGroup: RadioGroup

  override fun getLayoutId(): Int = R.layout.controller_media_viewer_gestures_settings

  override fun injectDependencies(component: ActivityComponent) {
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

    when (ChanSettings.mediaViewerTopGestureAction.get()) {
      ChanSettings.ImageGestureActionType.SaveImage -> {
        swipeUpGroup.check(R.id.image_viewer_gestures_swipe_up_save_image)
      }
      ChanSettings.ImageGestureActionType.CloseImage -> {
        swipeUpGroup.check(R.id.image_viewer_gestures_swipe_up_close_image)
      }
      ChanSettings.ImageGestureActionType.Disabled -> {
        swipeUpGroup.check(R.id.image_viewer_gestures_swipe_up_disabled)
      }
      else -> swipeUpGroup.check(R.id.image_viewer_gestures_swipe_up_close_image)
    }

    when (ChanSettings.mediaViewerBottomGestureAction.get()) {
      ChanSettings.ImageGestureActionType.SaveImage -> {
        swipeDownGroup.check(R.id.image_viewer_gestures_swipe_down_save_image)
      }
      ChanSettings.ImageGestureActionType.CloseImage -> {
        swipeDownGroup.check(R.id.image_viewer_gestures_swipe_down_close_image)
      }
      ChanSettings.ImageGestureActionType.Disabled -> {
        swipeDownGroup.check(R.id.image_viewer_gestures_swipe_down_disabled)
      }
      else -> swipeDownGroup.check(R.id.image_viewer_gestures_swipe_down_close_image)
    }

    apply.setOnClickListener {
      val swipeUpGesture = when (swipeUpGroup.checkedRadioButtonId) {
        R.id.image_viewer_gestures_swipe_up_close_image -> {
          ChanSettings.ImageGestureActionType.CloseImage
        }
        R.id.image_viewer_gestures_swipe_up_save_image -> {
          ChanSettings.ImageGestureActionType.SaveImage
        }
        R.id.image_viewer_gestures_swipe_up_disabled -> {
          ChanSettings.ImageGestureActionType.Disabled
        }
        else -> throw IllegalArgumentException("Unknown checkedRadioButtonId: " +
          "${swipeUpGroup.checkedRadioButtonId}")
      }

      val swipeDownGesture = when (swipeDownGroup.checkedRadioButtonId) {
        R.id.image_viewer_gestures_swipe_down_close_image -> {
          ChanSettings.ImageGestureActionType.CloseImage
        }
        R.id.image_viewer_gestures_swipe_down_save_image -> {
          ChanSettings.ImageGestureActionType.SaveImage
        }
        R.id.image_viewer_gestures_swipe_down_disabled -> {
          ChanSettings.ImageGestureActionType.Disabled
        }
        else -> throw IllegalArgumentException("Unknown checkedRadioButtonId: " +
          "${swipeUpGroup.checkedRadioButtonId}")
      }

      ChanSettings.mediaViewerTopGestureAction.set(swipeUpGesture)
      ChanSettings.mediaViewerBottomGestureAction.set(swipeDownGesture)

      showToast(R.string.restart_the_media_viewer)
      pop()
    }
  }

}