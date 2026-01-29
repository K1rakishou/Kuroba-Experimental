package com.github.k1rakishou.chan.ui.captcha.lynxchan

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.ui.captcha.chan4.Chan4CaptchaLayout
import com.github.k1rakishou.chan.ui.captcha.lynxchan.LynxchanCaptchaLayoutViewModel.VerifyCaptchaResult
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeTextField
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.joda.time.Period
import org.joda.time.format.PeriodFormatterBuilder
import kotlin.math.min
import kotlin.time.measureTime

@Composable
fun LynxchanCaptchaSection(
  chanDescriptor: ChanDescriptor,
  lynxchanCaptcha: SiteAuthentication.CustomCaptcha.LynxchanCaptcha,
  viewModel: LynxchanCaptchaLayoutViewModel,
  onResetCaptcha: () -> Unit,
  onCaptchaSolved: (String, Long?) -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  val captchaInfoAsync by viewModel.captchaInfoToShow
  val captchaInfo = (captchaInfoAsync as? AsyncData.Data)?.data
  val captchaBlockMut by viewModel.captchaBlock
  val captchaBlock = captchaBlockMut
  val verifyingCaptchaState = remember { mutableStateOf(false) }
  val verifyingCaptcha by verifyingCaptchaState

  var currentInputValue by viewModel.currentInputValue

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

    if (captchaBlock != null && captchaInfo.needBlockBypass) {
      Spacer(modifier = Modifier.height(8.dp))

      ProofOfWorkProgressIndicator(captchaBlock)

      Spacer(modifier = Modifier.height(8.dp))

      ElapsedTime()

      Spacer(modifier = Modifier.height(8.dp))
    }
  }

  BuildCaptchaImageOrText(captchaInfoAsync)
  Spacer(modifier = Modifier.height(8.dp))

  if (captchaInfo != null) {
    val verifyingCaptcha by verifyingCaptchaState

    KurobaComposeTextField(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .padding(horizontal = 16.dp),
      enabled = !verifyingCaptcha,
      value = currentInputValue,
      onValueChange = { newValue -> currentInputValue = newValue },
      keyboardActions = KeyboardActions(
        onDone = {
          verifyCaptcha(
            context = context,
            scope = coroutineScope,
            needBlockBypass = captchaInfo.needBlockBypass,
            verifyingCaptchaState = verifyingCaptchaState,
            captchaInfo = captchaInfo,
            answer = currentInputValue,
            chanDescriptor = chanDescriptor,
            lynxchanCaptcha = lynxchanCaptcha,
            viewModel = viewModel,
            onResetCaptcha = onResetCaptcha,
            onCaptchaSolved = onCaptchaSolved
          )
        }
      ),
      keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Password,
        showKeyboardOnFocus = true,
        capitalization = KeyboardCapitalization.None,
        platformImeOptions = null,
        autoCorrectEnabled = false
      ),
      maxLines = 1,
      singleLine = true
    )

    Spacer(modifier = Modifier.height(8.dp))
  }

  LynxchanCaptchaButtonsSection(
    resetButtonEnabled = !verifyingCaptcha,
    reloadButtonEnabled = !verifyingCaptcha,
    verifyButtonEnabled = captchaInfo != null &&
      currentInputValue.isNotEmpty() &&
      !verifyingCaptcha,
    onResetButtonClicked = {
      viewModel.requestCaptcha(
        lynxchanCaptcha = lynxchanCaptcha,
        chanDescriptor = chanDescriptor,
        resetCaptchaCookies = true
      )
    },
    onReloadButtonClicked = {
      viewModel.requestCaptcha(
        lynxchanCaptcha = lynxchanCaptcha,
        chanDescriptor = chanDescriptor,
        resetCaptchaCookies = false
      )
    },
    onVerifyButtonClicked = {
      verifyCaptcha(
        context = context,
        scope = coroutineScope,
        needBlockBypass = captchaInfo?.needBlockBypass == true,
        verifyingCaptchaState = verifyingCaptchaState,
        captchaInfo = captchaInfo,
        answer = currentInputValue,
        chanDescriptor = chanDescriptor,
        lynxchanCaptcha = lynxchanCaptcha,
        viewModel = viewModel,
        onResetCaptcha = onResetCaptcha,
        onCaptchaSolved = onCaptchaSolved
      )
    }
  )

  Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun ProofOfWorkProgressIndicator(
  captchaBlock: LynxchanCaptchaLayoutViewModel.LynxchanCaptchaBlock
) {
  val chanTheme = LocalChanTheme.current

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .padding(horizontal = 8.dp, vertical = 4.dp)
      .background(color = chanTheme.backColorSecondaryCompose),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    KurobaComposeText(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      text = stringResource(id = R.string.chan8moe_block_bypass_solve_message),
      color = ThemeEngine.resolveTextColor(chanTheme.backColorSecondaryCompose)
    )
  }

  Spacer(modifier = Modifier.height(8.dp))

  KurobaComposeText(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .padding(horizontal = 8.dp, vertical = 4.dp),
    text = "Bruteforce iteration: ${captchaBlock.iteration}",
    textAlign = TextAlign.Center,
    fontSize = 20.ktu
  )
}

@Composable
private fun ElapsedTime() {
  val elapsedTimeMut = remember { mutableStateOf("") }
  val elapsedTime by elapsedTimeMut

  LaunchedEffect(key1 = Unit) {
    val formatter = PeriodFormatterBuilder()
      .printZeroAlways()
      .minimumPrintedDigits(2)
      .appendMinutes()
      .appendLiteral(":")
      .appendSeconds()
      .toFormatter()

    var durationMillis = 0L

    while (isActive) {
      val delta = measureTime { awaitFrame() }
      durationMillis += delta.inWholeMilliseconds
      elapsedTimeMut.value = formatter.print(Period(durationMillis))
    }
  }

  if (elapsedTime.isNotEmpty()) {
    KurobaComposeText(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .padding(horizontal = 8.dp, vertical = 4.dp),
      text = "Elapsed time: ${elapsedTime}",
      textAlign = TextAlign.Center,
      fontSize = 20.ktu
    )
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
          val error = captchaInfoAsync.throwable
          KurobaComposeErrorMessage(
            error = error,
            modifier = Modifier.fillMaxSize()
          )

          null
        }
        is AsyncData.Data -> captchaInfoAsync.data
      }

      if (captchaInfo != null) {
        val imgBitmapPainter = captchaInfo.captchaImage

        val scale = min(
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

private fun verifyCaptcha(
  context: Context,
  scope: CoroutineScope,
  answer: String,
  needBlockBypass: Boolean,
  chanDescriptor: ChanDescriptor,
  verifyingCaptchaState: MutableState<Boolean>,
  captchaInfo: LynxchanCaptchaLayoutViewModel.LynxchanCaptchaFull?,
  lynxchanCaptcha: SiteAuthentication.CustomCaptcha.LynxchanCaptcha,
  viewModel: LynxchanCaptchaLayoutViewModel,
  onResetCaptcha: () -> Unit,
  onCaptchaSolved: (String, Long?) -> Unit
) {
  if (captchaInfo == null) {
    return
  }

  val captchaId = captchaInfo.captchaInfo.captchaId

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

      val captchaVerificationResult = when (result) {
        is ModularResult.Error -> {
          val error = result.error
          if (error is CancellationException) {
            return@launch
          }

          if (error is LynxchanCaptchaLayoutViewModel.LynxchanCaptchaPOWError) {
            showToast(context, result.error.errorMessageOrClassName())
            return@launch
          }

          showToast(
            context = context,
            message = getString(R.string.lynxchan_captcha_verification_error, result.error.errorMessageOrClassName())
          )

          onResetCaptcha()
          return@launch
        }
        is ModularResult.Value<VerifyCaptchaResult> -> result.value
      }

      when (captchaVerificationResult) {
        VerifyCaptchaResult.NotSupported -> {
          // Just don't do anything
          return@launch
        }
        VerifyCaptchaResult.Failure -> {
          showToast(
            context = context,
            message = getString(R.string.lynxchan_captcha_verification_not_successful)
          )

          onResetCaptcha()
          return@launch
        }
        VerifyCaptchaResult.SolvedCaptcha -> {
          if (needBlockBypass) {
            showToast(
              context = context,
              message = getString(R.string.lynxchan_captcha_verification_block_bypassed)
            )

            onResetCaptcha()
            return@launch
          }

          // fallthrough
        }
        VerifyCaptchaResult.SolvedProofOfWork -> {
          // fallthrough
        }
      }

      val expirationTimeMillis = captchaInfo.captchaInfo.kurobaCookie.expirationMillis()
      onCaptchaSolved(captchaId, expirationTimeMillis)
    } finally {
      verifyingCaptchaState.value = false
    }
  }
}