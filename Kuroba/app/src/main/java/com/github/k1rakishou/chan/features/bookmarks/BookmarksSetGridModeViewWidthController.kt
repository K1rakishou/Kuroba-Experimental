package com.github.k1rakishou.chan.features.bookmarks

import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.settings.ChanSettings
import com.github.k1rakishou.chan.ui.controller.BaseFloatingController
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableSlider
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView

class BookmarksSetGridModeViewWidthController(
  context: Context
) : BaseFloatingController(context) {
  private lateinit var outsideArea: ConstraintLayout
  private lateinit var bookmarkViewWidthSlider: ColorizableSlider
  private lateinit var minValueTextView: ColorizableTextView
  private lateinit var maxValueTextView: ColorizableTextView
  private lateinit var cancel: ColorizableBarButton
  private lateinit var apply: ColorizableBarButton

  private var presenting = true

  override fun getLayoutId(): Int = R.layout.controller_bookmarks_set_grid_view_width

  override fun onCreate() {
    super.onCreate()

    outsideArea = view.findViewById(R.id.outside_area)
    bookmarkViewWidthSlider = view.findViewById(R.id.view_width_slider)
    minValueTextView = view.findViewById(R.id.grid_bookmark_view_min_width_text)
    maxValueTextView = view.findViewById(R.id.grid_bookmark_view_max_width_text)
    cancel = view.findViewById(R.id.cancel_button)
    apply = view.findViewById(R.id.apply_button)

    bookmarkViewWidthSlider.valueFrom =
      context.resources.getDimension(R.dimen.thread_grid_bookmark_view_min_width)
    bookmarkViewWidthSlider.valueTo =
      context.resources.getDimension(R.dimen.thread_grid_bookmark_view_max_width)
    bookmarkViewWidthSlider.value = ChanSettings.bookmarkGridViewWidth.get().toFloat()

    minValueTextView.text =
      context.resources.getDimension(R.dimen.thread_grid_bookmark_view_min_width).toString()
    maxValueTextView.text =
      context.resources.getDimension(R.dimen.thread_grid_bookmark_view_max_width).toString()

    cancel.setOnClickListener { pop() }
    outsideArea.setOnClickListener { pop() }

    apply.setOnClickListener {
      val currentValue = ChanSettings.bookmarkGridViewWidth.get()
      val newValue = bookmarkViewWidthSlider.value.toInt()

      if (currentValue != newValue) {
        ChanSettings.bookmarkGridViewWidth.set(newValue)
        showToast(R.string.controller_bookmarks_reopen_bookmarks_screen)
      }

      pop()
    }
  }

  override fun onBack(): Boolean {
    if (presenting) {
      pop()
      return true
    }

    return super.onBack()
  }

  private fun pop() {
    if (!presenting) {
      return
    }

    presenting = false
    stopPresenting()
  }

  override fun onDestroy() {
    super.onDestroy()
  }

}