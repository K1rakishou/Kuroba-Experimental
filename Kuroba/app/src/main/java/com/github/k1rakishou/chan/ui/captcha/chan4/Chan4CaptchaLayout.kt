package com.github.k1rakishou.chan.ui.captcha.chan4

import android.annotation.SuppressLint
import android.content.Context
import android.text.SpannableString
import android.text.util.Linkify
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
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
import com.github.k1rakishou.chan.ui.compose.FlowMainAxisAlignment
import com.github.k1rakishou.chan.ui.compose.FlowRow
import com.github.k1rakishou.chan.ui.compose.KurobaComposeCardView
import com.github.k1rakishou.chan.ui.compose.KurobaComposeClickableIcon
import com.github.k1rakishou.chan.ui.compose.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.KurobaComposeSnappingSlider
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.KurobaComposeTextField
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.ProvideChanTheme
import com.github.k1rakishou.chan.ui.compose.kurobaClickable
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayout
import com.github.k1rakishou.chan.ui.view.floating_menu.CheckableFloatingListMenuItem
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.flow.collectLatest
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

    scope.launch {
      viewModel.notifyUserAboutCaptchaSolverErrorFlow.collect { captchaSolverInfo ->
        when (captchaSolverInfo) {
          CaptchaSolverInfo.Installed -> {
            // no-op
          }
          CaptchaSolverInfo.NotInstalled -> {
            val bodyMessage = SpannableString(getString(R.string.captcha_layout_captcha_solver_not_installed_body))
            Linkify.addLinks(bodyMessage, Linkify.WEB_URLS)

            dialogFactory.createSimpleInformationDialog(
              context = context,
              titleText = getString(R.string.captcha_layout_captcha_solver_not_installed_title),
              descriptionText = bodyMessage
            )
          }
          is CaptchaSolverInfo.InstalledVersionMismatch -> {
            val bodyMessage = SpannableString(
              getString(
                R.string.captcha_layout_captcha_solver_version_mismatch_body,
                captchaSolverInfo.expected,
                captchaSolverInfo.actual
              )
            )
            Linkify.addLinks(bodyMessage, Linkify.WEB_URLS)

            dialogFactory.createSimpleInformationDialog(
              context = context,
              titleText = getString(R.string.captcha_layout_captcha_solver_version_mismatch_title),
              descriptionText = bodyMessage
            )
          }
        }
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
    viewModel.requestCaptcha(context, chanDescriptor, forced = false)
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
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .verticalScroll(rememberScrollState())
    ) {
      BuildCaptchaWindow()
    }
  }

  @Composable
  private fun BuildCaptchaWindow() {
    BuildCaptchaWindowImageOrText()

    Spacer(modifier = Modifier.height(8.dp))

    BuildCaptchaWindowSliderOrInput()

    BuildCaptchaWindowFooter()

    Spacer(modifier = Modifier.height(8.dp))
  }

  @Composable
  private fun BuildCaptchaWindowSliderOrInput() {
    val captchaInfoAsync by viewModel.captchaInfoToShow
    val captchaInfo = (captchaInfoAsync as? AsyncData.Data)?.data

    if (captchaInfo == null || captchaInfo.isNoopChallenge()) {
      return
    }

    val chanTheme = LocalChanTheme.current

    var currentInputValue by captchaInfo.currentInputValue
    var prevSolution by remember { mutableStateOf<String?>(null) }

    val currentCaptchaSolution by captchaInfo.captchaSolution
    val scrollValueState = captchaInfo.sliderValue
    
    var captchaSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(
      key1 = Unit,
      block = {
        snapshotFlow { currentCaptchaSolution }
          .collectLatest { captchaSolution ->
            captchaSuggestions = emptyList()

            if (captchaSolution == null) {
              return@collectLatest
            }

            val solution = captchaSolution.solutions.firstOrNull()

            if (solution == null || solution.isEmpty()) {
              showToast(context, R.string.captcha_layout_failed_to_find_solution)
              return@collectLatest
            }

            if (solution == prevSolution) {
              return@collectLatest
            }

            if (captchaSolution.solutions.size > 1) {
              val duplicates = mutableSetOf<String>()
              val actualSuggestions = mutableListOf<String>()

              for (suggestion in captchaSolution.solutions) {
                if (duplicates.add(suggestion)) {
                  actualSuggestions += suggestion
                }

                if (actualSuggestions.size >= 10) {
                  break
                }
              }

              if (actualSuggestions.isNotEmpty()) {
                captchaSuggestions = actualSuggestions
              }
            }

            prevSolution = solution
            currentInputValue = solution

            captchaSolution.sliderOffset?.let { sliderOffset ->
              scrollValueState.value = sliderOffset.coerceIn(0f, 1f)
            }
          }
      })

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
    
    if (captchaSuggestions.isNotEmpty()) {
      Spacer(modifier = Modifier.height(8.dp))
      
      CaptchaSuggestions(
        currentInputValue = currentInputValue,
        captchaSuggestions = captchaSuggestions,
        onSuggestionClicked = { clickedSuggestion -> currentInputValue = clickedSuggestion }
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (captchaInfo.needSlider()) {
      BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        KurobaComposeSnappingSlider(
          slideOffsetState = scrollValueState,
          slideSteps = SLIDE_STEPS,
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

  @Composable
  private fun CaptchaSuggestions(
    currentInputValue: String,
    captchaSuggestions: List<String>,
    onSuggestionClicked: (String) -> Unit
  ) {
    FlowRow(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .padding(horizontal = 16.dp),
      mainAxisAlignment = FlowMainAxisAlignment.Center,
      mainAxisSpacing = 4.dp,
      crossAxisSpacing = 4.dp
    ) {
      for (captchaSuggestion in captchaSuggestions) {
        key(captchaSuggestion) {
          CaptchaSuggestion(
            currentInputValue = currentInputValue,
            captchaSuggestion = captchaSuggestion,
            onSuggestionClicked = onSuggestionClicked
          )
        }
      }
    }
  }

  @Composable
  private fun CaptchaSuggestion(
    currentInputValue: String,
    captchaSuggestion: String,
    onSuggestionClicked: (String) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current

    Row(
      modifier = Modifier.wrapContentSize()
    ) {
      val bgColor = remember(key1 = currentInputValue, key2 = captchaSuggestion) {
        if (currentInputValue.equals(captchaSuggestion, ignoreCase = true)) {
          chanTheme.accentColorCompose
        } else {
          chanTheme.backColorSecondaryCompose
        }
      }

      val textColor = remember(key1 = bgColor) {
        if (ThemeEngine.isDarkColor(bgColor)) {
          Color.White
        } else {
          Color.Black
        }
      }

      KurobaComposeCardView(
        modifier = Modifier
          .wrapContentSize()
          .kurobaClickable(bounded = true, onClick = { onSuggestionClicked(captchaSuggestion) }),
        backgroundColor = bgColor,
        shape = remember { RoundedCornerShape(4.dp) }
      ) {
        Text(
          modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp),
          text = captchaSuggestion,
          color = textColor,
          fontSize = 18.sp
        )
      }
    }
  }

  @Composable
  private fun BuildCaptchaWindowFooter() {
    val captchaInfoAsync by viewModel.captchaInfoToShow
    val solvingInProgress by viewModel.solvingInProgress
    val captchaSolverInstalled by viewModel.captchaSolverInstalled
    val captchaInfo = (captchaInfoAsync as? AsyncData.Data)?.data

    Row(
      horizontalArrangement = Arrangement.End,
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
    ) {
      KurobaComposeClickableIcon(
        modifier = Modifier
          .padding(8.dp)
          .width(28.dp)
          .height(28.dp),
        drawableId = R.drawable.ic_settings_white_24dp,
        onClick = { showChan4CaptchaSettings() }
      )

      Spacer(modifier = Modifier.width(8.dp))

      KurobaComposeClickableIcon(
        modifier = Modifier
          .padding(8.dp)
          .width(28.dp)
          .height(28.dp),
        drawableId = R.drawable.ic_refresh_white_24dp,
        enabled = !solvingInProgress,
        onClick = { viewModel.requestCaptcha(context, chanDescriptor, forced = true) }
      )

      Spacer(modifier = Modifier.width(8.dp))

      KurobaComposeClickableIcon(
        modifier = Modifier
          .padding(8.dp)
          .width(28.dp)
          .height(28.dp),
        drawableId = R.drawable.ic_baseline_content_copy_24,
        enabled = captchaInfo?.captchaInfoRawString != null,
        onClick = {
          captchaInfo?.captchaInfoRawString?.let { captchaInfoJson ->
            AndroidUtils.setClipboardContent("captcha_json", captchaInfoJson)
            showToast(context, "Captcha json copied to clipboard")
          }
        }
      )

      Spacer(modifier = Modifier.weight(1f))

      KurobaComposeTextBarButton(
        onClick = {
          if (captchaInfo?.captchaInfoRawString != null) {
            viewModel.solveCaptcha(
              context = context,
              captchaInfoRawString = captchaInfo.captchaInfoRawString,
              sliderOffset = captchaInfo.sliderValue.value
            )
          }
        },
        text = stringResource(id = R.string.captcha_layout_solve),
        enabled = !solvingInProgress &&
          captchaSolverInstalled &&
          captchaInfo != null &&
          captchaInfo.captchaInfoRawString != null
      )

      Spacer(modifier = Modifier.width(8.dp))

      KurobaComposeTextBarButton(
        onClick = {
          val currentInputValue = captchaInfo?.currentInputValue
            ?: return@KurobaComposeTextBarButton

          verifyCaptcha(captchaInfo, currentInputValue.value)
        },
        enabled = captchaInfo != null
          && (captchaInfo.isNoopChallenge() || captchaInfo.currentInputValue.value.isNotEmpty())
          && !solvingInProgress,
        text = stringResource(id = R.string.captcha_layout_verify)
      )

      Spacer(modifier = Modifier.width(8.dp))
    }
  }

  @Composable
  private fun BuildCaptchaWindowImageOrText() {
    val captchaInfoAsync by viewModel.captchaInfoToShow

    BoxWithConstraints(
      modifier = Modifier.wrapContentSize()
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
            KurobaComposeProgressIndicator(
              modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
            )

            null
          }
          is AsyncData.Error -> {
            val error = cia.throwable
            KurobaComposeErrorMessage(
              modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
              error = error
            )

            null
          }
          is AsyncData.Data -> cia.data
        }

        if (captchaInfo != null) {
          if (captchaInfo.isNoopChallenge()) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
                .align(Alignment.Center)
                .padding(vertical = 16.dp)
            ) {
              KurobaComposeText(
                text = stringResource(id = R.string.captcha_layout_verification_not_required),
                textAlign = TextAlign.Center,
                modifier = Modifier
                  .fillMaxWidth()
              )
            }
          } else {
            BuildCaptchaImageNormal(captchaInfo, size)
          }
        }
      }
    }
  }

  @Composable
  private fun BuildCaptchaImageNormal(
    captchaInfo: Chan4CaptchaLayoutViewModel.CaptchaInfo,
    size: IntSize
  ) {
    val density = LocalDensity.current

    val width = captchaInfo.imgBitmap!!.width
    val height = captchaInfo.imgBitmap.height
    val th = 80
    val pw = 16
    val canvasScale = (th / height)
    val canvasHeight = th
    val canvasWidth = width * canvasScale + pw * 2

    val scale = Math.min(size.width.toFloat() / width, size.height.toFloat() / height)
    val canvasWidthDp = with(density) { (canvasWidth * scale).toDp() }
    val canvasHeightDp = with(density) { (canvasHeight * scale).toDp() }

    val scrollValue by captchaInfo.sliderValue
    val captchaTtlMillis by viewModel.captchaTtlMillisFlow.collectAsState()

    Box {
      Canvas(
        modifier = Modifier
          .size(canvasWidthDp, canvasHeightDp)
          .clipToBounds(),
        onDraw = {
          val canvas = drawContext.canvas.nativeCanvas

          canvas.withScale(x = scale, y = scale) {
            drawRect(Color(0xFFEEEEEE.toInt()))

            if (captchaInfo.bgBitmap != null) {
              canvas.withTranslation(x = (scrollValue * captchaInfo.widthDiff() * -1)) {
                canvas.drawBitmap(captchaInfo.bgBitmap, 0f, 0f, null)
              }
            }

            canvas.drawBitmap(captchaInfo.imgBitmap, 0f, 0f, null)
          }
        }
      )

      if (captchaTtlMillis >= 0L) {
        val bgColor = remember { Color.Black.copy(alpha = 0.6f) }

        KurobaComposeText(
          modifier = Modifier
            .align(Alignment.TopStart)
            .background(bgColor)
            .padding(4.dp),
          text = "${captchaTtlMillis / 1000L} sec",
          color = Color.White,
          fontSize = 12.sp
        )
      }
    }
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
      ACTION_USE_CAPTCHA_SOLVER,
      getString(R.string.captcha_layout_use_captcha_solver),
      isCurrentlySelected = chan4CaptchaSettings.useCaptchaSolver
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
          ACTION_USE_CAPTCHA_SOLVER -> {
            val setting = viewModel.chan4CaptchaSettingsJson.get()
            val updatedSetting = setting.copy(useCaptchaSolver = setting.useCaptchaSolver.not())

            viewModel.chan4CaptchaSettingsJson.set(updatedSetting)
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
    private const val SLIDE_STEPS = 50

    private const val ACTION_USE_CONTRAST_BACKGROUND = 0
    private const val ACTION_SHOW_CAPTCHA_HELP = 1
    private const val ACTION_REMEMBER_CAPTCHA_COOKIES = 2
    private const val ACTION_USE_CAPTCHA_SOLVER = 3
  }

}