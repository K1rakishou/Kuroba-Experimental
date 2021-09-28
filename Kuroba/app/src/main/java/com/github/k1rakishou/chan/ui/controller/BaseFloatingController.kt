package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isTablet
import com.github.k1rakishou.common.updatePaddings
import javax.inject.Inject

abstract class BaseFloatingController(
  context: Context
) : Controller(context), WindowInsetsListener {
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private var presenting = true

  override fun onCreate() {
    super.onCreate()
    presenting = true

    view = inflate(context, getLayoutId())
    updatePaddings()

    globalWindowInsetsManager.addInsetsUpdatesListener(this)
  }

  override fun onInsetsChanged() {
    updatePaddings()
  }

  override fun onDestroy() {
    super.onDestroy()

    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
    presenting = false
  }

  private fun updatePaddings() {
    val horizPadding = if (isTablet()) {
      HPADDING_TABLET
    } else {
      HPADDING
    }

    val vertPadding = if (isTablet()) {
      VPADDING_TABLET
    } else {
      VPADDING
    }

    view.updatePaddings(
      left = horizPadding + globalWindowInsetsManager.left(),
      right = horizPadding + globalWindowInsetsManager.right(),
      top = vertPadding + globalWindowInsetsManager.top(),
      bottom = vertPadding + globalWindowInsetsManager.bottom()
    )
  }

  override fun onBack(): Boolean {
    if (presenting) {
      if (pop()) {
        return true
      }
    }

    return super.onBack()
  }

  protected open fun pop(): Boolean {
    if (!presenting) {
      return false
    }

    presenting = false
    stopPresenting()

    return true
  }

  protected abstract fun getLayoutId(): Int

  companion object {
    val HPADDING = dp(12f)
    val VPADDING = dp(16f)

    val HPADDING_TABLET = dp(24f)
    val VPADDING_TABLET = dp(32f)
  }
}