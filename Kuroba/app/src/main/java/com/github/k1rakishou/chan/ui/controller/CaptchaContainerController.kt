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
import com.github.k1rakishou.chan.core.site.sites.dvach.Dvach
import com.github.k1rakishou.chan.features.bypass.FirewallType
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutCallback
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutInterface
import com.github.k1rakishou.chan.ui.captcha.CaptchaLayout
import com.github.k1rakishou.chan.ui.captcha.GenericWebViewAuthenticationLayout
import com.github.k1rakishou.chan.ui.captcha.chan4.Chan4CaptchaLayout
import com.github.k1rakishou.chan.ui.captcha.dvach.DvachCaptchaLayout
import com.github.k1rakishou.chan.ui.captcha.lynxchan.LynxchanCaptchaLayout
import com.github.k1rakishou.chan.ui.captcha.v1.CaptchaNojsLayoutV1
import com.github.k1rakishou.chan.ui.captcha.v2.CaptchaNoJsLayoutV2
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import javax.inject.Inject

class CaptchaContainerController(
  context: Context,
  private val afterPostingAttempt: Boolean,
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

    try {
      initAuthenticationInternal(useV2NoJsCaptcha = true)
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

  private fun initAuthenticationInternal(useV2NoJsCaptcha: Boolean) {
    val site = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
    if (site == null) {
      showToast("Failed to find site by site descriptor ${chanDescriptor.siteDescriptor()}")
      pop()
      return
    }

    captchaContainer.removeAllViews()

    var postAuthentication = site.actions().postAuthenticate()

    if (afterPostingAttempt && postAuthentication.type == SiteAuthentication.Type.CAPTCHA2_INVISIBLE) {
      if (site is Dvach) {
        postAuthentication = site.captchaV2NoJs
      } else {
        showToast("Cannot override default invisible captcha for site '${chanDescriptor.siteDescriptor()}'.\n" +
          "(most likely it was forgotten to be handled)")

        pop()
        return
      }
    }

    val authenticationLayout = createAuthenticationLayout(
      authentication = postAuthentication,
      useV2NoJsCaptcha = useV2NoJsCaptcha
    )

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

  override fun onAuthenticationFailed(error: Throwable) {
    authenticationCallback(AuthenticationResult.Failure(error))
    pop()
  }

  override fun onSiteRequiresAdditionalAuth(firewallType: FirewallType, siteDescriptor: SiteDescriptor) {
    authenticationCallback(AuthenticationResult.SiteRequiresAdditionalAuth(firewallType, siteDescriptor))
    pop()
  }

  override fun onFallbackToV1CaptchaView() {
    initAuthenticationInternal(useV2NoJsCaptcha = false)
  }

  @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
  private fun createAuthenticationLayout(
    authentication: SiteAuthentication,
    useV2NoJsCaptcha: Boolean
  ): AuthenticationLayoutInterface {
    when (authentication.type) {
      SiteAuthentication.Type.NONE -> {
        throw IllegalArgumentException("${authentication.type} is not supposed to be used here")
      }
      SiteAuthentication.Type.CAPTCHA2_INVISIBLE,
      SiteAuthentication.Type.CAPTCHA2 -> {
        val view = CaptchaLayout(context)
        val params = FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          WEBVIEW_BASE_CAPTCHA_VIEW_HEIGHT
        )

        view.layoutParams = params
        return view
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
          WEBVIEW_BASE_CAPTCHA_VIEW_HEIGHT
        )

        view.layoutParams = params
        return view
      }
      SiteAuthentication.Type.ID_BASED_CAPTCHA -> {
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

    data class SiteRequiresAdditionalAuth(
      val firewallType: FirewallType,
      val siteDescriptor: SiteDescriptor
    ) : AuthenticationResult()
  }

  companion object {
    private const val TAG = "CaptchaContainerController"
    private val WEBVIEW_BASE_CAPTCHA_VIEW_HEIGHT = dp(600f)
  }
}