package com.github.k1rakishou.chan.ui.captcha.dvach

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutCallback
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutInterface
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder
import com.github.k1rakishou.chan.ui.captcha.CaptchaSolution
import com.github.k1rakishou.chan.ui.compose.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.KurobaComposeTextButton
import com.github.k1rakishou.chan.ui.compose.KurobaComposeTextField
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.ProvideChanTheme
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayout
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.google.accompanist.coil.rememberCoilPainter
import com.google.accompanist.insets.ProvideWindowInsets
import javax.inject.Inject

class DvachCaptchaLayout(context: Context) : TouchBlockingFrameLayout(context),
  AuthenticationLayoutInterface {

  @Inject
  lateinit var captchaHolder: CaptchaHolder
  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2

  private val viewModel by lazy { (context as ComponentActivity).viewModelByKey<DvachCaptchaLayoutViewModel>() }
  private val scope = KurobaCoroutineScope()

  private var siteAuthentication: SiteAuthentication? = null
  private var callback: AuthenticationLayoutCallback? = null

  init {
    AppModuleAndroidUtils.extractActivityComponent(getContext())
      .inject(this)
  }

  override fun initialize(
    authentication: SiteAuthentication,
    callback: AuthenticationLayoutCallback
  ) {
    this.siteAuthentication = authentication
    this.callback = callback

    val view = ComposeView(context).apply {
      setContent {
        ProvideChanTheme(themeEngine) {
          ProvideWindowInsets {
            val chanTheme = LocalChanTheme.current

            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(chanTheme.backColorCompose)
            ) {
              BuildContent()
            }
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
        val solution = CaptchaSolution.TokenWithIdSolution(
          id = captchaId,
          token = token,
          type = CaptchaSolution.TokenWithIdSolution.Type.DvachCaptcha
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
    val context = LocalContext.current
    val chanTheme = LocalChanTheme.current

    val errorDrawable = remember(key1 = chanTheme) {
      imageLoaderV2.getImageErrorLoadingDrawable(context)
    }

    Column(modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
    ) {
      BuildCaptchaImage(errorDrawable, onReloadClick)

      Spacer(modifier = Modifier.height(16.dp))

      val focusRequester = FocusRequester()

      var currentInputValue by viewModel.currentInputValue
      KurobaComposeTextField(
        value = currentInputValue,
        onValueChange = { newValue -> currentInputValue = newValue },
        maxLines = 1,
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .padding(horizontal = 16.dp)
          .focusRequester(focusRequester)
      )

      DisposableEffect(Unit) {
        focusRequester.requestFocus()
        onDispose { }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
      ) {
        KurobaComposeTextButton(
          onClick = onReloadClick,
          modifier = Modifier
            .width(112.dp)
            .height(36.dp),
          text = stringResource(id = R.string.captcha_layout_dvach_reload)
        )

        Spacer(modifier = Modifier.width(8.dp))

        val captchaIdAsync by viewModel.captchaIdToShow
        val captchaId = (captchaIdAsync as? AsyncData.Data)?.data
        val enabled = captchaId.isNotNullNorEmpty() && currentInputValue.isNotEmpty()

        KurobaComposeTextButton(
          onClick = {
            if (captchaId.isNotNullNorEmpty()) {
              onVerifyClick(captchaId, viewModel.currentInputValue.value)
            }
          },
          enabled = enabled,
          modifier = Modifier
            .width(112.dp)
            .height(36.dp),
          text = stringResource(id = R.string.captcha_layout_dvach_verify)
        )

        Spacer(modifier = Modifier.width(8.dp))
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }

  @Composable
  private fun BuildCaptchaImage(
    errorDrawable: BitmapDrawable,
    onReloadClick: () -> Unit
  ) {
    Box(modifier = Modifier
      .height(200.dp)
      .fillMaxWidth()
    ) {
      val captchaId by viewModel.captchaIdToShow
      when (captchaId) {
        AsyncData.NotInitialized,
        AsyncData.Loading -> {
          KurobaComposeProgressIndicator(
            modifier = Modifier.fillMaxSize()
          )
        }
        is AsyncData.Error -> {
          val error = (captchaId as AsyncData.Error).throwable
          KurobaComposeErrorMessage(
            error = error,
            modifier = Modifier.fillMaxSize()
          )
        }
        is AsyncData.Data -> {
          val id = (captchaId as AsyncData.Data).data

          val requestFullUrl = remember(key1 = id) {
            "https://2ch.hk/api/captcha/2chcaptcha/show?id=$id"
          }

          Image(
            painter = rememberCoilPainter(
              request = requestFullUrl,
              requestBuilder = { this.error(errorDrawable) }
            ),
            contentDescription = null,
            modifier = Modifier
              .fillMaxSize()
              .clickable { onReloadClick() }
          )
        }
      }
    }
  }

}