package com.github.k1rakishou.chan.ui.captcha.lynxchan

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutCallback
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutInterface
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder
import com.github.k1rakishou.chan.ui.captcha.CaptchaSolution
import com.github.k1rakishou.chan.ui.captcha.chan4.Chan4CaptchaLayout
import com.github.k1rakishou.chan.ui.compose.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.KurobaComposeTextField
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.ProvideChanTheme
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayout
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@SuppressLint("ViewConstructor")
class LynxchanCaptchaLayout(
  context: Context,
  private val chanDescriptor: ChanDescriptor,
) : TouchBlockingFrameLayout(context), AuthenticationLayoutInterface {

  @Inject
  lateinit var captchaHolder: CaptchaHolder
  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2
  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private val viewModel by lazy { (context as ComponentActivity).viewModelByKey<LynxchanCaptchaLayoutViewModel>() }
  private val scope = KurobaCoroutineScope()

  private var siteDescriptor: SiteDescriptor? = null
  private var lynxchanCaptcha: SiteAuthentication.CustomCaptcha.LynxchanCaptcha? = null
  private var callback: AuthenticationLayoutCallback? = null

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
    this.lynxchanCaptcha = authentication.customCaptcha as? SiteAuthentication.CustomCaptcha.LynxchanCaptcha
    this.callback = callback

    val view = ComposeView(context).apply {
      setContent {
        ProvideChanTheme(themeEngine, globalWindowInsetsManager) {
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
    viewModel.requestCaptcha(lynxchanCaptcha, chanDescriptor)
  }

  override fun onDestroy() {
    this.lynxchanCaptcha = null
    this.callback = null

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
  private fun ColumnScope.BuildCaptchaWindow() {
    val captchaInfoAsync by viewModel.captchaInfoToShow
    val verifyingCaptchaState = remember { mutableStateOf(false) }
    val captchaInfo = (captchaInfoAsync as? AsyncData.Data)?.data
    val needProofOfWork = captchaInfo?.needProofOfWork?.value == true

    if (needProofOfWork) {
      BuildProofOfWorkRequired()
      return
    }

    if (captchaInfo != null) {
      val captchaTypeText = if (captchaInfo.needBlockBypass) {
        stringResource(id = R.string.lynxchan_tor_proxy_vpv_detected_message)
      } else {
        stringResource(id = R.string.lynxchan_regular_captcha_message)
      }

      KurobaComposeText(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .padding(horizontal = 8.dp, vertical = 4.dp),
        text = captchaTypeText,
        textAlign = TextAlign.Center
      )
    }

    BuildCaptchaImageOrText(captchaInfoAsync)
    Spacer(modifier = Modifier.height(8.dp))

    if (captchaInfo != null) {
      var currentInputValue by captchaInfo.currentInputValue
      val verifyingCaptcha by verifyingCaptchaState

      KurobaComposeTextField(
        enabled = !verifyingCaptcha,
        value = currentInputValue,
        onValueChange = { newValue -> currentInputValue = newValue },
        keyboardActions = KeyboardActions(
          onDone = {
            verifyCaptcha(
              needBlockBypass = captchaInfo.needBlockBypass,
              verifyingCaptchaState = verifyingCaptchaState,
              captchaInfo = captchaInfo,
              answer = currentInputValue
            )
          }
        ),
        keyboardOptions = KeyboardOptions(
          autoCorrect = false,
          keyboardType = KeyboardType.Password
        ),
        maxLines = 1,
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .padding(horizontal = 16.dp)
      )

      Spacer(modifier = Modifier.height(8.dp))
    }

    Row(
      horizontalArrangement = Arrangement.End,
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
    ) {
      val verifyingCaptcha by verifyingCaptchaState

      Spacer(modifier = Modifier.weight(1f))

      KurobaComposeTextBarButton(
        enabled = !verifyingCaptcha,
        onClick = { viewModel.requestCaptcha(lynxchanCaptcha, chanDescriptor) },
        text = stringResource(id = R.string.captcha_layout_reload)
      )

      Spacer(modifier = Modifier.width(8.dp))

      KurobaComposeTextBarButton(
        onClick = {
          val currentInputValue = captchaInfo?.currentInputValue
            ?: return@KurobaComposeTextBarButton

          verifyCaptcha(
            needBlockBypass = captchaInfo.needBlockBypass,
            verifyingCaptchaState = verifyingCaptchaState,
            captchaInfo = captchaInfo,
            answer = currentInputValue.value
          )
        },
        enabled = captchaInfo != null && captchaInfo.currentInputValue.value.isNotEmpty() && !verifyingCaptcha,
        text = stringResource(id = R.string.captcha_layout_verify)
      )

      Spacer(modifier = Modifier.width(8.dp))
    }

    Spacer(modifier = Modifier.height(8.dp))
  }

  @Composable
  private fun ColumnScope.BuildProofOfWorkRequired() {
    KurobaComposeText(
      modifier = Modifier
        .wrapContentHeight()
        .fillMaxWidth()
        .padding(4.dp),
      text = stringResource(id = R.string.lynxchan_proof_of_work_message)
    )

    Row(
      horizontalArrangement = Arrangement.End,
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
    ) {
      Spacer(modifier = Modifier.weight(1f))

      KurobaComposeTextBarButton(
        onClick = { callback?.onAuthenticationFailed(LynxchanCaptchaLayoutViewModel.LynxchanCaptchaPOWError()) },
        text = stringResource(id = R.string.close)
      )

      Spacer(modifier = Modifier.width(8.dp))
    }
  }

  @Composable
  private fun BuildCaptchaImageOrText(
    captchaInfoAsync: AsyncData<LynxchanCaptchaLayoutViewModel.LynxchanCaptchaFull>
  ) {
    var size by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = Modifier
      .height(160.dp)
      .fillMaxWidth()
      .onSizeChanged { newSize -> size = newSize }
    ) {
      if (size != IntSize.Zero) {
        val captchaInfo = when (captchaInfoAsync) {
          AsyncData.NotInitialized,
          AsyncData.Loading -> {
            KurobaComposeProgressIndicator()
            null
          }
          is AsyncData.Error -> {
            val error = (captchaInfoAsync as AsyncData.Error).throwable
            KurobaComposeErrorMessage(
              error = error,
              modifier = Modifier.fillMaxSize()
            )

            null
          }
          is AsyncData.Data -> (captchaInfoAsync as AsyncData.Data).data
        }

        if (captchaInfo != null) {
          val imgBitmapPainter = captchaInfo.captchaImage

          val scale = Math.min(
            size.width.toFloat() / imgBitmapPainter.intrinsicSize.width,
            size.height.toFloat() / imgBitmapPainter.intrinsicSize.height
          )

          val contentScale = Chan4CaptchaLayout.Scale(scale)

          Image(
            modifier = Modifier
              .fillMaxSize(),
            painter = imgBitmapPainter,
            contentScale = contentScale,
            contentDescription = null
          )
        }
      }
    }
  }

  @Suppress("IfThenToElvis")
  private fun verifyCaptcha(
    needBlockBypass: Boolean,
    verifyingCaptchaState: MutableState<Boolean>,
    captchaInfo: LynxchanCaptchaLayoutViewModel.LynxchanCaptchaFull?,
    answer: String
  ) {
    val lynxchanCaptcha = this.lynxchanCaptcha
      ?: return

    if (captchaInfo == null) {
      return
    }

    val captchaId = captchaInfo.lynxchanCaptchaJson.captchaId
      ?: return
    scope.launch {
      verifyingCaptchaState.value = true

      try {
        val result = viewModel.verifyCaptcha(
          needBlockBypass = needBlockBypass,
          chanDescriptor = chanDescriptor,
          lynxchanCaptcha = lynxchanCaptcha,
          captchaInfo = captchaInfo,
          answer = answer
        )

        if (result is ModularResult.Error) {
          if (result.error is LynxchanCaptchaLayoutViewModel.LynxchanCaptchaPOWError) {
            showToast(context, result.error.errorMessageOrClassName())
            return@launch
          }

          showToast(context, getString(R.string.lynxchan_captcha_verification_error, result.error.errorMessageOrClassName()))
          reset()
          return@launch
        }

        val success = result.valueOrNull()
        if (success != true) {
          showToast(context, getString(R.string.lynxchan_captcha_verification_not_successful))

          reset()
          return@launch
        }

        if (success && needBlockBypass) {
          showToast(context, getString(R.string.lynxchan_captcha_verification_block_bypassed))

          reset()
          return@launch
        }

        val expirationTimeMillis = captchaInfo.lynxchanCaptchaJson.expirationTimeMillis

        captchaHolder.addNewSolution(
          solution = CaptchaSolution.SimpleTokenSolution(token = captchaId),
          tokenLifetime = expirationTimeMillis ?: CaptchaHolder.RECAPTCHA_TOKEN_LIVE_TIME
        )

        viewModel.resetCaptchaForced()
        callback?.onAuthenticationComplete()
      } finally {
        verifyingCaptchaState.value = false
      }
    }
  }

}