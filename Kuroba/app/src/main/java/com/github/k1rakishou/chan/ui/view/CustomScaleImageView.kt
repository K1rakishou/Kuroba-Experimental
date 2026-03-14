package com.github.k1rakishou.chan.ui.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.AttributeSet
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.github.k1rakishou.chan.utils.appDependencies
import com.github.k1rakishou.common.AndroidUtils.isAndroidO
import com.github.k1rakishou.core_logger.Logger
import kotlin.math.abs
import kotlin.math.min

class CustomScaleImageView @JvmOverloads constructor(
  context: Context,
  attr: AttributeSet? = null
) : SubsamplingScaleImageView(context, attr) {
  private var callback: Callback? = null
  private val panRectF = RectF()

  init {
    setDoubleTapZoomDuration(250)

    if (appDependencies().kurobaSettings.application.isLowRamDeviceBlocking()) {
      Logger.d(TAG, "Using Bitmap.Config.RGB_565")
      setPreferredBitmapConfig(Bitmap.Config.RGB_565)
    } else {
      if (isAndroidO) {
        Logger.d(TAG, "Using Bitmap.Config.HARDWARE")
        setPreferredBitmapConfig(Bitmap.Config.HARDWARE)
      } else {
        Logger.d(TAG, "Using Bitmap.Config.ARGB_8888")
        setPreferredBitmapConfig(Bitmap.Config.ARGB_8888)
      }
    }

    setOnImageEventListener(object : DefaultOnImageEventListener() {
      override fun onReady() {
        val scale = min(width / sWidth.toFloat(), height / sHeight.toFloat())

        if (maxScale < scale * 2f) {
          maxScale = scale * 2f
        }

        setMinimumScaleType(SCALE_TYPE_CUSTOM)
        callback?.onReady()
      }

      override fun onImageLoaded() {
        callback?.onImageLoaded()
      }

      override fun onImageLoadError(e: Exception) {
        callback?.onImageLoadError(e)
      }

      override fun onTileLoadError(e: Exception) {
        callback?.onTileLoadError(e)
      }
    })
  }

  val imageViewportTouchSide: ImageViewportTouchSide
    get() {
      var side = 0

      panRectF.set(0f, 0f, 0f, 0f)
      getPanRemaining(panRectF)

      if (abs(panRectF.left) < MIN_PAN_OFFSET) {
        side = side or ImageViewportTouchSide.Companion.LEFT_SIDE
      }
      if (abs(panRectF.right) < MIN_PAN_OFFSET) {
        side = side or ImageViewportTouchSide.Companion.RIGHT_SIDE
      }
      if (abs(panRectF.top) < MIN_PAN_OFFSET) {
        side = side or ImageViewportTouchSide.Companion.TOP_SIDE
      }
      if (abs(panRectF.bottom) < MIN_PAN_OFFSET) {
        side = side or ImageViewportTouchSide.Companion.BOTTOM_SIDE
      }

      return ImageViewportTouchSide(side)
    }

  fun setCallback(callback: Callback?) {
    this.callback = callback
  }

  class ImageViewportTouchSide(private val side: Int) {
    val isTouchingLeft: Boolean
      get() = (side and LEFT_SIDE) != 0

    val isTouchingRight: Boolean
      get() = (side and RIGHT_SIDE) != 0

    val isTouchingTop: Boolean
      get() = (side and TOP_SIDE) != 0

    val isTouchingBottom: Boolean
      get() = (side and BOTTOM_SIDE) != 0

    val isTouchingAllSides: Boolean
      get() = side == (LEFT_SIDE or RIGHT_SIDE or TOP_SIDE or BOTTOM_SIDE)

    companion object {
      val LEFT_SIDE: Int = 1 shl 0
      val RIGHT_SIDE: Int = 1 shl 1
      val TOP_SIDE: Int = 1 shl 2
      val BOTTOM_SIDE: Int = 1 shl 3
    }
  }

  interface Callback {
    fun onReady()
    fun onImageLoaded()
    fun onImageLoadError(e: Exception)
    fun onTileLoadError(e: Exception)
  }

  companion object {
    private const val TAG = "CustomScaleImageView"
    private const val MIN_PAN_OFFSET = 3f
  }
}
