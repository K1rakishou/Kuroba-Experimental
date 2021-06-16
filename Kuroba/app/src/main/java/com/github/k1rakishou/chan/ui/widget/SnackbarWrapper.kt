package com.github.k1rakishou.chan.ui.widget

import android.graphics.Color
import android.view.View
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.ui.layout.DrawerWidthAdjustingLayout
import com.github.k1rakishou.chan.ui.layout.ThreadLayout
import com.github.k1rakishou.chan.ui.view.HidingFloatingActionButton
import com.github.k1rakishou.chan.ui.view.KurobaBottomNavigationView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.common.findChild
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.Companion.isDarkColor
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import javax.inject.Inject

class SnackbarWrapper private constructor(
  private val globalWindowInsetsManager: GlobalWindowInsetsManager,
  private var snackbar: Snackbar? = null
) {

  @Inject
  lateinit var themeEngine: ThemeEngine

  init {
    Chan.getComponent().inject(this)

    snackbar?.view?.setBackgroundColor(themeEngine.chanTheme.primaryColor)
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

        findFab(view)?.hide()
      }

      override fun onDismissed(transientBottomBar: Snackbar, event: Int) {
        super.onDismissed(transientBottomBar, event)

        val view = transientBottomBar.view
        snackbar?.removeCallback(this)

        val bottomNavigationView = findBottomNavigationView(view)
          ?: return

        if (bottomNavigationView.isFullyVisible()) {
          val fab = findFab(view)
            ?: return

          // Delayed, because we want the snackbar to be hidden and it's not yet detached from the
          // parent at this point
          view.post {
            fab.show()
          }
        }
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
    val bottomNavView = findBottomNavigationView(snackbarView)
    if (bottomNavView == null) {
      snackbarView.translationY = 0f
      return
    }

    if (snackbarView.y + snackbarView.height > bottomNavView.y) {
      val newTranslationY = (snackbarView.y + snackbarView.height) - bottomNavView.y
      snackbarView.translationY = -(newTranslationY + MARGIN)
    } else {
      snackbarView.translationY = -(globalWindowInsetsManager.bottom()).toFloat()
    }
  }

  private fun findBottomNavigationView(snackbarView: View): KurobaBottomNavigationView? {
    var parent = snackbarView.parent

    while (parent != null && parent !is DrawerWidthAdjustingLayout) {
      parent = parent.parent
    }

    if (parent !is DrawerWidthAdjustingLayout) {
      Logger.e("SnackbarWrapper", "Couldn't find DrawerWidthAdjustingLayout!!!")
      return null
    }

    val drawer = parent
    val bottomNavView = drawer.findChild { view ->
      view is KurobaBottomNavigationView
    } as? KurobaBottomNavigationView

    if (bottomNavView == null) {
      Logger.e("SnackbarWrapper", "Couldn't find HidingBottomNavigationView!!!")
      return null
    }

    return bottomNavView
  }

  companion object {
    private val MARGIN = dp(8f)

    private val allowedDurations = setOf(
      Snackbar.LENGTH_INDEFINITE,
      Snackbar.LENGTH_SHORT,
      Snackbar.LENGTH_LONG
    )

    @JvmStatic
    fun create(
      globalWindowInsetsManager: GlobalWindowInsetsManager,
      theme: ChanTheme,
      view: View,
      textId: Int,
      duration: Int
    ): SnackbarWrapper {
      require(duration in allowedDurations) { "Bad duration" }

      val snackbar = Snackbar.make(view, textId, duration)
      snackbar.isGestureInsetBottomIgnored = false
      snackbar.animationMode = Snackbar.ANIMATION_MODE_FADE

      fixSnackbarColors(theme, snackbar)
      return SnackbarWrapper(globalWindowInsetsManager, snackbar)
    }

    @JvmStatic
    fun create(
      globalWindowInsetsManager: GlobalWindowInsetsManager,
      theme: ChanTheme,
      view: View,
      text: String,
      duration: Int
    ): SnackbarWrapper {
      require(duration in allowedDurations) { "Bad duration" }

      val snackbar = Snackbar.make(view, text, duration)
      snackbar.isGestureInsetBottomIgnored = false
      snackbar.animationMode = Snackbar.ANIMATION_MODE_FADE

      fixSnackbarColors(theme, snackbar)
      return SnackbarWrapper(globalWindowInsetsManager, snackbar)
    }

    private fun fixSnackbarColors(theme: ChanTheme, snackbar: Snackbar) {
      val isDarkColor = isDarkColor(theme.primaryColor)
      if (isDarkColor) {
        snackbar.setTextColor(Color.WHITE)
        snackbar.setActionTextColor(Color.WHITE)
      } else {
        snackbar.setTextColor(Color.BLACK)
        snackbar.setActionTextColor(Color.BLACK)
      }
    }
  }
}