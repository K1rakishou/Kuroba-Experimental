package com.github.k1rakishou.chan.ui.widget

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import coil.size.Size
import coil.size.ViewSizeResolver
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FixedViewSizeResolver<T : View>(
  override val view: T,
  override val subtractPadding: Boolean = true
) : ViewSizeResolver<T> {

  override suspend fun size(): Size {
    // Fast path: the view is already measured.
    getSize()?.let { return it }

    // Slow path: wait for the view to be measured.
    return suspendCancellableCoroutine { continuation ->
      val viewTreeObserver = view.viewTreeObserver

      val preDrawListener = object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
          val size = getSize()
          if (size != null) {
            viewTreeObserver.removePreDrawListenerSafe(this)

            if (continuation.isActive) {
              continuation.resume(size)
            }
          }

          return true
        }
      }

      viewTreeObserver.addOnPreDrawListener(preDrawListener)

      continuation.invokeOnCancellation {
        viewTreeObserver.removePreDrawListenerSafe(preDrawListener)
      }
    }
  }

  private fun getSize(): Size? {
    val width = getWidth() ?: return null
    val height = getHeight() ?: return null
    return Size(width, height)
  }

  private fun getWidth(): Int {
    return getDimension(
      paramSize = view.layoutParams?.width ?: -1,
      viewSize = view.width,
      paddingSize = if (subtractPadding) view.paddingLeft + view.paddingRight else 0,
      isWidth = true
    )
  }

  private fun getHeight(): Int {
    return getDimension(
      paramSize = view.layoutParams?.height ?: -1,
      viewSize = view.height,
      paddingSize = if (subtractPadding) view.paddingTop + view.paddingBottom else 0,
      isWidth = false
    )
  }

  private fun getDimension(
    paramSize: Int,
    viewSize: Int,
    paddingSize: Int,
    isWidth: Boolean
  ): Int {
    // Assume the dimension will match the value in the view's layout params.
    val insetParamSize = paramSize - paddingSize
    if (insetParamSize > 0) {
      return insetParamSize
    }

    // Fallback to the view's current size.
    val insetViewSize = viewSize - paddingSize
    if (insetViewSize > 0) {
      return insetViewSize
    }

    // If the dimension is set to WRAP_CONTENT, fall back to the size of the display.
    if (paramSize == ViewGroup.LayoutParams.WRAP_CONTENT) {
      return view.context.resources.displayMetrics.run { if (isWidth) widthPixels else heightPixels }
    }

    // Unable to resolve the dimension's size.
    return -1
  }

  private fun ViewTreeObserver.removePreDrawListenerSafe(victim: ViewTreeObserver.OnPreDrawListener) {
    if (isAlive) {
      removeOnPreDrawListener(victim)
    } else {
      view.viewTreeObserver.removeOnPreDrawListener(victim)
    }
  }

}