package com.github.k1rakishou.chan.ui.captcha.lynxchan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeTextBarButton

@Composable
fun LynxchanCaptchaButtonsSection(
  resetButtonEnabled: Boolean,
  reloadButtonEnabled: Boolean,
  verifyButtonEnabled: Boolean,
  onResetButtonClicked: () -> Unit,
  onReloadButtonClicked: () -> Unit,
  onVerifyButtonClicked: () -> Unit,
) {
  Row(
    horizontalArrangement = Arrangement.End,
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
  ) {
    Spacer(modifier = Modifier.width(8.dp))

    KurobaComposeTextBarButton(
      enabled = resetButtonEnabled,
      onClick = onResetButtonClicked,
      text = stringResource(id = R.string.captcha_layout_reset)
    )

    Spacer(modifier = Modifier.weight(1f))

    KurobaComposeTextBarButton(
      enabled = reloadButtonEnabled,
      onClick = onReloadButtonClicked,
      text = stringResource(id = R.string.captcha_layout_reload)
    )

    Spacer(modifier = Modifier.width(8.dp))

    KurobaComposeTextBarButton(
      onClick = onVerifyButtonClicked,
      enabled = verifyButtonEnabled,
      text = stringResource(id = R.string.captcha_layout_verify)
    )

    Spacer(modifier = Modifier.width(8.dp))
  }
}