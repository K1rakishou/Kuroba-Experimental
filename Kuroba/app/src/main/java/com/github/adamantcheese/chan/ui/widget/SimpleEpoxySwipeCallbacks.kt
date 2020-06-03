package com.github.adamantcheese.chan.ui.widget

import android.graphics.Canvas
import android.view.View
import com.airbnb.epoxy.EpoxyModel
import com.airbnb.epoxy.EpoxyTouchHelper

open class SimpleEpoxySwipeCallbacks<T : EpoxyModel<*>> : EpoxyTouchHelper.SwipeCallbacks<T>() {

  override fun clearView(model: T, itemView: View?) {
    super.clearView(model, itemView)
  }

  override fun onSwipeCompleted(model: T, itemView: View?, position: Int, direction: Int) {
  }

  override fun isSwipeEnabledForModel(model: T): Boolean {
    return super.isSwipeEnabledForModel(model)
  }

  override fun onSwipeProgressChanged(model: T, itemView: View?, swipeProgress: Float, canvas: Canvas?) {
    super.onSwipeProgressChanged(model, itemView, swipeProgress, canvas)
  }

  override fun onSwipeReleased(model: T, itemView: View?) {
    super.onSwipeReleased(model, itemView)
  }

  override fun onSwipeStarted(model: T, itemView: View?, adapterPosition: Int) {
    super.onSwipeStarted(model, itemView, adapterPosition)
  }

}