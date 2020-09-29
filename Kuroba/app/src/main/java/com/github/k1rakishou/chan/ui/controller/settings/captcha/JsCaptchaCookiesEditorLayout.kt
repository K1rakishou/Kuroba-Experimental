package com.github.k1rakishou.chan.ui.controller.settings.captcha

import android.content.Context
import android.widget.FrameLayout
import android.widget.TextView
import com.github.k1rakishou.chan.Chan.Companion.inject
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.settings.ChanSettings
import com.github.k1rakishou.chan.core.settings.ChanSettings.EMPTY_JSON
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEditText
import com.github.k1rakishou.chan.utils.AndroidUtils.getString
import com.github.k1rakishou.chan.utils.AndroidUtils.showToast
import com.google.gson.Gson
import javax.inject.Inject

class JsCaptchaCookiesEditorLayout(context: Context) : FrameLayout(context) {

  @Inject
  lateinit var gson: Gson
  @Inject
  lateinit var themeEngine: ThemeEngine

  private var callbacks: JsCaptchaCookiesEditorControllerCallbacks? = null

  private val cookiesEditorWarning: TextView
  private val cookiesEditorTitle: TextView
  private val hsidCookieEditText: ColorizableEditText
  private val ssidCookieEditText: ColorizableEditText
  private val sidCookieEditText: ColorizableEditText
  private val nidCookieEditText: ColorizableEditText
  private val saveAndApplyButton: ColorizableBarButton
  private val resetButton: ColorizableBarButton

  init {
    inject(this)

    inflate(context, R.layout.js_captcha_cookies_editor, this).apply {
      cookiesEditorWarning = findViewById(R.id.js_captcha_cookies_editor_warning)
      cookiesEditorTitle = findViewById(R.id.js_captcha_cookies_editor_title)
      hsidCookieEditText = findViewById(R.id.js_captcha_cookies_editor_hsid_cookie)
      ssidCookieEditText = findViewById(R.id.js_captcha_cookies_editor_ssid_cookie)
      sidCookieEditText = findViewById(R.id.js_captcha_cookies_editor_sid_cookie)
      nidCookieEditText = findViewById(R.id.js_captcha_cookies_editor_nid_cookie)
      saveAndApplyButton = findViewById(R.id.js_captcha_cookies_editor_save_and_apply)
      resetButton = findViewById(R.id.js_captcha_cookies_editor_reset)

      cookiesEditorWarning.setTextColor(themeEngine.chanTheme.textColorPrimary)
      cookiesEditorTitle.setTextColor(themeEngine.chanTheme.textColorPrimary)

      var prevCookiesJar = JsCaptchaCookiesJar.empty()
      try {
        prevCookiesJar = gson.fromJson<JsCaptchaCookiesJar>(
          ChanSettings.jsCaptchaCookies.get(),
          JsCaptchaCookiesJar::class.java
        )
      } catch (ignored: Exception) {
        showToast(context, R.string.cookies_editor_failed_parse);
      }

      if (prevCookiesJar.hsidCookie.isNotEmpty()) {
        hsidCookieEditText.setText(prevCookiesJar.hsidCookie)
      }
      if (prevCookiesJar.ssidCookie.isNotEmpty()) {
        ssidCookieEditText.setText(prevCookiesJar.ssidCookie)
      }
      if (prevCookiesJar.sidCookie.isNotEmpty()) {
        sidCookieEditText.setText(prevCookiesJar.sidCookie)
      }
      if (prevCookiesJar.nidCookie.isNotEmpty()) {
        nidCookieEditText.setText(prevCookiesJar.nidCookie)
      }

      saveAndApplyButton.setOnClickListener {
        onSaveAndApplyClicked()
      }
      resetButton.setOnClickListener {
        onResetClicked()
      }
    }
  }

  fun onReady(callbacks: JsCaptchaCookiesEditorControllerCallbacks) {
    this.callbacks = callbacks
  }

  fun destroy() {
    this.callbacks = null
  }

  private fun onResetClicked() {
    hsidCookieEditText.setText("")
    ssidCookieEditText.setText("")
    sidCookieEditText.setText("")
    nidCookieEditText.setText("")

    ChanSettings.jsCaptchaCookies.set(EMPTY_JSON)
    callbacks?.onFinished()
  }

  private fun onSaveAndApplyClicked() {
    val hsidCookie = hsidCookieEditText.text?.toString() ?: ""
    val ssidCookie = ssidCookieEditText.text?.toString() ?: ""
    val sidCookie = sidCookieEditText.text?.toString() ?: ""
    val nidCookie = nidCookieEditText.text?.toString() ?: ""

    hsidCookieEditText.error = null
    ssidCookieEditText.error = null
    sidCookieEditText.error = null
    nidCookieEditText.error = null

    var hasError = false

    if (hsidCookie.isEmpty()) {
      hsidCookieEditText.error = getString(R.string.cookies_editor_bad_cookie, hsidCookieEditText.hint)
      hasError = true
    }

    if (ssidCookie.isEmpty()) {
      ssidCookieEditText.error = getString(R.string.cookies_editor_bad_cookie, ssidCookieEditText.hint)
      hasError = true
    }

    if (sidCookie.isEmpty()) {
      sidCookieEditText.error = getString(R.string.cookies_editor_bad_cookie, sidCookieEditText.hint)
      hasError = true
    }

    if (nidCookie.isEmpty()) {
      nidCookieEditText.error = getString(R.string.cookies_editor_bad_cookie, nidCookieEditText.hint)
      hasError = true
    }

    if (hasError) {
      return
    }

    val cookiesJar = JsCaptchaCookiesJar(
      hsidCookie = hsidCookie,
      ssidCookie = ssidCookie,
      sidCookie = sidCookie,
      nidCookie = nidCookie
    )

    val json = gson.toJson(cookiesJar)
    ChanSettings.jsCaptchaCookies.set(json)

    callbacks?.onFinished()
  }

  interface JsCaptchaCookiesEditorControllerCallbacks {
    fun onFinished()
  }
}