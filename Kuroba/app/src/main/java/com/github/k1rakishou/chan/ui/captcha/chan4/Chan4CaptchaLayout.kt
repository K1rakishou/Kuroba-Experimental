package com.github.k1rakishou.chan.ui.captcha.chan4

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.google.accompanist.insets.ProvideWindowInsets
import javax.inject.Inject

@SuppressLint("ViewConstructor")
class Chan4CaptchaLayout(
  context: Context,
  private val chanDescriptor: ChanDescriptor
) : TouchBlockingFrameLayout(context), AuthenticationLayoutInterface {

  @Inject
  lateinit var captchaHolder: CaptchaHolder
  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2

  private val viewModel by lazy { (context as ComponentActivity).viewModelByKey<Chan4CaptchaLayoutViewModel>() }
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
          ProvideWindowInsets {
            val chanTheme = LocalChanTheme.current

            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
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
    viewModel.requestCaptcha(chanDescriptor)
  }

  override fun onDestroy() {
    this.siteAuthentication = null
    this.callback = null

    scope.cancelChildren()
    viewModel.cleanup()
  }

  @Composable
  private fun BuildContent() {
    Column(modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
    ) {
      val captchaInfoAsync by viewModel.captchaInfoToShow
      var currentInputValue by viewModel.currentInputValue
      var sliderValue by remember { mutableStateOf(0f) }

      BuildCaptchaImage(captchaInfoAsync = captchaInfoAsync, sliderValue = sliderValue)

      Spacer(modifier = Modifier.height(8.dp))

      val captchaInfo = (captchaInfoAsync as? AsyncData.Data)?.data
      if (captchaInfo != null && captchaInfo.needSlider()) {
        Slider(
          value = sliderValue,
          modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          onValueChange = { newValue -> sliderValue = newValue }
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      KurobaComposeTextField(
        value = currentInputValue,
        onValueChange = { newValue -> currentInputValue = newValue },
        maxLines = 1,
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .padding(horizontal = 16.dp)
      )

      Spacer(modifier = Modifier.weight(1f))

      Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
      ) {
        KurobaComposeTextButton(
          onClick = {
            sliderValue = 0f
            hardReset()
          },
          modifier = Modifier
            .width(112.dp)
            .height(36.dp),
          text = stringResource(id = R.string.captcha_layout_reload)
        )

        Spacer(modifier = Modifier.width(8.dp))

        val buttonEnabled = captchaInfo != null && currentInputValue.isNotEmpty()

        KurobaComposeTextButton(
          onClick = {
            if (captchaInfo == null) {
              return@KurobaComposeTextButton
            }

            val challenge = captchaInfo.challenge
            val solution = CaptchaSolution.TokenWithIdSolution(id = challenge, token = currentInputValue)

            captchaHolder.addNewSolution(solution, captchaInfo.ttlMillis())
            callback?.onAuthenticationComplete()
          },
          enabled = buttonEnabled,
          modifier = Modifier
            .width(112.dp)
            .height(36.dp),
          text = stringResource(id = R.string.captcha_layout_verify)
        )

        Spacer(modifier = Modifier.width(8.dp))
      }

      Spacer(modifier = Modifier.height(8.dp))
    }
  }

  @Composable
  private fun BuildCaptchaImage(
    captchaInfoAsync: AsyncData<Chan4CaptchaLayoutViewModel.CaptchaInfo>,
    sliderValue: Float
  ) {
    var size by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = Modifier
      .height(160.dp)
      .fillMaxWidth()
      .onSizeChanged { newSize -> size = newSize }
    ) {
      if (size != IntSize.Zero) {
        when (captchaInfoAsync) {
          AsyncData.NotInitialized,
          AsyncData.Loading -> {
            KurobaComposeProgressIndicator()
          }
          is AsyncData.Error -> {
            val error = (captchaInfoAsync as AsyncData.Error).throwable
            KurobaComposeErrorMessage(
              error = error,
              modifier = Modifier.fillMaxSize()
            )
          }
          is AsyncData.Data -> {
            val captchaInfo = (captchaInfoAsync as AsyncData.Data).data
            val contentScale = Scale()

            if (captchaInfo.bgBitmapPainter != null) {
              val bgBitmapPainter = captchaInfo.bgBitmapPainter
              val offset = remember(key1 = sliderValue) {
                IntOffset(x = (START_OFFSET + (sliderValue * MAX_OFFSET * -1f)).toInt(), y = 0)
              }

              Image(
                modifier = Modifier
                  .fillMaxSize()
                  .offset { offset },
                painter = bgBitmapPainter,
                contentScale = contentScale,
                contentDescription = null,
              )
            }

            Image(
              modifier = Modifier.fillMaxSize(),
              painter = captchaInfo.imgBitmapPainter,
              contentScale = contentScale,
              contentDescription = null
            )
          }
        }
      }
    }
  }

  class Scale : ContentScale {
    override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
      return ScaleFactor(4f, 4f)
    }
  }
  
  companion object {
    private const val START_OFFSET = 100f
    private const val MAX_OFFSET = 300f
  }

}