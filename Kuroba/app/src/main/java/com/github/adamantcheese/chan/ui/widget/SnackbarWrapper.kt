package com.github.adamantcheese.chan.ui.widget

import android.view.View
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.google.android.material.snackbar.Snackbar

class SnackbarWrapper private constructor(
  private var snackbar: Snackbar? = null
) {

  fun dismiss() {
    snackbar?.dismiss()
    snackbar = null
  }

  fun setAction(actionTextId: Int, onClickListener: View.OnClickListener) {
    snackbar?.setAction(actionTextId, onClickListener)
  }

  fun setAction(actionTextId: Int, onClickListener: () -> Unit) {
    snackbar?.setAction(actionTextId) { onClickListener.invoke() }
  }

  fun show() {
    snackbar?.show()
  }

  companion object {
    private val allowedDurations = setOf(
      Snackbar.LENGTH_INDEFINITE,
      Snackbar.LENGTH_SHORT,
      Snackbar.LENGTH_LONG
    )

    @JvmStatic
    fun create(view: View, textId: Int, duration: Int): SnackbarWrapper {
      require(duration in allowedDurations) { "Bad duration" }

      val snackbar = Snackbar.make(view, textId, duration)
      snackbar.isGestureInsetBottomIgnored = false

      AndroidUtils.fixSnackbarText(view.context, snackbar)
      return SnackbarWrapper(snackbar)
    }

    @JvmStatic
    fun create(view: View, text: String, duration: Int): SnackbarWrapper {
      require(duration in allowedDurations) { "Bad duration" }

      val snackbar = Snackbar.make(view, text, duration)
      snackbar.isGestureInsetBottomIgnored = false

      AndroidUtils.fixSnackbarText(view.context, snackbar)
      return SnackbarWrapper(snackbar)
    }
  }
}