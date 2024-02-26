package com.github.k1rakishou.chan.features.reply.right

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable

@Composable
internal fun PrefillCaptchaButton(
  iconSize: Dp,
  padding: Dp,
  onPrefillCaptchaButtonClicked: () -> Unit
) {
  // TODO: New reply layout. Draw how many captchas are currently prefilled
  Image(
    modifier = Modifier
      .size(iconSize)
      .padding(padding)
      .kurobaClickable(
        bounded = false,
        onClick = onPrefillCaptchaButtonClicked
      ),
    contentDescription = null,
    painter = painterResource(id = R.drawable.ic_captcha_24dp)
  )
}
