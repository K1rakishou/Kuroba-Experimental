package com.github.k1rakishou.chan.ui.view

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableString
import android.text.method.MovementMethod
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.textclassifier.TextClassifier
import androidx.appcompat.widget.AppCompatTextView
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.utils.ViewUtils.emulateMotionEvent
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.isActionUpOrCancel
import com.github.k1rakishou.common.isNotNullNorEmpty
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Some of the ideas were taken from Dashchan project. Thanks to Dashchan dev for inspiration.
class PostCommentTextView @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defAttrStyle: Int = 0
) : AppCompatTextView(context, attributeSet, defAttrStyle) {
  private var selectionMode = false
  private var needToCancelOtherEvents = false
  private var emulatingDoubleTap = false
  private var linkConsumesEvents = false

  private var touchEventListener: OnTouchListener? = null
  private var customMovementMethod: MovementMethod? = null

  private var activeSelectionJob: Job? = null
  private var endEmulatingDoubleTapJob: Job? = null

  private val scope = KurobaCoroutineScope()

  init {
    if (AndroidUtils.isAndroidO()) {
      setTextClassifier(TextClassifier.NO_OP)
    }
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()

    activeSelectionJob?.cancel()
    activeSelectionJob = null
    endEmulatingDoubleTapJob?.cancel()
    endEmulatingDoubleTapJob = null
  }

  fun customTouchEventListener(listener: OnTouchListener?) {
    this.touchEventListener = listener
  }

  fun customMovementMethod(movementMethod: MovementMethod?) {
    this.customMovementMethod = movementMethod
  }

  fun startSelectionMode(clickX: Float, clickY: Float) {
    if (activeSelectionJob != null) {
      return
    }

    activeSelectionJob = scope.launch {
      val commentTextSpannable = when {
        text is Spannable -> text as Spannable
        text.isNotNullNorEmpty() -> SpannableString(text)
        else -> null
      }

      if (commentTextSpannable.isNullOrEmpty()) {
        return@launch
      }

      endEmulatingDoubleTapJob?.cancel()
      endEmulatingDoubleTapJob = null

      selectionMode = true
      requestFocus()

      val x = clickX
      val y = clickY

      needToCancelOtherEvents = true

      emulatingDoubleTap = true
      emulateMotionEvent(DOWN_TIME_TAGGED, MotionEvent.ACTION_DOWN, x, y)
      emulateMotionEvent(DOWN_TIME_TAGGED, MotionEvent.ACTION_UP, x, y)
      emulateMotionEvent(DOWN_TIME_TAGGED, MotionEvent.ACTION_DOWN, x, y)
      emulateMotionEvent(DOWN_TIME_TAGGED, MotionEvent.ACTION_UP, x, y)

      endEmulatingDoubleTapJob = scope.launch {
        delay(500)

        emulatingDoubleTap = false
      }

      activeSelectionJob = null
    }
  }

  fun endSelectionMode() {
    selectionMode = false
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    val action = event.actionMasked

    if (needToCancelOtherEvents) {
      needToCancelOtherEvents = false

      val motionEvent = MotionEvent.obtain(0, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, event.x, event.y, 0)
      touchEventListener?.onTouch(this, motionEvent)
      motionEvent.recycle()
    }

    if (selectionMode) {
      if (emulatingDoubleTap && event.downTime != DOWN_TIME_TAGGED) {
        return false
      }

      return super.onTouchEvent(event)
    }

    if (action == MotionEvent.ACTION_DOWN || linkConsumesEvents) {
      linkConsumesEvents = customMovementMethod?.onTouchEvent(this, text as Spannable, event) == true
    }

    if (event.isActionUpOrCancel()) {
      linkConsumesEvents = false
    }

    if (linkConsumesEvents) {
      return true
    }

    touchEventListener?.onTouch(this, event)

    return true
  }

  override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
    try {
      super.onFocusChanged(focused, direction, previouslyFocusedRect)
    } catch (error: IndexOutOfBoundsException) {
      // java.lang.IndexOutOfBoundsException: setSpan (-1 ... -1) starts before 0
    }
  }

  companion object {
    private const val DOWN_TIME_TAGGED = 1911L
  }
}