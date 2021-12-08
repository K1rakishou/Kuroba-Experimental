package com.github.k1rakishou.chan.ui.helper

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

abstract class KurobaViewGroup @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defAttrStyle: Int = 0
) : ViewGroup(context, attributeSet, defAttrStyle) {

  protected fun mspec(size: Int, mode: Int): Int {
    return MeasureSpec.makeMeasureSpec(size, mode)
  }

  protected fun unspecified(): Int = mspec(0, MeasureSpec.UNSPECIFIED)
  protected fun atMost(size: Int): Int = mspec(size, MeasureSpec.AT_MOST)
  protected fun exactly(size: Int): Int = mspec(size, MeasureSpec.EXACTLY)

  protected fun measureHorizontal(vararg measureResults: MeasureResult): MeasureResult {
    return MeasureResult(
      takenWidth = measureResults.sumOf { it.takenWidth },
      takenHeight = measureResults.maxOf { it.takenHeight }
    )
  }

  protected fun measureVertical(vararg measureResults: MeasureResult): MeasureResult {
    return MeasureResult(
      takenWidth = measureResults.maxOf { it.takenWidth },
      takenHeight = measureResults.sumOf { it.takenHeight }
    )
  }

  protected fun measure(view: View, widthSpec: Int, heightSpec: Int): MeasureResult {
    if (view.visibility == View.GONE) {
      view.measure(exactly(0), exactly(0))
      return MeasureResult.EMPTY
    }

    view.measure(widthSpec, heightSpec)

    return MeasureResult(
      takenWidth = view.measuredWidth,
      takenHeight = view.measuredHeight
    )
  }

  protected data class LayoutResult(
    var left: Int = 0,
    var top: Int = 0
  ) {

    fun reset(newLeft: Int = 0, newTop: Int = 0) {
      left = newLeft
      top = newTop
    }

    inline fun withOffset(
      vertical: Int = 0,
      horizontal: Int = 0,
      crossinline func: () -> Unit
    ) {
      left += horizontal
      top += vertical

      func()

      left -= horizontal
      top -= vertical
    }

    fun vertical(vararg views: View) {
      for (view in views) {
        if (view.visibility == View.GONE) {
          view.layout(left, top, left, top)
          continue
        }

        view.layout(left, top, left + view.measuredWidth, top + view.measuredHeight)
        top += view.measuredHeight
      }
    }

    fun horizontal(vararg views: View) {
      for (view in views) {
        if (view.visibility == View.GONE) {
          view.layout(left, top, left, top)
          continue
        }

        view.layout(left, top, left + view.measuredWidth, top + view.measuredHeight)
        left += view.measuredWidth
      }
    }

    fun layout(view: View) {
      if (view.visibility == View.GONE) {
        return
      }

      view.layout(left, top, left + view.measuredWidth, top + view.measuredHeight)
    }

    fun offset(vertical: Int = 0, horizontal: Int = 0) {
      left += horizontal
      top += vertical
    }

  }

  protected data class MeasureResult(
    var takenWidth: Int = 0,
    var takenHeight: Int = 0
  ) {

    fun reset() {
      takenWidth = 0
      takenHeight = 0
    }

    fun addVertical(measureResult: MeasureResult) {
      this.takenHeight += measureResult.takenHeight
    }

    fun addVertical(size: Int) {
      this.takenHeight += size
    }

    fun subVertical(size: Int) {
      this.takenHeight -= size
    }

    fun addHorizontal(size: Int) {
      this.takenWidth += size
    }

    fun addHorizontal(measureResult: MeasureResult) {
      this.takenWidth += measureResult.takenWidth
    }

    companion object {
      val EMPTY = MeasureResult(0, 0)
    }

  }

}