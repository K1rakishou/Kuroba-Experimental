package com.github.k1rakishou.chan.core.helper

import android.view.View

class MeasurementHelper {

  fun measure(
    initialWidth: Int = 0,
    initialHeight: Int = 0,
    measureFunc: Measurement.() -> Unit
  ): MeasureResult {
    val measurement = Measurement(_totalWidth = initialWidth, _totalHeight = initialHeight)
    measureFunc(measurement)

    return MeasureResult(
      totalWidth = measurement.totalWidth,
      totalHeight = measurement.totalHeight
    )
  }

  class Measurement(
    private var _totalWidth: Int = 0,
    private var _totalHeight: Int = 0
  ) {
    val totalWidth: Int
      get() = _totalWidth
    val totalHeight: Int
      get() = _totalHeight

    fun vertical(
      accumulate: Boolean = true,
      func: Element.Vertical.() -> Unit
    ): Int {
      val element = Element.Vertical()
      func(element)

      if (accumulate) {
        _totalHeight += element.height
      }

      return element.height
    }

    fun maxOfVertical(
      accumulate: Boolean = true,
      func: Element.MaxOfVertical.() -> Unit
    ): Int {
      val element = Element.MaxOfVertical()
      func(element)

      if (accumulate) {
        _totalHeight += element.maxHeight
      }

      return element.maxHeight
    }

    fun horizontal(
      accumulate: Boolean = true,
      func: Element.Horizontal.() -> Unit
    ): Int {
      val element = Element.Horizontal()
      func(element)

      if (accumulate) {
        _totalWidth += element.width
      }

      return element.width
    }
  }

  sealed class Element {
    abstract fun element(func: () -> Int)
    abstract fun view(view: View, widthMeasureSpec: Int, heightMeasureSpec: Int)

    class MaxOfVertical : Element() {
      var maxHeight = 0
        private set

      override fun element(func: () -> Int) {
        maxHeight = Math.max(maxHeight, func())
      }

      override fun view(view: View, widthMeasureSpec: Int, heightMeasureSpec: Int) {
        view.measure(widthMeasureSpec, heightMeasureSpec)
        maxHeight = Math.max(maxHeight, view.measuredHeight)
      }
    }

    class Vertical : Element() {
      var height = 0
        private set

      override fun element(func: () -> Int) {
        height += func()
      }

      override fun view(view: View, widthMeasureSpec: Int, heightMeasureSpec: Int) {
        view.measure(widthMeasureSpec, heightMeasureSpec)
        height += view.measuredHeight
      }
    }

    class Horizontal : Element() {
      var width = 0
        private set

      override fun element(func: () -> Int) {
        width += func()
      }

      override fun view(view: View, widthMeasureSpec: Int, heightMeasureSpec: Int) {
        view.measure(widthMeasureSpec, heightMeasureSpec)
        width += view.measuredWidth
      }
    }

  }

  data class MeasureResult(val totalWidth: Int, val totalHeight: Int)
}