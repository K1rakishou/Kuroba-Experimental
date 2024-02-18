package com.github.k1rakishou.chan.ui.controller.dialog

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.controller.BaseFloatingController
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayout
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isTablet

class KurobaAlertDialogHostController(
  context: Context,
  private val cancelable: Boolean,
  private val onAppearListener: (() -> Unit)?,
  private val onDismissListener: (() -> Unit)?,
  private val onReady: (ViewGroup, KurobaAlertDialogHostControllerCallbacks) -> Unit
) : BaseFloatingController(context), KurobaAlertDialogHostControllerCallbacks {

  override fun injectDependencies(component: ActivityComponent) {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)
  }

  override fun getLayoutId(): Int = R.layout.controller_kuroba_alert_dialog_host

  override fun onCreate() {
    super.onCreate()

    onAppearListener?.invoke()

    view.findViewById<TouchBlockingFrameLayout>(R.id.dialog_container).let { dialogContainer ->
      onReady(dialogContainer, this)
    }

    view.findViewById<FrameLayout>(R.id.inner_container).let { innerContainer ->
      innerContainer.updateLayoutParams<ConstraintLayout.LayoutParams> {
        matchConstraintMaxWidth = if (isTablet()) {
          TABLET_WIDTH
        } else {
          NORMAL_WIDTH
        }
      }
    }

    view.findViewById<ConstraintLayout>(R.id.outside_area).let { outsideArea ->
      outsideArea.setOnClickListener {
        if (!cancelable) {
          return@setOnClickListener
        }

        pop()
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    onDismissListener?.invoke()
  }

  override fun onBack(): Boolean {
    if (!cancelable) {
      return true
    }

    return super.onBack()
  }

  override fun onDismiss() {
    pop()
  }

  companion object {
    private val TABLET_WIDTH = dp(500f)
    private val NORMAL_WIDTH = dp(360f)
  }
}