package com.github.k1rakishou.chan.ui.captcha.lynxchan

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.core.concurrency.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.image.ImageLoaderDeprecated
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutCallback
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutInterface
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder
import com.github.k1rakishou.chan.ui.captcha.CaptchaSolution
import com.github.k1rakishou.chan.ui.compose.providers.ComposeEntrypoint
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayout
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showErrorToast
import com.github.k1rakishou.chan.utils.IHasViewModelScope
import com.github.k1rakishou.chan.utils.ViewModelScope
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.common.KurobaCookie
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.requireComponentActivity
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import javax.inject.Inject

@SuppressLint("ViewConstructor")
class LynxchanCaptchaLayout(
  context: Context,
  private val chanDescriptor: ChanDescriptor,
) :
  TouchBlockingFrameLayout(context),
  AuthenticationLayoutInterface,
  IHasViewModelScope {

  @Inject
  lateinit var captchaHolder: CaptchaHolder
  @Inject
  lateinit var imageLoaderDeprecated: ImageLoaderDeprecated
  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private val viewModel by viewModelByKey<LynxchanCaptchaLayoutViewModel>()
  private val scope = KurobaCoroutineScope()

  private lateinit var siteDescriptor: SiteDescriptor
  private lateinit var lynxchanCaptcha: SiteAuthentication.CustomCaptcha.LynxchanCaptcha
  private lateinit var callback: AuthenticationLayoutCallback

  override val viewModelScope: ViewModelScope
    get() = ViewModelScope.ActivityScope(context.requireComponentActivity())

  init {
    AppModuleAndroidUtils.extractActivityComponent(getContext())
      .inject(this)
  }

  override fun initialize(
    siteDescriptor: SiteDescriptor,
    authentication: SiteAuthentication,
    callback: AuthenticationLayoutCallback
  ) {
    this.siteDescriptor = siteDescriptor
    this.lynxchanCaptcha = authentication.customCaptcha as SiteAuthentication.CustomCaptcha.LynxchanCaptcha
    this.callback = callback

    val view = ComposeView(context).apply {
      setContent {
        ComposeEntrypoint {
          val chanTheme = LocalChanTheme.current

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .wrapContentHeight()
              .background(chanTheme.backColorCompose)
          ) {
            BuildContent()
          }
        }
      }
    }

    view.layoutParams = FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.MATCH_PARENT,
      FrameLayout.LayoutParams.WRAP_CONTENT
    )

    addView(view)
  }

  override fun reset() {
    hardReset()
  }

  override fun hardReset() {
    viewModel.requestCaptcha(
      lynxchanCaptcha = lynxchanCaptcha,
      chanDescriptor = chanDescriptor,
      resetCaptchaCookies = false
    )
  }

  override fun onDestroy() {
    scope.cancelChildren()
    viewModel.cleanup()
  }

  @Composable
  private fun BuildContent() {
    Column(modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .verticalScroll(rememberScrollState())
    ) {
      BuildCaptchaWindow()
    }
  }

  @Composable
  private fun BuildCaptchaWindow() {
    val needHashCashSolution by viewModel.needHashCashSolution
    if (needHashCashSolution) {
      LynxchanHashCashSection(
        chanDescriptor = chanDescriptor,
        lynxchanCaptcha = lynxchanCaptcha,
        viewModel = viewModel,
        onCookiesFromUrlApplied = { result ->
          when (result) {
            is ModularResult.Error<*> -> {
              showErrorToast("Failed to apply cookies from url, error: ${result.error.errorMessageOrClassName()}")
              reset()
              return@LynxchanHashCashSection
            }
            is ModularResult.Value<KurobaCookie?> -> {
              val captchaIdWithExpiration = result.value
              if (captchaIdWithExpiration == null) {
                reset()
                return@LynxchanHashCashSection
              }

              val captchaId = captchaIdWithExpiration.value
              val tokenLifetimeMillis = captchaIdWithExpiration.expirationMillis()

              proceedToPosting(
                captchaId = captchaId,
                expirationTimeMillis = tokenLifetimeMillis
              )
            }
          }
        }
      )
    } else {
      LynxchanCaptchaSection(
        chanDescriptor = chanDescriptor,
        lynxchanCaptcha = lynxchanCaptcha,
        viewModel = viewModel,
        onResetCaptcha = { reset() },
        onCaptchaSolved = { captchaId, expirationTimeMillis ->
          proceedToPosting(
            captchaId = captchaId,
            expirationTimeMillis = expirationTimeMillis
          )
        }
      )
    }

    Spacer(modifier = Modifier.height(8.dp))
  }

  private fun proceedToPosting(captchaId: String, expirationTimeMillis: Long?) {
    captchaHolder.addNewSolution(
      solution = CaptchaSolution.SimpleTokenSolution(token = captchaId),
      tokenLifetimeMillis = expirationTimeMillis
        ?: CaptchaHolder.DEFAULT_TOKEN_LIVE_TIME
    )

    viewModel.resetCaptchaForced()
    callback.onAuthenticationComplete()
  }

}