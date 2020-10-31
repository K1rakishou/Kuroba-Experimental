package com.github.k1rakishou.chan.ui.controller.settings.captcha

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.StartActivityComponent

class JsCaptchaCookiesEditorController(context: Context) :
  Controller(context), JsCaptchaCookiesEditorLayout.JsCaptchaCookiesEditorControllerCallbacks {

  override fun injectDependencies(component: StartActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    navigation.setTitle(R.string.js_captcha_cookies_editor_controller_title)
    view = JsCaptchaCookiesEditorLayout(context).apply {
      onReady(this@JsCaptchaCookiesEditorController)
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    (view as? JsCaptchaCookiesEditorLayout)?.destroy()
  }

  override fun onFinished() {
    navigationController!!.popController()
  }

}