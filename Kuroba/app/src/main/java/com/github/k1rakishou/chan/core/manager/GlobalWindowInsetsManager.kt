package com.github.k1rakishou.chan.core.manager

import android.graphics.Rect
import androidx.core.view.WindowInsetsCompat

class GlobalWindowInsetsManager {
  private var initialized = false

  var isKeyboardOpened = false
    private set
  var keyboardHeight = 0
    private set

  private val currentInsets = Rect()

  private val callbacksAwaitingInsetsDispatch = ArrayList<Runnable>()
  private val callbacksAwaitingKeyboardHidden = ArrayList<Runnable>()
  private val callbacksAwaitingKeyboardVisible = ArrayList<Runnable>()
  private val insetsUpdatesListeners = HashSet<WindowInsetsListener>()
  private val keyboardUpdatesListeners = HashSet<KeyboardStateListener>()

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

  fun updateKeyboardHeight(height: Int) {
    keyboardHeight = height.coerceAtLeast(0)
  }

  fun updateInsets(insets: WindowInsetsCompat) {
    currentInsets.set(
      insets.systemWindowInsetLeft,
      insets.systemWindowInsetTop,
      insets.systemWindowInsetRight,
      insets.systemWindowInsetBottom
    )

    initialized = true
  }

  fun fireInsetsUpdateCallbacks() {
    insetsUpdatesListeners.forEach { listener -> listener.onInsetsChanged() }
  }

  fun fireCallbacks() {
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