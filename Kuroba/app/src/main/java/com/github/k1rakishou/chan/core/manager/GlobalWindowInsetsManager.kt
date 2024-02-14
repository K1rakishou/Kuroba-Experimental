package com.github.k1rakishou.chan.core.manager

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.Window
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.k1rakishou.chan.ui.compose.KurobaWindowInsets
import com.github.k1rakishou.chan.ui.misc.ConstraintLayoutBias
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.pxToDp
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.updateMargins

class GlobalWindowInsetsManager {
  var isKeyboardOpened = false
    private set
  var keyboardHeight = 0
    private set

  private val displaySize = Point(0, 0)
  private val lastTouchCoordinates = Point(0, 0)

  private val currentInsets = Rect()
  private val _currentWindowInsets = mutableStateOf(KurobaWindowInsets())
  val currentWindowInsets: State<KurobaWindowInsets>
    get() = _currentWindowInsets

  var currentInsetsCompose = mutableStateOf(PaddingValues())
    private set

  private val callbacksAwaitingInsetsDispatch = ArrayList<Runnable>()
  private val callbacksAwaitingKeyboardHidden = ArrayList<Runnable>()
  private val callbacksAwaitingKeyboardVisible = ArrayList<Runnable>()
  private val insetsUpdatesListeners = HashSet<WindowInsetsListener>()
  private val keyboardUpdatesListeners = HashSet<KeyboardStateListener>()

  private val attachStateChangeListener = object : View.OnAttachStateChangeListener {
    override fun onViewAttachedToWindow(v: View) {
      v.requestApplyInsets()
    }

    override fun onViewDetachedFromWindow(v: View) {

    }
  }

  fun stopListeningForWindowInsetsChanges(window: Window) {
    val view = window.decorView

    ViewCompat.setOnApplyWindowInsetsListener(view, null)
    view.removeOnAttachStateChangeListener(attachStateChangeListener)
  }

  fun listenForWindowInsetsChanges(window: Window, mainRootLayoutMargins: View?) {
    val view = window.decorView

    val applyWindowInsetsListener = OnApplyWindowInsetsListener { _, insets ->
      val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
      val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())

      val left = Math.max(imeInsets.left, systemBarInsets.left)
      val top = Math.max(imeInsets.top, systemBarInsets.top)
      val right = Math.max(imeInsets.right, systemBarInsets.right)
      val bottom = Math.max(imeInsets.bottom, systemBarInsets.bottom)

      val isKeyboardOpen = imeInsets.bottom > 0
      val newInsets = Rect(left, top, right, bottom)

      if (updateInsets(newInsets) || isKeyboardOpened != isKeyboardOpen) {
        updateKeyboardHeight(imeInsets.bottom)
        updateIsKeyboardOpened(isKeyboardOpen)
        fireCallbacks()
        mainRootLayoutMargins?.updateMargins(left = left(), right = right())

        _currentWindowInsets.value = KurobaWindowInsets(
          left = leftDp(),
          right = rightDp(),
          top = topDp(),
          bottom = bottomDp(),
          keyboardOpened = isKeyboardOpen
        )
      }

      return@OnApplyWindowInsetsListener WindowInsetsCompat.CONSUMED
    }

    ViewCompat.setOnApplyWindowInsetsListener(view, applyWindowInsetsListener)
    view.addOnAttachStateChangeListener(attachStateChangeListener)
    ViewCompat.requestApplyInsets(view)
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

  fun lastTouchCoordinatesAsBiasAlignment(): BiasAlignment {
    val dispWidth = displaySize.x.coerceAtLeast(1).toFloat()
    val dispHeight = displaySize.y.coerceAtLeast(1).toFloat()

    val horizBias = (lastTouchCoordinates.x.toFloat() / dispWidth) - (dispWidth / 2f)
    val vertBias = (lastTouchCoordinates.y.toFloat() / dispHeight) - (dispHeight / 2f)

    return BiasAlignment(
      horizBias.coerceIn(-1f, 1f),
      vertBias.coerceIn(-1f, 1f)
    )
  }

  private fun updateKeyboardHeight(height: Int) {
    keyboardHeight = height.coerceAtLeast(0)
  }

  private fun updateInsets(newInsets: Rect): Boolean {
    if (
      currentInsets.left == newInsets.left
      && currentInsets.right == newInsets.right
      && currentInsets.top == newInsets.top
      && currentInsets.bottom == newInsets.bottom
    ) {
      // Insets weren't changed no need to fire callbacks
      return false
    }

    currentInsets.set(newInsets)

    currentInsetsCompose.value = PaddingValues(
      start = pxToDp(newInsets.left.toFloat()).dp,
      end = pxToDp(newInsets.right.toFloat()).dp,
      top = pxToDp(newInsets.top.toFloat()).dp,
      bottom = pxToDp(newInsets.bottom.toFloat()).dp,
    )

    return true
  }

  private fun fireCallbacks() {
    callbacksAwaitingInsetsDispatch.forEach { it.run() }
    callbacksAwaitingInsetsDispatch.clear()

    insetsUpdatesListeners.forEach { listener -> listener.onInsetsChanged() }
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

  fun leftDp(): Dp = pxToDp(currentInsets.left.toFloat()).dp
  fun rightDp(): Dp = pxToDp(currentInsets.right.toFloat()).dp
  fun topDp(): Dp = pxToDp(currentInsets.top.toFloat()).dp
  fun bottomDp(): Dp = pxToDp(currentInsets.bottom.toFloat()).dp

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