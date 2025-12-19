package com.github.k1rakishou.chan.core.manager

import android.view.HapticFeedbackConstants
import android.view.View
import com.github.k1rakishou.common.AndroidUtils.isAndroid11
import com.github.k1rakishou.common.AndroidUtils.isAndroid14
import com.github.k1rakishou.core_logger.Logger

class HapticFeedbackManager {
  private var _view: View? = null

  fun onAttachedToView(view: View) {
    _view = view
  }

  fun onDetachedFromView() {
    _view = null
  }

  fun tap() {
    Logger.verbose(TAG) { "tap()" }
    _view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
  }

  fun toggleOn() {
    Logger.verbose(TAG) { "toggleOn()" }
    complainIfViewIsNull()

    if (isAndroid14) {
      _view?.performHapticFeedback(HapticFeedbackConstants.TOGGLE_ON)
    } else {
      tapFallback()
    }
  }

  fun toggleOff() {
    Logger.verbose(TAG) { "toggleOff()" }
    complainIfViewIsNull()

    if (isAndroid14) {
      _view?.performHapticFeedback(HapticFeedbackConstants.TOGGLE_OFF)
    } else {
      tapFallback()
    }
  }

  fun gestureStart() {
    Logger.verbose(TAG) { "gestureStart()" }
    complainIfViewIsNull()

    if (isAndroid11) {
      _view?.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
    } else {
      tapFallback()
    }
  }

  fun gestureEnd() {
    Logger.verbose(TAG) { "gestureEnd()" }
    complainIfViewIsNull()

    if (isAndroid11) {
      _view?.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
    } else {
      tapFallback()
    }
  }

  private fun complainIfViewIsNull() {
    if (_view == null) {
      Logger.warning(TAG) { "view is null!" }
    }
  }

  private fun tapFallback() {
    _view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
  }

  companion object {
    private const val TAG = "HapticFeedbackManager"
  }

}