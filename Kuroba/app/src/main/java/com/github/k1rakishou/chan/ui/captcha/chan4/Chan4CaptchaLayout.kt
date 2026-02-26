package com.github.k1rakishou.chan.ui.captcha.chan4

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.compose.AsyncUiData
import com.github.k1rakishou.chan.core.concurrency.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.image.ImageLoaderDeprecated
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutCallback
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutInterface
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder
import com.github.k1rakishou.chan.ui.captcha.CaptchaSolution
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeClickableIcon
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.providers.ComposeEntrypoint
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.scaffold.FloatingListScaffoldBuilder
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayout
import com.github.k1rakishou.chan.ui.view.floating_menu.CheckableFloatingListMenuItem
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.chan.utils.IHasViewModelScope
import com.github.k1rakishou.chan.utils.ViewModelScope
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.requireComponentActivity
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import javax.inject.Inject

@SuppressLint("ViewConstructor")
class Chan4CaptchaLayout(
  context: Context,
  private val chanDescriptor: ChanDescriptor,
  private val presentControllerFunc: (Controller) -> Unit
) : TouchBlockingFrameLayout(context),
  AuthenticationLayoutInterface,
  IHasViewModelScope {

  @Inject
  lateinit var captchaHolder: CaptchaHolder
  @Inject
  lateinit var imageLoaderDeprecated: ImageLoaderDeprecated
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var appResources: AppResources

  private val viewModel by viewModelByKey<Chan4CaptchaLayoutViewModel>()
  private val scope = KurobaCoroutineScope()

  private var siteDescriptor: SiteDescriptor? = null
  private var siteAuthentication: SiteAuthentication? = null
  private var callback: AuthenticationLayoutCallback? = null

  override val viewModelScope: ViewModelScope
    get() = ViewModelScope.ActivityScope(context.requireComponentActivity())

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
        ComposeEntrypoint {
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

    viewModel.onCaptchaViewInitialized()
  }

  override fun reset() {
    hardReset()
  }

  override fun hardReset() {
    viewModel.requestCaptcha(
      chanDescriptor = chanDescriptor,
      mcl = "",
      forced = false
    )
  }

  override fun onDestroy() {
    this.siteAuthentication = null
    this.callback = null

    scope.cancelChildren()

    viewModel.resetCaptchaIfCaptchaIsAlmostDead(chanDescriptor)
    viewModel.onCaptchaViewDestroyed()
  }

  @Composable
  private fun BoxScope.BuildContent() {
    val scrollState = rememberScrollState()

    with(FloatingListScaffoldBuilder()) {
      Content(
        boxScope = this@BuildContent,
        scrollState = scrollState,
        header = {
          val captchaTtlMillis by viewModel.captchaTtlMillisFlow.collectAsState()
          val captchaInfoAsync by viewModel.captchaInfoToShow

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            if (captchaTtlMillis >= 0L) {
              KurobaComposeText(
                modifier = Modifier
                  .wrapContentWidth()
                  .padding(vertical = 4.dp),
                text = "Captcha TTL: ${captchaTtlMillis / 1000L} sec",
                fontSize = 14.ktu
              )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (captchaInfoAsync is AsyncUiData.UiData) {
              KurobaComposeClickableIcon(
                drawableId = R.drawable.ic_help_outline_white_24dp,
                onClick = {
                  val title = appResources.string(R.string.captcha_4chan_updates_title)
                  val description = buildString {
                    appendLine(appResources.string(R.string.captcha_4chan_update_26_02_2026))
                  }

                  dialogFactory.createSimpleInformationDialog(
                    context = context,
                    titleText = title,
                    descriptionText = description
                  )
                }
              )
            }
          }
        },
        body = { paddingValues ->
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .wrapContentHeight()
              .verticalScroll(scrollState)
          ) {
            Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding()))
            BuildCaptchaWindow()
            Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding()))
          }
        },
        footer = {
          BuildCaptchaWindowFooter()
        }
      )
    }
  }

  @Composable
  private fun BuildCaptchaWindow() {
    Spacer(modifier = Modifier.height(8.dp))

    Box(
      modifier = Modifier
        .heightIn(min = 64.dp)
    ) {
      BuildCaptchaImageRows()
    }

    Spacer(modifier = Modifier.height(8.dp))
  }

  @Composable
  private fun BuildCaptchaImageRows() {
    val chanTheme = LocalChanTheme.current
    val captchaInfoAsync by viewModel.captchaInfoToShow

    val captchaInfo = when (val captchaInfo = captchaInfoAsync) {
      is AsyncUiData.UiData<Chan4CaptchaLayoutViewModel.CaptchaInfo> -> captchaInfo
      is AsyncUiData.Error -> {
        KurobaComposeErrorMessage(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
          error = captchaInfo.throwable
        )
        return
      }
      AsyncUiData.Loading -> {
        KurobaComposeProgressIndicator(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        )
        return
      }
      AsyncUiData.NotInitialized -> {
        return
      }
    }

    val tasks = captchaInfo.data.tasks

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        if (captchaInfo.data.isNoopChallenge()) {
          VerificationNotRequired()
        } else {
          for ((taskIndex, task) in tasks.withIndex()) {
            if (taskIndex > 0) {
              Spacer(modifier = Modifier.height(12.dp))
            }

            Column(
              modifier = Modifier
                .fillMaxWidth()
                .background(chanTheme.backColorSecondaryCompose)
                .padding(all = 8.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
              ) {
                KurobaComposeText(
                  text = "#${taskIndex + 1}",
                  color = chanTheme.textColorHintCompose
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                ) {
                  CaptchaTaskTitle(task.title)
                }
              }

              Spacer(
                modifier = Modifier
                  .wrapContentWidth()
                  .height(8.dp)
              )

              FlowRow(
                modifier = Modifier
                  .fillMaxWidth()
                  .wrapContentHeight(),
                horizontalArrangement = Arrangement.spacedBy(
                  space = 4.dp,
                  alignment = Alignment.CenterHorizontally
                ),
                verticalArrangement = Arrangement.spacedBy(space = 10.dp),
                maxItemsInEachRow = if (task.hasWideImages) 2 else Int.MAX_VALUE
              ) {
                for ((imageIndex, taskImage) in task.images.withIndex()) {
                  val aspectRatio = taskImage.imageBitmap.width.toFloat() / taskImage.imageBitmap.height.toFloat()
                  val isWideImage = aspectRatio > 1.5f

                  val scale by animateFloatAsState(targetValue = if (taskImage.isSelected) 0.8f else 1.0f)

                  Image(
                    modifier = Modifier
                      .background(chanTheme.backColorCompose)
                      .weight(if (isWideImage) 1f else 0.5f)
                      .aspectRatio(aspectRatio)
                      .kurobaClickable(
                        bounded = true,
                        onClick = { viewModel.onCaptchaImageClicked(taskIndex, imageIndex) }
                      )
                      .scale(scale)
                      .drawWithContent {
                        drawContent()

                        if (taskImage.isSelected) {
                          drawRect(
                            color = chanTheme.accentColorCompose,
                            style = Stroke(
                              width = 12.0f,
                              pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 24f), 0f)
                            )
                          )
                        }

                        if (task.isNotLikeTheOthersTaskType()) {
                          val sliderWidth = this.size.width
                          val sliderHeight = this.size.height
                          val sliderThumbSize = 6.dp.toPx()

                          val thumbOffsetStep = (sliderWidth - (sliderThumbSize * 2)) / (task.images.size).toFloat()
                          val thumbOffset = ((imageIndex + 1) * thumbOffsetStep) + sliderThumbSize

                          drawCircle(
                            color = if (ThemeEngine.isDarkColor(chanTheme.accentColorCompose)) {
                              Color.White
                            } else {
                              Color.Black
                            },
                            center = Offset(x = thumbOffset, y = sliderHeight),
                            radius = sliderThumbSize + 1.dp.toPx()
                          )

                          drawCircle(
                            color = chanTheme.accentColorCompose,
                            center = Offset(x = thumbOffset, y = sliderHeight),
                            radius = sliderThumbSize
                          )
                        }
                      },
                    bitmap = taskImage.imageBitmap,
                    contentDescription = "Captcha task image"
                  )
                }

                if (task.images.size % 2 != 0 && task.hasWideImages) {
                  Spacer(modifier = Modifier.weight(1f))
                }
              }
            }
          }
        }
      }
    }
  }

  @Composable
  private fun VerificationNotRequired() {
    val text = stringResource(id = R.string.captcha_layout_verification_not_required)

    val annotatedText = remember {
      buildAnnotatedString {
        pushStyle(
          SpanStyle(
            fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline
          )
        )

        append(text)
      }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(42.dp),
      contentAlignment = Alignment.Center
    ) {
      KurobaComposeText(
        text = annotatedText,
        fontSize = 18.ktu
      )
    }
  }

  @Composable
  private fun ColumnScope.CaptchaTaskTitle(title: Chan4CaptchaTitleFormatter.Title?) {
    val chanTheme = LocalChanTheme.current

    when (title) {
      is Chan4CaptchaTitleFormatter.Title.Image -> {
        val ratio = run {
          if (title.image.height == 0) {
            return@run 1f
          }

          return@run title.image.width.toFloat() / title.image.height.toFloat()
        }

        Image(
          modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .heightIn(min = 42.dp, max = 120.dp)
            .aspectRatio(ratio = ratio),
          bitmap = title.image,
          contentDescription = null
        )

        Spacer(modifier = Modifier.height(8.dp))

        KurobaComposeText(text = "Ignore the \"scrollbar\"/\"click Next\" parts and just click " +
          "one of the images containing the element mentioned above")
      }
      is Chan4CaptchaTitleFormatter.Title.TextWithImage -> {
        if (title.images.isEmpty()) {
          KurobaComposeText(text = title.annotated)
          return
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
          verticalAlignment = Alignment.CenterVertically
        ) {
          KurobaComposeText(
            modifier = Modifier.weight(1f),
            text = title.annotated
          )

          title.images.forEach { imageBitmap ->
            Spacer(modifier = Modifier.width(4.dp))

            val ratio = imageBitmap.width.toFloat() / imageBitmap.height.toFloat()

            Image(
              modifier = Modifier
                .aspectRatio(ratio = ratio)
                .widthIn(min = 52.dp)
                .border(width = 2.dp, color = chanTheme.accentColorCompose),
              bitmap = imageBitmap,
              contentDescription = null
            )
          }

          Spacer(modifier = Modifier.width(4.dp))
        }
      }
      null -> {
        KurobaComposeText(text = "Failed to parse captcha task title, time to guess ;)")
      }
    }
  }

  @Composable
  private fun BuildCaptchaWindowFooter() {
    val chanTheme = LocalChanTheme.current
    val captchaInfoAsync by viewModel.captchaInfoToShow
    val captchaDataJson by viewModel.captchaDataJson
    val captchaInfo = (captchaInfoAsync as? AsyncUiData.UiData)?.data

    Row(
      horizontalArrangement = Arrangement.End,
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .background(chanTheme.backColorCompose)
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
        onClick = {
          viewModel.requestCaptcha(
            chanDescriptor = chanDescriptor,
            mcl = "",
            forced = true
          )
        }
      )

      Spacer(modifier = Modifier.width(8.dp))

      KurobaComposeClickableIcon(
        modifier = Modifier
          .padding(8.dp)
          .width(28.dp)
          .height(28.dp),
        drawableId = R.drawable.ic_baseline_content_copy_24,
        enabled = captchaDataJson != null,
        onClick = {
          captchaDataJson?.let { captchaInfoJson ->
            AndroidUtils.setClipboardContent("captcha_json", captchaInfoJson)
            showToast(context, "Captcha json copied to clipboard")
          }
        }
      )

      Spacer(modifier = Modifier.weight(1f))

      run {
        val buttonTextId = if (captchaInfo?.isNoopChallenge() == true) {
          R.string.send
        } else {
          R.string.captcha_layout_verify
        }

        KurobaComposeTextBarButton(
          onClick = { verifyCaptcha(captchaInfo) },
          enabled = captchaInfo != null && (captchaInfo.isFilledIn() || captchaInfo.isNoopChallenge()),
          text = stringResource(id = buttonTextId)
        )
      }

      Spacer(modifier = Modifier.width(8.dp))
    }
  }

  private fun verifyCaptcha(
    captchaInfo: Chan4CaptchaLayoutViewModel.CaptchaInfo?,
  ) {
    if (captchaInfo == null) {
      return
    }

    val challenge = captchaInfo.challenge
    val uuid = captchaHolder.generateCaptchaUuid()

    val solution = CaptchaSolution.ChallengeWithSolution(
      uuid = uuid,
      challenge = challenge,
      solution = captchaInfo.solution()
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
      key = ACTION_REMEMBER_CAPTCHA_COOKIES,
      name = getString(R.string.captcha_layout_remember_captcha_cookies),
      checked = chan4CaptchaSettings.rememberCaptchaCookies
    )

    items += FloatingListMenuItem(
      key = ACTION_SHOW_CAPTCHA_HELP,
      name = getString(R.string.captcha_layout_show_captcha_help)
    )

    val floatingListMenuController = FloatingListMenuController(
      context = context,
      constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      items = items,
      itemClickListener = { clickedMenuItem ->
        when (val itemId = clickedMenuItem.key as Int) {
          ACTION_SHOW_CAPTCHA_HELP -> {
            showCaptchaHelp()
          }
          ACTION_REMEMBER_CAPTCHA_COOKIES -> {
            val setting = viewModel.chan4CaptchaSettingsJson.get()
            val updatedSetting = setting.copy(rememberCaptchaCookies = setting.rememberCaptchaCookies.not())

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
    private const val ACTION_SHOW_CAPTCHA_HELP = 1
    private const val ACTION_REMEMBER_CAPTCHA_COOKIES = 2
  }

}