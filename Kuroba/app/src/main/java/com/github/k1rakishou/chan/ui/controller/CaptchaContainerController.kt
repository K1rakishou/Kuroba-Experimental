package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import android.content.res.Resources
import android.util.AndroidRuntimeException
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutCallback
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutInterface
import com.github.k1rakishou.chan.ui.captcha.chan4.Chan4CaptchaLayout
import com.github.k1rakishou.chan.ui.captcha.dvach.DvachCaptchaLayout
import com.github.k1rakishou.chan.ui.captcha.lynxchan.LynxchanCaptchaLayout
import com.github.k1rakishou.chan.ui.controller.base.BaseFloatingController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_logger.Logger
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

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    captchaContainer = view.findViewById(R.id.captcha_container)
    view.findViewById<FrameLayout>(R.id.outside_area)
      .setOnClickListener { pop() }

    try {
      initAuthenticationInternal()
    } catch (error: Throwable) {
      Logger.e(TAG, "initAuthenticationInternal error", error)
      showToast(getReason(error))

      pop()
    }
  }

  private fun getReason(error: Throwable): String {
    if (error is AndroidRuntimeException && error.message != null) {
      if (error.message?.contains("MissingWebViewPackageException") == true) {
        return AppModuleAndroidUtils.getString(R.string.fail_reason_webview_is_not_installed)
      }

      // Fallthrough
    } else if (error is Resources.NotFoundException) {
      return AppModuleAndroidUtils.getString(
        R.string.fail_reason_some_part_of_webview_not_initialized,
        error.message
      )
    }

    if (error.message != null) {
      return String.format("%s: %s", error.javaClass.simpleName, error.message)
    }

    return error.javaClass.simpleName
  }

  private fun initAuthenticationInternal() {
    val site = siteManager.bySiteDescriptorAndActive(chanDescriptor.siteDescriptor())
    if (site == null) {
      showToast("Failed to find site by site descriptor ${chanDescriptor.siteDescriptor()}")
      pop()
      return
    }

    captchaContainer.removeAllViews()

    val postAuthentication = site.actions.postAuthenticate()
    authenticationLayout = createAuthenticationLayout(postAuthentication)

    captchaContainer.addView(authenticationLayout as View, 0)
    authenticationLayout.initialize(chanDescriptor.siteDescriptor(), postAuthentication, this)
    authenticationLayout.reset()
  }

  override fun onDestroy() {
    super.onDestroy()

    if (::authenticationLayout.isInitialized) {
      authenticationLayout.onDestroy()
    }
  }

  override fun onAuthenticationComplete() {
    authenticationCallback(AuthenticationResult.Success)
    pop()
  }

  @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
  private fun createAuthenticationLayout(
    authentication: SiteAuthentication,
  ): AuthenticationLayoutInterface {
    when (authentication.type) {
      SiteAuthentication.Type.NONE -> {
        throw IllegalArgumentException("${authentication.type} is not supposed to be used here")
      }
      SiteAuthentication.Type.ID_BASED_CAPTCHA,
      SiteAuthentication.Type.EMOJI_CAPTCHA -> {
        val view = DvachCaptchaLayout(context)
        val params = FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        )

        view.layoutParams = params
        return view
      }
      SiteAuthentication.Type.ENDPOINT_BASED_CAPTCHA -> {
        val view = Chan4CaptchaLayout(
          context = context,
          chanDescriptor = chanDescriptor,
          presentControllerFunc = { controller -> presentController(controller) }
        )

        val params = FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        )

        view.layoutParams = params
        return view
      }
      SiteAuthentication.Type.CUSTOM_CAPTCHA -> {
        val customCaptcha = checkNotNull(authentication.customCaptcha) { "Custom captcha is null!" }
        return createViewForCustomCaptcha(customCaptcha)
      }
    }
  }

  private fun createViewForCustomCaptcha(
    customCaptcha: SiteAuthentication.CustomCaptcha
  ): AuthenticationLayoutInterface {
    when (customCaptcha) {
      is SiteAuthentication.CustomCaptcha.LynxchanCaptcha -> {
        val view = LynxchanCaptchaLayout(
          context = context,
          chanDescriptor = chanDescriptor
        )

        val params = FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        )

        view.layoutParams = params

        return view
      }
    }
  }

  sealed class AuthenticationResult {
    object Success : AuthenticationResult()
    data class Failure(val throwable: Throwable) : AuthenticationResult()
  }

  companion object {
    private const val TAG = "CaptchaContainerController"
  }
}