package com.github.k1rakishou.chan.ui.captcha.chan4

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutCallback
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutInterface
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder
import com.github.k1rakishou.chan.ui.captcha.CaptchaSolution
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers.simpleVerticalScrollbar
import com.github.k1rakishou.chan.ui.compose.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.KurobaComposeSnappingSlider
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.KurobaComposeTextField
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.ProvideChanTheme
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayout
import com.github.k1rakishou.chan.ui.view.floating_menu.CheckableFloatingListMenuItem
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@SuppressLint("ViewConstructor")
class Chan4CaptchaLayout(
  context: Context,
  private val chanDescriptor: ChanDescriptor,
  private val presentControllerFunc: (Controller) -> Unit
) : TouchBlockingFrameLayout(context), AuthenticationLayoutInterface {

  @Inject
  lateinit var captchaHolder: CaptchaHolder
  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var dialogFactory: DialogFactory

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

    scope.launch {
      viewModel.showCaptchaHelpFlow.take(1).collect {
        showCaptchaHelp()
      }
    }

    val view = ComposeView(context).apply {
      setContent {
        ProvideChanTheme(themeEngine) {
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
    viewModel.requestCaptcha(chanDescriptor, forced = false)
  }

  override fun onDestroy() {
    this.siteAuthentication = null
    this.callback = null

    scope.cancelChildren()

    viewModel.resetCaptchaIfCaptchaIsAlmostDead(chanDescriptor)
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
  private fun BuildCaptchaWindow() {
    val sliderCaptchaGridMode = viewModel.chan4CaptchaSettingsJson.get().sliderCaptchaGridMode

    BuildCaptchaWindowImageOrText(
      sliderCaptchaGridMode = sliderCaptchaGridMode
    )

    Spacer(modifier = Modifier.height(8.dp))

    BuildCaptchaWindowSliderOrInput(
      sliderCaptchaGridMode = sliderCaptchaGridMode
    )

    BuildCaptchaWindowFooter()

    Spacer(modifier = Modifier.height(8.dp))
  }

  @Composable
  private fun BuildCaptchaWindowSliderOrInput(
    sliderCaptchaGridMode: Boolean
  ) {
    val chanTheme = LocalChanTheme.current
    val captchaInfoAsync by viewModel.captchaInfoToShow
    val captchaInfo = (captchaInfoAsync as? AsyncData.Data)?.data

    if (captchaInfo != null && !captchaInfo.isNoopChallenge()) {
      var currentInputValue by captchaInfo.currentInputValue
      val scrollValueState = captchaInfo.sliderValue

      KurobaComposeTextField(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .padding(horizontal = 16.dp),
        value = currentInputValue,
        onValueChange = { newValue -> currentInputValue = newValue.uppercase(Locale.ENGLISH) },
        keyboardActions = KeyboardActions(
          onDone = { verifyCaptcha(captchaInfo, currentInputValue) }
        ),
        keyboardOptions = KeyboardOptions(
          autoCorrect = false,
          keyboardType = KeyboardType.Password
        ),
        maxLines = 1,
        singleLine = true
      )

      Spacer(modifier = Modifier.height(8.dp))

      if (captchaInfo.needSlider() && !sliderCaptchaGridMode) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
          val widthDiff = captchaInfo.widthDiff()

          val slideSteps = widthDiff
            ?: (constraints.maxWidth / PIXELS_PER_STEP)

          KurobaComposeSnappingSlider(
            slideOffsetState = scrollValueState,
            slideSteps = slideSteps.coerceAtLeast(MIN_SLIDE_STEPS),
            backgroundColor = chanTheme.backColorCompose,
            modifier = Modifier
              .wrapContentHeight()
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
            onValueChange = { newValue -> scrollValueState.value = newValue }
          )
        }

        Spacer(modifier = Modifier.height(8.dp))
      }
    }
  }

  @Composable
  private fun BuildCaptchaWindowFooter() {
    val chanTheme = LocalChanTheme.current
    val captchaInfoAsync by viewModel.captchaInfoToShow
    val captchaInfo = (captchaInfoAsync as? AsyncData.Data)?.data

    Row(
      horizontalArrangement = Arrangement.End,
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
    ) {
      val drawableTintColor = ThemeEngine.resolveDrawableTintColor(chanTheme)
      val colorFilter = remember(key1 = drawableTintColor) { ColorFilter.tint(color = Color(drawableTintColor)) }

      Image(
        painter = painterResource(id = R.drawable.ic_settings_white_24dp),
        contentDescription = null,
        colorFilter = colorFilter,
        modifier = Modifier
          .padding(start = 16.dp)
          .width(24.dp)
          .height(24.dp)
          .align(Alignment.CenterVertically)
          .clickable { showChan4CaptchaSettings() }
      )

      val captchaTtlMillis by viewModel.captchaTtlMillisFlow.collectAsState()
      if (captchaTtlMillis >= 0L) {
        Spacer(modifier = Modifier.width(8.dp))

        KurobaComposeText(
          text = "${captchaTtlMillis / 1000L} sec",
          fontSize = 13.sp,
          modifier = Modifier.align(Alignment.CenterVertically)
        )
      }

      Spacer(modifier = Modifier.weight(1f))

      KurobaComposeTextBarButton(
        onClick = {
          viewModel.requestCaptcha(chanDescriptor, forced = true)
        },
        text = stringResource(id = R.string.captcha_layout_reload)
      )

      Spacer(modifier = Modifier.width(8.dp))

      val buttonEnabled = captchaInfo != null
        && (captchaInfo.isNoopChallenge() || captchaInfo.currentInputValue.value.isNotEmpty())

      KurobaComposeTextBarButton(
        onClick = {
          val currentInputValue = captchaInfo?.currentInputValue
            ?: return@KurobaComposeTextBarButton

          verifyCaptcha(captchaInfo, currentInputValue.value)
        },
        enabled = buttonEnabled,
        text = stringResource(id = R.string.captcha_layout_verify)
      )

      Spacer(modifier = Modifier.width(8.dp))
    }
  }

  @Composable
  private fun BuildCaptchaWindowImageOrText(
    sliderCaptchaGridMode: Boolean
  ) {
    val captchaInfoAsync by viewModel.captchaInfoToShow
    var height by remember { mutableStateOf(160.dp) }

    BoxWithConstraints(
      modifier = Modifier
        .wrapContentHeight()
        .height(height)
    ) {
      val size = with(LocalDensity.current) {
        remember(key1 = maxWidth, key2 = maxHeight) {
          IntSize(maxWidth.toPx().toInt(), maxHeight.toPx().toInt())
        }
      }

      if (size != IntSize.Zero) {
        val captchaInfo = when (val cia = captchaInfoAsync) {
          AsyncData.NotInitialized,
          AsyncData.Loading -> {
            KurobaComposeProgressIndicator()
            null
          }
          is AsyncData.Error -> {
            val error = cia.throwable
            KurobaComposeErrorMessage(
              error = error,
              modifier = Modifier.fillMaxSize()
            )

            null
          }
          is AsyncData.Data -> cia.data
        }

        if (captchaInfo != null) {
          if (captchaInfo.isNoopChallenge()) {
            Box(modifier = Modifier
              .fillMaxWidth()
              .height(128.dp)
              .align(Alignment.Center)
            ) {
              KurobaComposeText(
                text = stringResource(id = R.string.captcha_layout_verification_not_required),
                textAlign = TextAlign.Center,
                modifier = Modifier
                  .fillMaxWidth()
              )
            }
          } else {
            if (captchaInfo.needSlider() && sliderCaptchaGridMode) {
              height = 320.dp
              BuildCaptchaImageGridMode(captchaInfo)
            } else {
              height = 160.dp
              BuildCaptchaImageNormal(captchaInfo, size)
            }
          }
        }
      }
    }
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun BuildCaptchaImageGridMode(
    captchaInfo: Chan4CaptchaLayoutViewModel.CaptchaInfo
  ) {
    val widthDiff = captchaInfo.widthDiff()
      ?: return
    val chanTheme = LocalChanTheme.current
    val totalHorizWidth = widthDiff.times(3)
    val horizOffset = totalHorizWidth / 2
    val imagesToShow = 30
    val slideStep = totalHorizWidth / imagesToShow
    val density = LocalDensity.current
    val lazyListState = rememberLazyListState()

    LazyVerticalGrid(
      state = lazyListState,
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .simpleVerticalScrollbar(lazyListState, chanTheme),
      cells = GridCells.Adaptive(minSize = 160.dp),
      content = {
        items(
          count = imagesToShow,
          itemContent = { index ->
            val currentXOffset = (index * slideStep) - horizOffset
            val bgBitmapPainter = captchaInfo.bgBitmapPainter!!
            val imgBitmapPainter = captchaInfo.imgBitmapPainter!!
            val offset = remember(key1 = currentXOffset) { IntOffset(x = currentXOffset, y = 0) }

            BoxWithConstraints(
              modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(all = 2.dp)
                .clipToBounds()
            ) {
              val scale = with(density) {
                 Math.min(
                  maxWidth.toPx() / imgBitmapPainter.intrinsicSize.width,
                  maxHeight.toPx() / imgBitmapPainter.intrinsicSize.height
                )
              }

              val contentScale = Scale(scale)

              Image(
                modifier = Modifier
                  .fillMaxSize()
                  .offset { offset },
                painter = bgBitmapPainter,
                contentScale = contentScale,
                contentDescription = null,
              )

              Image(
                modifier = Modifier
                  .fillMaxSize(),
                painter = imgBitmapPainter,
                contentScale = contentScale,
                contentDescription = null
              )
            }
          })
      }
    )
  }

  @Composable
  private fun BuildCaptchaImageNormal(
    captchaInfo: Chan4CaptchaLayoutViewModel.CaptchaInfo,
    size: IntSize
  ) {
    val imgBitmapPainter = captchaInfo.imgBitmapPainter!!

    val scale = Math.min(
      size.width.toFloat() / imgBitmapPainter.intrinsicSize.width,
      size.height.toFloat() / imgBitmapPainter.intrinsicSize.height
    )

    val contentScale = Scale(scale)
    var scrollValue by captchaInfo.sliderValue

    if (captchaInfo.bgBitmapPainter != null) {
      val bgBitmapPainter = captchaInfo.bgBitmapPainter
      val offset = remember(key1 = scrollValue) {
        val xOffset = (captchaInfo.bgInitialOffset + MIN_OFFSET + (scrollValue * MAX_OFFSET * -1f)).toInt()
        IntOffset(x = xOffset, y = 0)
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

    val scrollState = rememberScrollableState { delta ->
      var newScrollValue = scrollValue + ((delta * 2f) / size.width.toFloat())

      if (newScrollValue < 0f) {
        newScrollValue = 0f
      } else if (newScrollValue > 1f) {
        newScrollValue = 1f
      }

      scrollValue = newScrollValue

      return@rememberScrollableState delta
    }

    Image(
      modifier = Modifier
        .fillMaxSize()
        .scrollable(state = scrollState, orientation = Orientation.Horizontal),
      painter = captchaInfo.imgBitmapPainter,
      contentScale = contentScale,
      contentDescription = null
    )
  }

  private fun verifyCaptcha(
    captchaInfo: Chan4CaptchaLayoutViewModel.CaptchaInfo?,
    currentInputValue: String
  ) {
    if (captchaInfo == null) {
      return
    }

    val challenge = captchaInfo.challenge
    val solution = CaptchaSolution.ChallengeWithSolution(
      challenge = challenge,
      solution = currentInputValue
    )

    val ttl = captchaInfo.ttlMillis()
    if (ttl <= 0L) {
      showToast(context, R.string.captcha_layout_captcha_already_expired)
      return
    }

    captchaHolder.addNewSolution(solution, ttl)
    callback?.onAuthenticationComplete()

    viewModel.resetCaptchaForced(chanDescriptor)
  }

  private fun showCaptchaHelp() {
    dialogFactory.createSimpleInformationDialog(
      context = context,
      titleText = getString(R.string.captcha_layout_help_title),
      descriptionText = getString(R.string.captcha_layout_help_text)
    )
  }

  private fun showChan4CaptchaSettings() {
    val chan4CaptchaSettings = viewModel.chan4CaptchaSettingsJson.get()
    val items = mutableListOf<FloatingListMenuItem>()

    items += CheckableFloatingListMenuItem(
      ACTION_USE_CONTRAST_BACKGROUND,
      getString(R.string.captcha_layout_contrast_bg_slider_captcha),
      isCurrentlySelected = chan4CaptchaSettings.sliderCaptchaUseContrastBackground
    )

    items += CheckableFloatingListMenuItem(
      ACTION_REMEMBER_CAPTCHA_COOKIES,
      getString(R.string.captcha_layout_remember_captcha_cookies),
      isCurrentlySelected = chan4CaptchaSettings.rememberCaptchaCookies
    )

    items += CheckableFloatingListMenuItem(
      ACTION_SLIDER_CAPTCHA_GRID_MODE,
      getString(R.string.captcha_layout_slider_captcha_alternative_ui),
      isCurrentlySelected = chan4CaptchaSettings.sliderCaptchaGridMode
    )

    items += FloatingListMenuItem(
      ACTION_SHOW_CAPTCHA_HELP,
      getString(R.string.captcha_layout_show_captcha_help)
    )

    val floatingListMenuController = FloatingListMenuController(
      context = context,
      constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      items = items,
      itemClickListener = { clickedMenuItem ->
        when (val itemId = clickedMenuItem.key as Int) {
          ACTION_USE_CONTRAST_BACKGROUND -> {
            val settings = viewModel.chan4CaptchaSettingsJson.get()
            val updatedSettings = settings
              .copy(sliderCaptchaUseContrastBackground = settings.sliderCaptchaUseContrastBackground.not())

            viewModel.chan4CaptchaSettingsJson.set(updatedSettings)
            showToast(context, R.string.captcha_layout_reload_captcha)
          }
          ACTION_SHOW_CAPTCHA_HELP -> {
            showCaptchaHelp()
          }
          ACTION_REMEMBER_CAPTCHA_COOKIES -> {
            val setting = viewModel.chan4CaptchaSettingsJson.get()
            val updatedSetting = setting.copy(rememberCaptchaCookies = setting.rememberCaptchaCookies.not())

            viewModel.chan4CaptchaSettingsJson.set(updatedSetting)
          }
          ACTION_SLIDER_CAPTCHA_GRID_MODE -> {
            val setting = viewModel.chan4CaptchaSettingsJson.get()
            val updatedSetting = setting.copy(sliderCaptchaGridMode = setting.sliderCaptchaGridMode.not())

            viewModel.chan4CaptchaSettingsJson.set(updatedSetting)
            showToast(context, R.string.captcha_layout_reload_captcha)
          }
        }
      }
    )

    presentControllerFunc(floatingListMenuController)
  }

  class Scale(
    private val scale: Float
  ) : ContentScale {
    override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
      return ScaleFactor(scale, scale)
    }
  }
  
  companion object {
    private const val MIN_OFFSET = 100f
    private const val MAX_OFFSET = 400f

    private const val MIN_SLIDE_STEPS = 25
    private const val PIXELS_PER_STEP = 50

    private const val ACTION_USE_CONTRAST_BACKGROUND = 0
    private const val ACTION_SHOW_CAPTCHA_HELP = 1
    private const val ACTION_REMEMBER_CAPTCHA_COOKIES = 2
    private const val ACTION_SLIDER_CAPTCHA_GRID_MODE = 3
  }

}