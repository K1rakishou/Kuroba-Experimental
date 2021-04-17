package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutCallback
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutInterface
import com.github.k1rakishou.chan.ui.captcha.CaptchaLayout
import com.github.k1rakishou.chan.ui.captcha.GenericWebViewAuthenticationLayout
import com.github.k1rakishou.chan.ui.captcha.LegacyCaptchaLayout
import com.github.k1rakishou.chan.ui.captcha.v1.CaptchaNojsLayoutV1
import com.github.k1rakishou.chan.ui.captcha.v2.CaptchaNoJsLayoutV2
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import javax.inject.Inject

class CaptchaContainerController(
  context: Context,
  private val chanDescriptor: ChanDescriptor,
  private val authenticationCallback: (AuthenticationResult) -> Unit
) : BaseFloatingController(context), AuthenticationLayoutCallback {
  private lateinit var authenticationLayout: AuthenticationLayoutInterface
  private lateinit var captchaContainer: FrameLayout

  @Inject
  lateinit var siteManager: SiteManager

  override fun getLayoutId(): Int = R.layout.layout_reply_captcha

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    captchaContainer = view.findViewById(R.id.captcha_container)
    view.findViewById<FrameLayout>(R.id.outside_area)
      .setOnClickListener { pop() }

    initAuthenticationInternal(useV2NoJsCaptcha = true)
  }

  private fun initAuthenticationInternal(useV2NoJsCaptcha: Boolean) {
    val site = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
    if (site == null) {
      showToast("Failed to find site by site descriptor ${chanDescriptor.siteDescriptor()}")
      pop()
      return
    }

    captchaContainer.removeAllViews()

    val authenticationLayout = createAuthenticationLayout(site.actions().postAuthenticate(), useV2NoJsCaptcha)
    captchaContainer.addView(authenticationLayout as View, 0)

    authenticationLayout.initialize(site, this, false)
    authenticationLayout.reset()
  }

  override fun onDestroy() {
    super.onDestroy()

    if (::authenticationLayout.isInitialized) {
      authenticationLayout.onDestroy()
    }
  }

  override fun onAuthenticationComplete(challenge: String?, response: String?, autoReply: Boolean) {
    authenticationCallback(AuthenticationResult.Success(challenge, response))
    pop()
  }

  override fun onAuthenticationFailed(error: Throwable) {
    authenticationCallback(AuthenticationResult.Failure(error))
    pop()
  }

  override fun onFallbackToV1CaptchaView(autoReply: Boolean) {
    initAuthenticationInternal(useV2NoJsCaptcha = false)
  }

  private fun createAuthenticationLayout(
    authentication: SiteAuthentication,
    useV2NoJsCaptcha: Boolean
  ): AuthenticationLayoutInterface {
    when (authentication.type) {
      SiteAuthentication.Type.CAPTCHA1 -> {
        return AppModuleAndroidUtils.inflate(
          context,
          R.layout.layout_captcha_legacy,
          captchaContainer,
          false
        ) as LegacyCaptchaLayout
      }
      SiteAuthentication.Type.CAPTCHA2 -> {
        return CaptchaLayout(context)
      }
      SiteAuthentication.Type.CAPTCHA2_NOJS -> {
        return if (useV2NoJsCaptcha) {
          // new captcha window without webview
          CaptchaNoJsLayoutV2(context)
        } else {
          // default webview-based captcha view
          CaptchaNojsLayoutV1(context)
        }
      }
      SiteAuthentication.Type.GENERIC_WEBVIEW -> {
        val view = GenericWebViewAuthenticationLayout(context)
        val params = FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT
        )

        view.layoutParams = params
        return view
      }
      SiteAuthentication.Type.NONE -> {
        throw IllegalArgumentException("${authentication.type} is not supposed to be used here")
      }
      else -> throw IllegalArgumentException("Unknown authentication.type=${authentication.type}")
    }
  }

  sealed class AuthenticationResult {
    data class Success(val challenge: String?, val response: String?) : AuthenticationResult()
    data class Failure(val throwable: Throwable) : AuthenticationResult()
  }

}