package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.common.AndroidUtils
import javax.inject.Inject

abstract class BaseFloatingController(
  context: Context
) : Controller(context), WindowInsetsListener {
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private var presenting = true

  override fun onCreate() {
    super.onCreate()

    view = AndroidUtils.inflate(context, getLayoutId())
    updatePaddings()

    globalWindowInsetsManager.addInsetsUpdatesListener(this)
  }

  override fun onInsetsChanged() {
    updatePaddings()
  }

  override fun onDestroy() {
    super.onDestroy()
    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
  }

  private fun updatePaddings() {
    AndroidUtils.updatePaddings(
      view,
      HPADDING + globalWindowInsetsManager.left(),
      HPADDING + globalWindowInsetsManager.right(),
      VPADDING + globalWindowInsetsManager.top(),
      VPADDING + globalWindowInsetsManager.bottom()
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
    private val HPADDING = AndroidUtils.dp(8f)
    private val VPADDING = AndroidUtils.dp(16f)
  }
}