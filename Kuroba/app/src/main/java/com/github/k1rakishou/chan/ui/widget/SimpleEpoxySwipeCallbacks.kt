package com.github.k1rakishou.chan.ui.widget

import android.view.View
import com.airbnb.epoxy.EpoxyModel
import com.airbnb.epoxy.EpoxyTouchHelper

open class SimpleEpoxySwipeCallbacks<T : EpoxyModel<*>> : EpoxyTouchHelper.SwipeCallbacks<T>() {

  override fun onSwipeCompleted(model: T, itemView: View?, position: Int, direction: Int) {
  }

}