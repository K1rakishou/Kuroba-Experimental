package com.github.k1rakishou.chan.features.settings

import android.content.Context
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.features.settings.setting.CookieSettingV2
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeCard
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeTextFieldV2
import com.github.k1rakishou.chan.ui.compose.components.KurobaLabelText
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.controller.base.BaseFloatingComposeController
import com.github.k1rakishou.common.KurobaCookie

class CookieCaptchaInputController(
  context: Context,
  private val cookieSettingV2: CookieSettingV2,
  private val onOkClicked: (KurobaCookie?) -> Unit
) : BaseFloatingComposeController(context) {

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  @Composable
  override fun BoxScope.BuildContent() {
    val chanTheme = LocalChanTheme.current
    val kurobaCookie = remember { cookieSettingV2.setting.get() }

    val initialValue = remember(key1 = kurobaCookie) { kurobaCookie?.value ?: "" }
    val initialLifetimeMinutes = remember(key1 = kurobaCookie) {
      if (kurobaCookie == null) {
        return@remember ""
      }

      val expirationMillis = kurobaCookie.expirationMillis()
      if (expirationMillis == null) {
        return@remember ""
      }

      val deltaTimeMillis = expirationMillis - System.currentTimeMillis()
      if (deltaTimeMillis <= 0) {
        return@remember ""
      }

      return@remember (deltaTimeMillis / KurobaCookie.Companion.MillisPerMinute).toString()
    }
    val initialPath = remember(key1 = kurobaCookie) { kurobaCookie?.path ?: "/" }

    val cookieValueState = rememberTextFieldState(initialText = initialValue)
    val cookieLifetimeMinutesState = rememberTextFieldState(initialLifetimeMinutes)
    val cookiePathState = rememberTextFieldState(initialText = initialPath)

    KurobaComposeCard {
      Column(
        modifier = Modifier.Companion
          .padding(
            horizontal = 16.dp,
            vertical = 16.dp
          )
      ) {
        KurobaComposeText(
          text = cookieSettingV2.topDescription,
          fontSize = 20.ktu
        )

        Spacer(modifier = Modifier.Companion.height(16.dp))

        KurobaComposeTextFieldV2(
          modifier = Modifier.Companion.fillMaxWidth(),
          state = cookieValueState,
          keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Companion.None,
            keyboardType = KeyboardType.Companion.Text,
            autoCorrectEnabled = false
          ),
          label = { interactionSource ->
            KurobaLabelText(
              enabled = true,
              labelText = stringResource(R.string.cookie_captcha_input_controller_value),
              fontSize = 12.ktu,
              interactionSource = interactionSource
            )
          }
        )

        Spacer(modifier = Modifier.Companion.height(16.dp))

        KurobaComposeText(
          text = stringResource(R.string.cookie_captcha_input_controller_expiration_description),
          color = chanTheme.textColorSecondaryCompose,
          fontSize = 14.ktu
        )

        Spacer(modifier = Modifier.Companion.height(4.dp))

        KurobaComposeTextFieldV2(
          modifier = Modifier.Companion.fillMaxWidth(),
          state = cookieLifetimeMinutesState,
          keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Companion.None,
            keyboardType = KeyboardType.Companion.Number,
            autoCorrectEnabled = false
          ),
          label = { interactionSource ->
            val currentExpiration = remember(cookieLifetimeMinutesState.text) {
              val expirationInMinutes = cookieLifetimeMinutesState.text.toString().toLongOrNull()
              if (expirationInMinutes == null) {
                return@remember ""
              }

              if (expirationInMinutes < 0) {
                return@remember "(Expires: never)"
              }

              if (expirationInMinutes == 0L) {
                return@remember "(Expires: end of session)"
              }

              val expirationTimeMillis =
                System.currentTimeMillis() + expirationInMinutes * KurobaCookie.Companion.MillisPerMinute
              val expirationTimeFormatted = KurobaCookie.Companion.HttpDateFormatter.print(expirationTimeMillis)
              return@remember "(Expires: ${expirationTimeFormatted})"
            }

            KurobaLabelText(
              enabled = true,
              labelText = stringResource(R.string.cookie_captcha_input_controller_lifetime, currentExpiration),
              fontSize = 12.ktu,
              interactionSource = interactionSource
            )
          }
        )

        Spacer(modifier = Modifier.Companion.height(16.dp))

        KurobaComposeText(
          text = stringResource(R.string.cookie_captcha_input_controller_path_description),
          color = chanTheme.textColorSecondaryCompose,
          fontSize = 14.ktu
        )

        Spacer(modifier = Modifier.Companion.height(4.dp))

        KurobaComposeTextFieldV2(
          modifier = Modifier.Companion.fillMaxWidth(),
          state = cookiePathState,
          keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Companion.None,
            keyboardType = KeyboardType.Companion.Text,
            autoCorrectEnabled = false
          ),
          label = { interactionSource ->
            KurobaLabelText(
              enabled = true,
              labelText = stringResource(R.string.cookie_captcha_input_controller_path),
              fontSize = 12.ktu,
              interactionSource = interactionSource
            )
          }
        )

        Spacer(modifier = Modifier.Companion.height(16.dp))

        Row(
          modifier = Modifier.Companion
            .fillMaxWidth()
            .wrapContentHeight()
        ) {
          KurobaComposeTextBarButton(
            onClick = {
              onOkClicked(null)
              pop()
            },
            text = stringResource(id = R.string.reset)
          )

          Spacer(modifier = Modifier.Companion.weight(1f))

          KurobaComposeTextBarButton(
            onClick = { pop() },
            text = stringResource(id = R.string.cancel)
          )

          Spacer(modifier = Modifier.Companion.width(24.dp))

          KurobaComposeTextBarButton(
            onClick = {
              validateAndSave(
                cookieValueState = cookieValueState,
                cookieLifetimeMinutesState = cookieLifetimeMinutesState,
                cookiePathState = cookiePathState
              )
            },
            text = stringResource(id = R.string.ok)
          )
        }
      }
    }
  }

  private fun validateAndSave(
    cookieValueState: TextFieldState,
    cookieLifetimeMinutesState: TextFieldState,
    cookiePathState: TextFieldState
  ) {
    val value = cookieValueState.text.toString()
    if (value.isBlank()) {
      showErrorToast("Cookie value cannot be empty/blank")
      return
    }

    val lifetimeMillis = cookieLifetimeMinutesState.text.toString()
      .toLongOrNull()
      ?.times(KurobaCookie.Companion.MillisPerMinute)
    if (lifetimeMillis == null) {
      showErrorToast("Invalid lifetime (cannot be converted into a number)")
      return
    }

    var path = cookiePathState.text.toString()
    if (path.isBlank()) {
      path = "/"
    }

    val expiration = when {
      lifetimeMillis < 0 -> KurobaCookie.Expiration.Never
      lifetimeMillis == 0L -> KurobaCookie.Expiration.Session
      else -> KurobaCookie.Expiration.Time(System.currentTimeMillis() + lifetimeMillis)
    }

    val kurobaCookie = KurobaCookie(
      value = value,
      expiration = expiration,
      path = path
    )

    onOkClicked(kurobaCookie)
    pop()
  }
}