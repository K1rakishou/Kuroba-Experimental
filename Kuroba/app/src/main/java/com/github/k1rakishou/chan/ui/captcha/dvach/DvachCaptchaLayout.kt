package com.github.k1rakishou.chan.ui.captcha.dvach

import android.content.Context
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.scale
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.core.site.sites.dvach.DvachPuzzleSolution
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
              .heightIn(min = 300.dp)
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
        val uuid = captchaHolder.generateCaptchaUuid()

        val solution = CaptchaSolution.ChallengeWithSolution(
          uuid = uuid,
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
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
    ) {
      val captchaInfoAsync by viewModel.captchaInfoToShow

      when (captchaInfoAsync) {
        AsyncData.NotInitialized-> {
          // no-op
        }
        AsyncData.Loading -> {
          KurobaComposeProgressIndicator(
            modifier = Modifier
              .fillMaxWidth()
              .height(300.dp)
          )
        }
        is AsyncData.Error -> {
          val error = (captchaInfoAsync as AsyncData.Error).throwable
          KurobaComposeErrorMessage(
            error = error,
            modifier = Modifier
              .fillMaxWidth()
              .height(300.dp)
          )
        }
        is AsyncData.Data -> {
          when (val captchaInfo = (captchaInfoAsync as AsyncData.Data).data) {
            is DvachCaptchaLayoutViewModel.CaptchaInfo.Puzzle -> {
              PuzzleBasedCaptchaImage(captchaInfo)
            }
            is DvachCaptchaLayoutViewModel.CaptchaInfo.Text -> {
              TextBaseCaptchaImage(captchaInfo, onReloadClick)
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      when (val captchaInfo = (captchaInfoAsync as? AsyncData.Data)?.data) {
        is DvachCaptchaLayoutViewModel.CaptchaInfo.Puzzle -> {
          CaptchaPuzzleBasedFooter(
            viewModel = viewModel,
            captchaInfo = captchaInfo,
            onVerifyClick = onVerifyClick,
            onReloadClick = onReloadClick
          )
        }
        is DvachCaptchaLayoutViewModel.CaptchaInfo.Text -> {
          CaptchaTextBasedFooter(
            viewModel = viewModel,
            captchaInfo = captchaInfo,
            onVerifyClick = onVerifyClick,
            onReloadClick = onReloadClick
          )
        }
        null -> {
          // no-op
        }
      }
    }
  }

  @Composable
  private fun PuzzleBasedCaptchaImage(
    captchaInfo: DvachCaptchaLayoutViewModel.CaptchaInfo.Puzzle
  ) {
    val (imageWidth, imageHeight) = remember { captchaInfo.image.width to captchaInfo.image.height }
    val (puzzleWidth, puzzleHeight) = remember { captchaInfo.puzzle.width to captchaInfo.puzzle.height }

    var initialTouchHappened by remember { mutableStateOf(false) }
    var currentSize by remember { mutableStateOf(IntSize.Zero) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragInProgress by remember { mutableStateOf(false) }

    val scaleX = if (currentSize != IntSize.Zero) {
      (currentSize.width.toFloat() / imageWidth.toFloat())
    } else {
      0f
    }

    val scaleY = if (currentSize != IntSize.Zero) {
      (currentSize.height.toFloat() / imageHeight.toFloat())
    } else {
      0f
    }

    val imagePainter = remember { BitmapPainter(captchaInfo.image.asImageBitmap()) }
    val puzzlePainter = remember(scaleX, scaleY) {
      if (scaleX <= 0f || scaleY <= 0f) {
        return@remember BitmapPainter(captchaInfo.puzzle.asImageBitmap())
      }

      val scaledBitmap = captchaInfo.puzzle.scale(
        width = (captchaInfo.puzzle.width * scaleX).toInt(),
        height = (captchaInfo.puzzle.height * scaleY).toInt()
      )

      return@remember BitmapPainter(scaledBitmap.asImageBitmap())
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
    ) {
      Image(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(
            ratio = imageWidth.toFloat() / imageHeight.toFloat(),
            matchHeightConstraintsFirst = false
          )
          .onSizeChanged { newSize ->
            if (currentSize == IntSize.Zero && newSize != IntSize.Zero) {
              dragOffset = Offset(
                x = (newSize.width - puzzleWidth) / 2f,
                y = (newSize.height - puzzleHeight) / 2f
              )
            }

            currentSize = newSize
          }
          .pointerInput(key1 = scaleX, key2 = scaleY) {
            detectDragGestures(
              onDragStart = {
                initialTouchHappened = true
                isDragInProgress = true
              },
              onDrag = { _, offset ->
                val puzzleScaledWidth = captchaInfo.puzzle.width * scaleX
                val puzzleScaledHeight = captchaInfo.puzzle.height * scaleY

                val newDragOffset = dragOffset + offset
                val offsetX = newDragOffset.x.coerceIn(0f, currentSize.width.toFloat() - puzzleScaledWidth)
                val offsetY = newDragOffset.y.coerceIn(0f, currentSize.height.toFloat() - (puzzleScaledHeight / 2f))

                dragOffset = Offset(offsetX, offsetY)
              },
              onDragEnd = {
                isDragInProgress = false

                if (scaleX > 0f && scaleY > 0f) {
                  val puzzlePieceRealOffset = Offset(x = dragOffset.x / scaleX, y = dragOffset.y / scaleY)
                  viewModel.currentPuzzlePieceOffsetValue.value = puzzlePieceRealOffset
                }
              }
            )
          }
          .drawWithContent {
            drawContent()

            if (!initialTouchHappened) {
              drawRect(color = Color.Black.copy(alpha = 0.8f))
            }
          },
        painter = imagePainter,
        contentDescription = "Puzzle background image"
      )

      Image(
        modifier = Modifier
          .wrapContentSize()
          .offset { IntOffset(dragOffset.x.toInt(), dragOffset.y.toInt()) }
          .graphicsLayer {
            alpha = if (isDragInProgress) 0.7f else 1f
          },
        painter = puzzlePainter,
        contentDescription = "Puzzle foreground image"
      )
    }
  }

  @Composable
  private fun TextBaseCaptchaImage(
      captchaInfo: DvachCaptchaLayoutViewModel.CaptchaInfo.Text,
      onReloadClick: () -> Unit
  ) {
    val requestFullUrl = remember { captchaInfo.fullRequestUrl(siteManager = siteManager) }
    if (requestFullUrl == null) {
      return
    }

    val request = remember(key1 = requestFullUrl) {
      val data = ImageLoaderRequestData.Url(
        httpUrl = requestFullUrl,
        cacheFileType = CacheFileType.Other
      )

      ImageLoaderRequest(data = data)
    }

    KurobaComposeImage(
      modifier = Modifier
        .fillMaxWidth()
        .height(160.dp)
        .clickable { onReloadClick() },
      request = request,
      imageLoaderV2 = imageLoaderV2,
      loading = { KurobaComposeProgressIndicator() },
      error = { throwable -> KurobaComposeErrorMessage(error = throwable) }
    )
  }

  @Composable
  private fun ColumnScope.CaptchaPuzzleBasedFooter(
    viewModel: DvachCaptchaLayoutViewModel,
    captchaInfo: DvachCaptchaLayoutViewModel.CaptchaInfo.Puzzle,
    onVerifyClick: (String, String) -> Unit,
    onReloadClick: () -> Unit
  ) {
    val currentPuzzlePieceOffsetValue by viewModel.currentPuzzlePieceOffsetValue
    val captchaId = captchaInfo.id

    Spacer(modifier = Modifier.height(16.dp))

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

      val buttonEnabled = currentPuzzlePieceOffsetValue != Offset.Unspecified

      KurobaComposeTextBarButton(
        onClick = {
          if (captchaId.isNotNullNorEmpty() && currentPuzzlePieceOffsetValue != Offset.Unspecified) {
            onVerifyClick(captchaId, DvachPuzzleSolution.encode(currentPuzzlePieceOffsetValue))
          }
        },
        enabled = buttonEnabled,
        text = stringResource(id = R.string.captcha_layout_verify)
      )

      Spacer(modifier = Modifier.width(8.dp))
    }

    Spacer(modifier = Modifier.height(16.dp))
  }

  @Composable
  private fun ColumnScope.CaptchaTextBasedFooter(
    viewModel: DvachCaptchaLayoutViewModel,
    captchaInfo: DvachCaptchaLayoutViewModel.CaptchaInfo.Text,
    onVerifyClick: (String, String) -> Unit,
    onReloadClick: () -> Unit
  ) {
    var currentInputValue by viewModel.currentInputValue
    val captchaId = captchaInfo.id
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