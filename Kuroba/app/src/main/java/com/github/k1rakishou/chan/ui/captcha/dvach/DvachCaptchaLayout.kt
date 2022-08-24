package com.github.k1rakishou.chan.ui.captcha.dvach

import android.content.Context
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutCallback
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutInterface
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder
import com.github.k1rakishou.chan.ui.captcha.CaptchaSolution
import com.github.k1rakishou.chan.ui.compose.ImageLoaderRequest
import com.github.k1rakishou.chan.ui.compose.ImageLoaderRequestData
import com.github.k1rakishou.chan.ui.compose.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.KurobaComposeImage
import com.github.k1rakishou.chan.ui.compose.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.KurobaComposeTextField
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.ProvideChanTheme
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayout
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import javax.inject.Inject

class DvachCaptchaLayout(context: Context) : TouchBlockingFrameLayout(context),
  AuthenticationLayoutInterface {

  @Inject
  lateinit var captchaHolder: CaptchaHolder
  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2
  @Inject
  lateinit var siteManager: SiteManager

  private val viewModel by lazy { (context as ComponentActivity).viewModelByKey<DvachCaptchaLayoutViewModel>() }
  private val scope = KurobaCoroutineScope()

  private var siteDescriptor: SiteDescriptor? = null
  private var siteAuthentication: SiteAuthentication? = null
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
    this.siteAuthentication = authentication
    this.callback = callback

    val view = ComposeView(context).apply {
      setContent {
        ProvideChanTheme(themeEngine) {
          val chanTheme = LocalChanTheme.current

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(300.dp)
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
    val baseUrl = siteAuthentication?.baseUrl
      ?: return

    viewModel.requestCaptcha(baseUrl)
  }

  override fun onDestroy() {
    this.siteAuthentication = null
    this.callback = null

    scope.cancelChildren()
    viewModel.cleanup()
  }

  @Composable
  private fun BuildContent() {
    BuildCaptchaInput(
      onReloadClick = { hardReset() },
      onVerifyClick = { captchaId, token ->
        val solution = CaptchaSolution.ChallengeWithSolution(
          challenge = captchaId,
          solution = token
        )

        captchaHolder.addNewSolution(solution)
        callback?.onAuthenticationComplete()
      }
    )
  }

  @Composable
  private fun BuildCaptchaInput(
    onReloadClick: () -> Unit,
    onVerifyClick: (String, String) -> Unit
  ) {
    Column(modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
    ) {
      BuildCaptchaImage(onReloadClick)

      Spacer(modifier = Modifier.height(16.dp))

      var currentInputValue by viewModel.currentInputValue
      val captchaInfoAsync by viewModel.captchaInfoToShow
      val captchaInfo = (captchaInfoAsync as? AsyncData.Data)?.data

      val captchaId = captchaInfo?.id

      if (captchaInfo != null) {
        val input = captchaInfo.input

        val keyboardOptions = remember(key1 = input) {
          when (input) {
            null -> KeyboardOptions.Default
            "numeric" -> KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.NumberPassword)
            else -> KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Password)
          }
        }

        KurobaComposeTextField(
          value = currentInputValue,
          onValueChange = { newValue -> currentInputValue = newValue },
          maxLines = 1,
          singleLine = true,
          keyboardOptions = keyboardOptions,
          keyboardActions = KeyboardActions(
            onDone = {
              if (captchaId.isNotNullNorEmpty()) {
                onVerifyClick(captchaId, viewModel.currentInputValue.value)
              }
            }
          ),
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
      }

      Spacer(modifier = Modifier.weight(1f))

      Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
      ) {
        KurobaComposeTextBarButton(
          onClick = onReloadClick,
          text = stringResource(id = R.string.captcha_layout_reload)
        )

        Spacer(modifier = Modifier.width(8.dp))

        val buttonEnabled = captchaId.isNotNullNorEmpty() && currentInputValue.isNotEmpty()

        KurobaComposeTextBarButton(
          onClick = {
            if (captchaId.isNotNullNorEmpty()) {
              onVerifyClick(captchaId, viewModel.currentInputValue.value)
            }
          },
          enabled = buttonEnabled,
          text = stringResource(id = R.string.captcha_layout_verify)
        )

        Spacer(modifier = Modifier.width(8.dp))
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }

  @Composable
  private fun BuildCaptchaImage(
    onReloadClick: () -> Unit
  ) {
    Box(modifier = Modifier
      .height(160.dp)
      .fillMaxWidth()
    ) {
      val captchaInfoAsync by viewModel.captchaInfoToShow
      when (captchaInfoAsync) {
        AsyncData.NotInitialized,
        AsyncData.Loading -> {
          // no-op
        }
        is AsyncData.Error -> {
          val error = (captchaInfoAsync as AsyncData.Error).throwable
          KurobaComposeErrorMessage(
            error = error,
            modifier = Modifier.fillMaxSize()
          )
        }
        is AsyncData.Data -> {
          val requestFullUrl = remember {
            (captchaInfoAsync as AsyncData.Data).data.fullRequestUrl(siteManager = siteManager)
          }

          if (requestFullUrl == null) {
            return@Box
          }

          val request = remember(key1 = requestFullUrl) {
            val data = ImageLoaderRequestData.Url(
              httpUrl = requestFullUrl,
              cacheFileType = CacheFileType.Other
            )

            ImageLoaderRequest(data = data)
          }

          KurobaComposeImage(
            request = request,
            modifier = Modifier
              .fillMaxSize()
              .clickable { onReloadClick() },
            imageLoaderV2 = imageLoaderV2,
            loading = { KurobaComposeProgressIndicator() },
            error = { throwable -> KurobaComposeErrorMessage(error = throwable) }
          )
        }
      }
    }
  }

}