package com.github.k1rakishou.chan.ui.captcha.lynxchan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeTextFieldV2
import com.github.k1rakishou.chan.ui.compose.components.KurobaLabelText
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.forEachTextValue
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.openLink
import com.github.k1rakishou.common.KurobaCookie
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl

private const val TAG = "LynxchanHashCashSection"

@Composable
fun LynxchanHashCashSection(
  chanDescriptor: ChanDescriptor,
  lynxchanCaptcha: SiteAuthentication.CustomCaptcha.LynxchanCaptcha?,
  hashCashInfoToShow: LynxchanCaptchaLayoutViewModel.HashCashInfo,
  viewModel: LynxchanCaptchaLayoutViewModel,
  onCookiesFromUrlApplied: (ModularResult<KurobaCookie?>) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val urlTextFieldState = rememberTextFieldState()
  val coroutineScope = rememberCoroutineScope()
  val currentErrorMut = remember { mutableStateOf<String?>(null) }
  val currentError by currentErrorMut
  var applyingCookiesFromUrl by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    val modifier = if (hashCashInfoToShow is LynxchanCaptchaLayoutViewModel.HashCashInfo.Krautchan) {
      Modifier
        .background(color = chanTheme.backColorSecondaryCompose)
        .kurobaClickable(
          bounded = true,
          onClick = { openLink(hashCashInfoToShow.urlToOpen) }
        )
        .padding(horizontal = 8.dp, vertical = 4.dp)
    } else {
      Modifier
        .padding(horizontal = 8.dp, vertical = 4.dp)
    }

    KurobaComposeText(
      modifier = modifier,
      text = hashCashInfoToShow.descriptionText
    )

    Spacer(modifier = Modifier.height(8.dp))

    KurobaComposeText(
      modifier = Modifier
        .padding(horizontal = 8.dp, vertical = 4.dp),
      text = stringResource(R.string.lynxchan_url_example_description),
    )

    Spacer(modifier = Modifier.height(4.dp))

    KurobaComposeText(
      modifier = Modifier
        .padding(horizontal = 8.dp, vertical = 4.dp),
      text = hashCashInfoToShow.urlExample,
    )

    Spacer(modifier = Modifier.height(8.dp))

    val textStyle = remember { TextStyle(fontSize = 16.sp) }

    LaunchedEffect(key1 = urlTextFieldState) {
      urlTextFieldState.forEachTextValue { text ->
        currentErrorMut.value = viewModel.validateHashCashUrl(text)
      }
    }

    if (currentError.isNotNullNorEmpty()) {
      val text = remember(key1 = currentError, key2 = chanTheme.errorColorCompose) {
        buildAnnotatedString {
          withStyle(SpanStyle(color = chanTheme.errorColorCompose)) {
            append("Current error:")
          }

          append(" ")
          append(currentError)
        }
      }

      KurobaComposeText(
        modifier = Modifier
          .padding(horizontal = 8.dp, vertical = 4.dp),
        text = text
      )
    }

    KurobaComposeTextFieldV2(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(),
      state = urlTextFieldState,
      textStyle = textStyle,
      lineLimits = TextFieldLineLimits.MultiLine(),
      isError = currentError.isNotNullNorEmpty(),
      label = { interactionSource ->
        KurobaLabelText(
          enabled = true,
          labelText = stringResource(id = R.string.lynxchan_hashcash_textfield_label_url),
          fontSize = 12.ktu,
          interactionSource = interactionSource
        )
      }
    )

    Spacer(modifier = Modifier.height(8.dp))

    LynxchanCaptchaButtonsSection(
      resetButtonEnabled = true,
      reloadButtonEnabled = true,
      verifyButtonEnabled = urlTextFieldState.text.isNotEmpty() &&
        currentError.isNullOrEmpty() ||
        applyingCookiesFromUrl,
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
        applyingCookiesFromUrl = true

        try {
          coroutineScope.launch {
            val url = urlTextFieldState.text.toString().toHttpUrl()

            val result = viewModel.applyHashCashCookiesByUrl(chanDescriptor, url)
              .onError { error -> Logger.error(TAG, error) { "Failed to apply hashcash cookies by url" } }
            onCookiesFromUrlApplied(result)
          }
        } finally {
          applyingCookiesFromUrl = false
        }
      }
    )

    Spacer(modifier = Modifier.height(8.dp))
  }
}