package com.github.k1rakishou.chan.features.settings.delegate

import android.content.Context
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.controller.ControllerComponent
import com.github.k1rakishou.chan.features.settings.setting.SettingUiElement
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeCard
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeTextFieldV2
import com.github.k1rakishou.chan.ui.compose.components.KurobaLabelText
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.controller.base.BaseFloatingComposeController
import com.github.k1rakishou.chan.utils.ViewModelScope
import com.github.k1rakishou.common.KurobaCookie
import com.github.k1rakishou.common.isNotNullNorBlank

class CookieCaptchaInputController(
  context: Context,
  private val cookieSettingUiElement: SettingUiElement.Cookie
) : BaseFloatingComposeController(context) {

  override val viewModelScope: ViewModelScope
    get() = ViewModelScope.ControllerScope(this)

  override fun injectControllerDependencies(component: ControllerComponent) {
    component.inject(this)
  }

  @Composable
  override fun BoxScope.BuildContent() {
    val chanTheme = LocalChanTheme.current
    val kurobaCookieSetting = cookieSettingUiElement.setting

    var kurobaCookieMut by remember { mutableStateOf<KurobaCookie?>(null) }
    val kurobaCookie = kurobaCookieMut
    LaunchedEffect(key1 = Unit) {
      val cookie = kurobaCookieSetting.read()
      if (cookie == null) {
        kurobaCookieMut = KurobaCookie(
          value = "",
          expiration = KurobaCookie.Expiration.Session
        )

        return@LaunchedEffect
      }

      kurobaCookieMut = cookie
    }

    if (kurobaCookie == null) {
      return
    }

    val currentTime = remember { System.currentTimeMillis() }

    var titleMut by remember { mutableStateOf<String?>(null) }
    val title = titleMut
    LaunchedEffect(key1 = Unit) {
      titleMut = cookieSettingUiElement.title()
    }

    val initialValue = remember(key1 = kurobaCookie) { kurobaCookie.value }
    val initialLifetimeMinutes = remember(key1 = kurobaCookie) {
      val expirationMillis = kurobaCookie.expirationMillis()
      if (expirationMillis == null) {
        return@remember ""
      }

      val deltaTimeMillis = expirationMillis - currentTime
      if (deltaTimeMillis <= 0) {
        return@remember ""
      }

      return@remember (deltaTimeMillis / KurobaCookie.MillisPerMinute).toString()
    }

    val initialPath = remember(key1 = kurobaCookie) { kurobaCookie.path }
    val cookieValueState = rememberTextFieldState(initialText = initialValue)
    val cookieLifetimeMinutesState = rememberTextFieldState(initialLifetimeMinutes)
    val cookiePathState = rememberTextFieldState(initialText = initialPath)

    val currentExpiration = remember(key1 = kurobaCookie) {
      val expirationTimeMillis = when (val expiration = kurobaCookie.expiration) {
        KurobaCookie.Expiration.Never -> {
          return@remember appResources.string(R.string.cookie_captcha_input_controller_expires_never)
        }
        KurobaCookie.Expiration.Session -> {
          return@remember appResources.string(R.string.cookie_captcha_input_controller_expires_end_of_session)
        }
        is KurobaCookie.Expiration.Time -> expiration.expirationTimeMillis
      }

      val deltaTimeMillis = expirationTimeMillis - currentTime
      if (deltaTimeMillis <= 0) {
        return@remember appResources.string(
          R.string.cookie_captcha_input_controller_cookie_already_expired,
          KurobaCookie.HttpDateFormatter.print(expirationTimeMillis)
        )
      }

      return@remember KurobaCookie.HttpDateFormatter.print(expirationTimeMillis)
    }

    val updatedExpiration = remember(key1 = cookieLifetimeMinutesState.text) {
      val currentCookieLifetimeMinutes = cookieLifetimeMinutesState.text.toString().toLongOrNull()
      if (currentCookieLifetimeMinutes == null) {
        return@remember appResources.string(R.string.cookie_captcha_input_controller_invalid_value)
      }

      when {
        currentCookieLifetimeMinutes < 0L -> {
          return@remember appResources.string(R.string.cookie_captcha_input_controller_expires_never)
        }
        currentCookieLifetimeMinutes == 0L -> {
          return@remember appResources.string(R.string.cookie_captcha_input_controller_expires_end_of_session)
        }
        else -> {
          val expirationDeltaMillis = currentCookieLifetimeMinutes * KurobaCookie.MillisPerMinute
          val expirationTimeMillis = currentTime + expirationDeltaMillis

          return@remember KurobaCookie.HttpDateFormatter.print(expirationTimeMillis)
        }
      }
    }

    KurobaComposeCard {
      Column(
        modifier = Modifier
          .padding(
            horizontal = 16.dp,
            vertical = 16.dp
          )
      ) {
        if (title.isNotNullNorBlank()) {
          KurobaComposeText(
            text = title,
            fontSize = 20.ktu
          )

          Spacer(modifier = Modifier.height(16.dp))
        }

        KurobaComposeTextFieldV2(
          modifier = Modifier.fillMaxWidth(),
          state = cookieValueState,
          keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            keyboardType = KeyboardType.Text,
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

        Spacer(modifier = Modifier.height(16.dp))

        run {
          KurobaComposeText(
            text = stringResource(R.string.cookie_captcha_input_controller_lifetime_description),
            color = chanTheme.textColorHintCompose,
            fontSize = 14.ktu
          )

          Spacer(modifier = Modifier.height(4.dp))

          KurobaComposeText(
            text = remember(key1 = currentExpiration) {
              buildAnnotatedString {
                append(appResources.string(R.string.cookie_captcha_input_controller_current_expiration))
                append(" ")

                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                  append(currentExpiration)
                }
              }
            },
            color = chanTheme.textColorSecondaryCompose,
            fontSize = 11.ktu
          )

          if (cookieLifetimeMinutesState.text != initialLifetimeMinutes) {
            Spacer(modifier = Modifier.height(4.dp))

            KurobaComposeText(
              text = remember(key1 = updatedExpiration) {
                buildAnnotatedString {
                  append(appResources.string(R.string.cookie_captcha_input_controller_updated_expiration))
                  append(" ")

                  withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(updatedExpiration)
                  }
                }
              },
              color = chanTheme.textColorSecondaryCompose,
              fontSize = 11.ktu
            )
          }

          Spacer(modifier = Modifier.height(4.dp))

          KurobaComposeTextFieldV2(
            modifier = Modifier.fillMaxWidth(),
            state = cookieLifetimeMinutesState,
            keyboardOptions = KeyboardOptions(
              capitalization = KeyboardCapitalization.None,
              keyboardType = KeyboardType.Number,
              autoCorrectEnabled = false
            )
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        KurobaComposeText(
          text = stringResource(R.string.cookie_captcha_input_controller_path_description),
          color = chanTheme.textColorSecondaryCompose,
          fontSize = 14.ktu
        )

        Spacer(modifier = Modifier.height(4.dp))

        KurobaComposeTextFieldV2(
          modifier = Modifier.fillMaxWidth(),
          state = cookiePathState,
          keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            keyboardType = KeyboardType.Text,
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

        Spacer(modifier = Modifier.height(16.dp))

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
        ) {
          KurobaComposeTextBarButton(
            onClick = { popWithResult(null) },
            text = stringResource(id = R.string.reset)
          )

          Spacer(modifier = Modifier.weight(1f))

          val cookieUpdated = cookieValueState.text != initialValue ||
            cookieLifetimeMinutesState.text != initialLifetimeMinutes ||
            cookiePathState.text != initialPath

          KurobaComposeTextBarButton(
            onClick = {
              if (!cookieUpdated) {
                pop()
                return@KurobaComposeTextBarButton
              }

              val validatedCookie = validate(
                cookieValueState = cookieValueState,
                cookieLifetimeMinutesState = cookieLifetimeMinutesState,
                cookiePathState = cookiePathState
              )

              if (validatedCookie != null) {
                popWithResult(validatedCookie)
              }
            },
            text = if (cookieUpdated) {
              stringResource(id = R.string.save)
            } else {
              stringResource(id = R.string.close)
            }
          )
        }
      }
    }
  }

  private fun validate(
    cookieValueState: TextFieldState,
    cookieLifetimeMinutesState: TextFieldState,
    cookiePathState: TextFieldState
  ): KurobaCookie? {
    val value = cookieValueState.text.toString()
    if (value.isBlank()) {
      showErrorToast(R.string.cookie_captcha_input_controller_cookie_error_empty)
      return null
    }

    val lifetimeMillis = cookieLifetimeMinutesState.text.toString()
      .toLongOrNull()
      ?.times(KurobaCookie.MillisPerMinute)
    if (lifetimeMillis == null) {
      showErrorToast(R.string.cookie_captcha_input_controller_cookie_error_invalid_lifetime)
      return null
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

    return KurobaCookie(
      value = value,
      expiration = expiration,
      path = path
    )
  }
}