package com.github.k1rakishou.chan.ui.controller.settings.captcha

import android.content.Context
import com.github.k1rakishou.chan.Chan.Companion.inject
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller

class JsCaptchaCookiesEditorController(context: Context) :
  Controller(context), JsCaptchaCookiesEditorLayout.JsCaptchaCookiesEditorControllerCallbacks {

  override fun onCreate() {
    super.onCreate()
    inject(this)

    navigation.setTitle(R.string.js_captcha_cookies_editor_controller_title)
    view = JsCaptchaCookiesEditorLayout(context).apply {
      onReady(this@JsCaptchaCookiesEditorController)
    }
  }

  override fun onFinished() {
    navigationController!!.popController()
  }

}