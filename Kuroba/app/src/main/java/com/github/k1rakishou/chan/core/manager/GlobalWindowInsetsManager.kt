package com.github.k1rakishou.chan.core.manager

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.k1rakishou.chan.ui.misc.ConstraintLayoutBias
import com.github.k1rakishou.chan.utils.FullScreenUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.updateMargins

class GlobalWindowInsetsManager {
  private val displaySize = Point(0, 0)

  private var initialized = false

  var isKeyboardOpened = false
    private set
  var keyboardHeight = 0
    private set

  private var lastTouchCoordinates = Point(0, 0)

  private val currentInsets = Rect()
  private val callbacksAwaitingInsetsDispatch = ArrayList<Runnable>()
  private val callbacksAwaitingKeyboardHidden = ArrayList<Runnable>()
  private val callbacksAwaitingKeyboardVisible = ArrayList<Runnable>()
  private val insetsUpdatesListeners = HashSet<WindowInsetsListener>()
  private val keyboardUpdatesListeners = HashSet<KeyboardStateListener>()

  fun listenForWindowInsetsChanges(window: Window, mainRootLayoutMargins: View?) {
    ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, insets ->
      val isKeyboardOpen = FullScreenUtils.isKeyboardShown(view, insets.systemWindowInsetBottom)

      updateInsets(
        insets.replaceSystemWindowInsets(
          insets.systemWindowInsetLeft,
          insets.systemWindowInsetTop,
          insets.systemWindowInsetRight,
          FullScreenUtils.calculateDesiredBottomInset(view, insets.systemWindowInsetBottom)
        )
      )

      updateKeyboardHeight(
        FullScreenUtils.calculateDesiredRealBottomInset(view, insets.systemWindowInsetBottom)
      )

      updateIsKeyboardOpened(isKeyboardOpen)
      fireCallbacks()
      fireInsetsUpdateCallbacks()

      mainRootLayoutMargins?.updateMargins(left = left(), right = right())

      val bottomInset = FullScreenUtils.calculateDesiredRealBottomInset(
        view,
        insets.systemWindowInsetBottom
      )

      return@setOnApplyWindowInsetsListener ViewCompat.onApplyWindowInsets(
        view,
        insets.replaceSystemWindowInsets(0, 0, 0, bottomInset)
      )
    }
  }

  fun updateDisplaySize(context: Context) {
    val dispSize = AndroidUtils.getDisplaySize(context)

    displaySize.set(dispSize.x, dispSize.y)
  }

  fun addInsetsUpdatesListener(listener: WindowInsetsListener) {
    insetsUpdatesListeners += listener
  }

  fun removeInsetsUpdatesListener(listener: WindowInsetsListener) {
    insetsUpdatesListeners -= listener
  }

  fun addKeyboardUpdatesListener(listener: KeyboardStateListener) {
    keyboardUpdatesListeners += listener
  }

  fun removeKeyboardUpdatesListener(listener: KeyboardStateListener) {
    keyboardUpdatesListeners -= listener
  }

  fun updateIsKeyboardOpened(opened: Boolean) {
    if (isKeyboardOpened == opened) {
      return
    }

    isKeyboardOpened = opened
    keyboardUpdatesListeners.forEach { listener -> listener.onKeyboardStateChanged() }

    if (opened) {
      callbacksAwaitingKeyboardVisible.forEach { it.run() }
      callbacksAwaitingKeyboardVisible.clear()
    } else {
      callbacksAwaitingKeyboardHidden.forEach { it.run() }
      callbacksAwaitingKeyboardHidden.clear()
    }
  }

  fun updateLastTouchCoordinates(event: MotionEvent?) {
    if (event == null || event.pointerCount != 1) {
      return
    }

    lastTouchCoordinates.set(event.rawX.toInt(), event.rawY.toInt())
  }

  fun lastTouchCoordinates(): Point = lastTouchCoordinates

  fun lastTouchCoordinatesAsConstraintLayoutBias(): ConstraintLayoutBias {
    val horizBias = lastTouchCoordinates.x.toFloat() / displaySize.x.coerceAtLeast(1).toFloat()
    val vertBias = lastTouchCoordinates.y.toFloat() / displaySize.y.coerceAtLeast(1).toFloat()

    return ConstraintLayoutBias(
      horizBias.coerceIn(0f, 1f),
      vertBias.coerceIn(0f, 1f)
    )
  }

  private fun updateKeyboardHeight(height: Int) {
    keyboardHeight = height.coerceAtLeast(0)
  }

  private fun updateInsets(insets: WindowInsetsCompat) {
    currentInsets.set(
      insets.systemWindowInsetLeft,
      insets.systemWindowInsetTop,
      insets.systemWindowInsetRight,
      insets.systemWindowInsetBottom
    )

    initialized = true
  }

  private fun fireInsetsUpdateCallbacks() {
    insetsUpdatesListeners.forEach { listener -> listener.onInsetsChanged() }
  }

  private fun fireCallbacks() {
    callbacksAwaitingInsetsDispatch.forEach { it.run() }
    callbacksAwaitingInsetsDispatch.clear()
  }

  fun runWhenKeyboardIsHidden(func: Runnable) {
    if (!isKeyboardOpened) {
      func.run()
      return
    }

    callbacksAwaitingKeyboardHidden += func
  }

  fun runWhenKeyboardIsVisible(func: Runnable) {
    if (isKeyboardOpened) {
      func.run()
      return
    }

    callbacksAwaitingKeyboardVisible += func
  }

  fun left() = currentInsets.left
  fun right() = currentInsets.right
  fun top() = currentInsets.top
  fun bottom() = currentInsets.bottom

  companion object {
    private const val TAG = "GlobalWindowInsetsManager"
  }
}

fun interface WindowInsetsListener {
  fun onInsetsChanged()
}

fun interface KeyboardStateListener {
  fun onKeyboardStateChanged()
}