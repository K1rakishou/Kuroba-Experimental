package com.github.adamantcheese.chan.ui.widget

import android.content.Context
import android.view.View
import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.ui.layout.DrawerWidthAdjustingLayout
import com.github.adamantcheese.chan.ui.layout.ThreadLayout
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.github.adamantcheese.chan.ui.view.HidingBottomNavigationView
import com.github.adamantcheese.chan.ui.view.HidingFloatingActionButton
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.AndroidUtils.dp
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.findChild
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import javax.inject.Inject

class SnackbarWrapper private constructor(
  private var snackbar: Snackbar? = null
) {

  @Inject
  lateinit var themeHelper: ThemeHelper

  init {
    Chan.inject(this)

    snackbar?.view?.setBackgroundColor(themeHelper.theme.primaryColor.color)
  }

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
    snackbar?.addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
      override fun onShown(transientBottomBar: Snackbar) {
        super.onShown(transientBottomBar)

        val view = transientBottomBar.view
        updateSnackbarBottomPaddingSuperHacky(view)

        findFab(view)?.let { fab ->
          fab.visibility = View.INVISIBLE
          fab.hide()
        }
      }

      override fun onDismissed(transientBottomBar: Snackbar, event: Int) {
        super.onDismissed(transientBottomBar, event)

        val view = transientBottomBar.view

        snackbar?.removeCallback(this)
        findFab(view)?.show()
      }
    })

    snackbar?.show()
  }

  private fun findFab(view: View): HidingFloatingActionButton? {
    var parent = view.parent

    while (parent != null && parent !is ThreadLayout) {
      parent = parent.parent
    }

    if (parent !is ThreadLayout) {
      Logger.e("SnackbarWrapper", "Couldn't find ThreadLayout!!!")
      return null
    }

    val threadLayout = parent
    val fab = threadLayout.findChild { view -> view is HidingFloatingActionButton } as? HidingFloatingActionButton

    if (fab == null) {
      Logger.e("SnackbarWrapper", "Couldn't find HidingFloatingActionButton!!!")
      return null
    }

    return fab
  }

  private fun updateSnackbarBottomPaddingSuperHacky(snackbarView: View) {
    var parent = snackbarView.parent

    while (parent != null && parent !is DrawerWidthAdjustingLayout) {
      parent = parent.parent
    }

    if (parent !is DrawerWidthAdjustingLayout) {
      Logger.e("SnackbarWrapper", "Couldn't find DrawerWidthAdjustingLayout!!!")
      return
    }

    val drawer = parent
    val bottomNavView = drawer.findChild { view -> view is HidingBottomNavigationView }

    if (bottomNavView == null) {
      Logger.e("SnackbarWrapper", "Couldn't find HidingBottomNavigationView!!!")
      return
    }

    if (snackbarView.y + snackbarView.height > bottomNavView.y) {
      val newTranslationY = (snackbarView.y + snackbarView.height) - bottomNavView.y
      snackbarView.translationY = -(newTranslationY + MARGIN)
    }
  }

  companion object {
    private val MARGIN = dp(8f)

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
      snackbar.animationMode = Snackbar.ANIMATION_MODE_FADE

      fixSnackbarColors(view.context, snackbar)
      return SnackbarWrapper(snackbar)
    }

    @JvmStatic
    fun create(view: View, text: String, duration: Int): SnackbarWrapper {
      require(duration in allowedDurations) { "Bad duration" }

      val snackbar = Snackbar.make(view, text, duration)
      snackbar.isGestureInsetBottomIgnored = false
      snackbar.animationMode = Snackbar.ANIMATION_MODE_FADE

      fixSnackbarColors(view.context, snackbar)
      return SnackbarWrapper(snackbar)
    }

    private fun fixSnackbarColors(context: Context, snackbar: Snackbar) {
      snackbar.setTextColor(AndroidUtils.getAttrColor(context, R.attr.text_color_primary))
      snackbar.setActionTextColor(AndroidUtils.getAttrColor(context, R.attr.colorAccent))
    }
  }
}