package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FloatTweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.github.k1rakishou.chan.ui.compose.KurobaTextUnit
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.utils.detectTapGesturesWithFilter
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.hypot

@Composable
fun KurobaComposeClickableText(
  text: AnnotatedString,
  modifier: Modifier = Modifier,
  color: Color? = null,
  fontSize: KurobaTextUnit = 16.ktu,
  fontWeight: FontWeight? = null,
  maxLines: Int = Int.MAX_VALUE,
  overflow: TextOverflow = TextOverflow.Clip,
  softWrap: Boolean = true,
  textAlign: TextAlign? = null,
  inlineContent: Map<String, InlineTextContent> = mapOf(),
  onTextClicked: (TextLayoutResult, Int) -> Boolean
) {
  val textColorPrimary = if (color == null) {
    val chanTheme = LocalChanTheme.current

    remember(key1 = chanTheme.textColorPrimary) {
      Color(chanTheme.textColorPrimary)
    }
  } else {
    color
  }

  val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

  val pressIndicatorModifier = Modifier.pointerInput(key1 = onTextClicked) {
    forEachGesture {
      awaitPointerEventScope {
        val downPointerInputChange = awaitFirstDown()

        val upOrCancelPointerInputChange = waitForUpOrCancellation()
          ?: return@awaitPointerEventScope

        val result = layoutResult.value
          ?: return@awaitPointerEventScope

        val offset = result.getOffsetForPosition(upOrCancelPointerInputChange.position)

        if (onTextClicked.invoke(result, offset)) {
          downPointerInputChange.consumeAllChanges()
          upOrCancelPointerInputChange.consumeAllChanges()
        }
      }
    }
  }

  ComposeText(
    color = textColorPrimary,
    text = text,
    fontSize = fontSize,
    maxLines = maxLines,
    overflow = overflow,
    softWrap = softWrap,
    textAlign = textAlign,
    fontWeight = fontWeight,
    inlineContent = inlineContent,
    modifier = modifier.then(pressIndicatorModifier),
    onTextLayout = { textLayoutResult -> layoutResult.value = textLayoutResult }
  )
}

@Composable
fun KurobaComposeClickableText(
  modifier: Modifier = Modifier,
  text: AnnotatedString,
  color: Color? = null,
  fontSize: KurobaTextUnit = 16.ktu,
  fontWeight: FontWeight? = null,
  maxLines: Int = Int.MAX_VALUE,
  overflow: TextOverflow = TextOverflow.Clip,
  softWrap: Boolean = true,
  enabled: Boolean = true,
  isTextClickable: Boolean = true,
  textAlign: TextAlign? = null,
  inlineContent: ImmutableMap<String, InlineTextContent> = persistentMapOf(),
  annotationBgColors: ImmutableMap<String, Color> = persistentMapOf(),
  detectClickedAnnotations: (Offset, TextLayoutResult?, AnnotatedString) -> AnnotatedString.Range<String>?,
  onTextAnnotationClicked: (AnnotatedString, Int) -> Unit,
  onTextAnnotationLongClicked: (AnnotatedString, Int) -> Unit,
  onTextLayout: (TextLayoutResult) -> Unit = {}
) {
  val animationDuration = 200

  val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
  val currentlyPressedAnnotationPath = remember { mutableStateOf<Path?>(null) }
  val currentPressedAnnotationBgColor = remember { mutableStateOf<Color?>(null) }
  val currentTouchPosition = remember { mutableStateOf<Offset?>(null) }

  val coroutineScope = rememberCoroutineScope()
  val clearLinkPressIndicatorAnimationJob = remember { mutableStateOf<Job?>(null) }

  fun clearLinkPressIndicatorAnimation(forced: Boolean = false) {
    clearLinkPressIndicatorAnimationJob.value?.cancel()

    if (forced) {
      currentlyPressedAnnotationPath.value = null
      return
    }

    clearLinkPressIndicatorAnimationJob.value = coroutineScope.launch {
      delay(animationDuration + 300L)

      if (isActive) {
        currentlyPressedAnnotationPath.value = null
        clearLinkPressIndicatorAnimationJob.value = null
      }
    }
  }

  val pointerInputModifier = if (isTextClickable) {
    Modifier.pointerInput(key1 = text) {
      detectTapGesturesWithFilter(
        processDownEvent = { position ->
          clearLinkPressIndicatorAnimation(forced = true)

          val layoutRes = layoutResult.value
            ?: return@detectTapGesturesWithFilter false
          val clickedAnnotation = detectClickedAnnotations(position, layoutRes, text)
            ?: return@detectTapGesturesWithFilter false

          val path = layoutRes.getPathForRange(clickedAnnotation.start, clickedAnnotation.end)
          if (path.isEmpty) {
            return@detectTapGesturesWithFilter false
          }

          currentPressedAnnotationBgColor.value = annotationBgColors[clickedAnnotation.tag]
          currentlyPressedAnnotationPath.value = path
          currentTouchPosition.value = position

          return@detectTapGesturesWithFilter true
        },
        onTap = { position ->
          clearLinkPressIndicatorAnimation()

          layoutResult.value?.let { result ->
            val offset = result.getOffsetForPosition(position)
            onTextAnnotationClicked(text, offset)
          }
        },
        onLongPress = { position ->
          clearLinkPressIndicatorAnimation()

          layoutResult.value?.let { result ->
            val offset = result.getOffsetForPosition(position)
            onTextAnnotationLongClicked(text, offset)
          }
        },
        onUpOrCancel = { clearLinkPressIndicatorAnimation() }
      )
    }
  } else {
    Modifier
  }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .padding(top = 4.dp)
  ) {
    KurobaComposeText(
      modifier = modifier.then(pointerInputModifier),
      color = color,
      fontSize = fontSize,
      text = text,
      fontWeight = fontWeight,
      maxLines = maxLines,
      overflow = overflow,
      softWrap = softWrap,
      enabled = enabled,
      textAlign = textAlign,
      inlineContent = inlineContent,
      onTextLayout = { result ->
        layoutResult.value = result
        onTextLayout(result)
      }
    )

    ClickableTextAnimatedIndicatorOverlay(
      animationDuration = animationDuration,
      currentlyPressedAnnotationPath = currentlyPressedAnnotationPath,
      currentPressedAnnotationBgColor = currentPressedAnnotationBgColor,
      currentTouchPosition = currentTouchPosition
    )
  }
}

@Composable
private fun ClickableTextAnimatedIndicatorOverlay(
  animationDuration: Int,
  currentlyPressedAnnotationPath: MutableState<Path?>,
  currentPressedAnnotationBgColor: MutableState<Color?>,
  currentTouchPosition: MutableState<Offset?>
) {
  val annotationPathMut by currentlyPressedAnnotationPath
  val annotationPath = annotationPathMut
  val annotationColorMut by currentPressedAnnotationBgColor
  val annotationColor = annotationColorMut
  val touchPositionMut by currentTouchPosition
  val touchPosition = touchPositionMut

  val targetCircleRadius = if (annotationPath != null) {
    val pathBounds = annotationPath.getBounds()

    val pathWidth = pathBounds.width
    val pathHeight = pathBounds.height

    hypot(pathWidth, pathHeight)
  } else {
    0f
  }

  val circleRadiusAnimated = animateFloatAsState(
    targetValue = targetCircleRadius,
    animationSpec = FloatTweenSpec(
      duration = animationDuration,
      easing = FastOutLinearInEasing
    )
  )

  val path = remember { Path() }

  Canvas(
    modifier = Modifier
      .fillMaxSize()
      .zIndex(1f),
    onDraw = {
      val currentCircleRadius = circleRadiusAnimated.value

      if (annotationPath != null && annotationColor != null && touchPosition != null && currentCircleRadius > 0f) {
        path.rewind()
        path.addOval(Rect(center = touchPosition, radius = currentCircleRadius * 2f))
        path.close()

        clipPath(path) {
          drawPath(
            path = annotationPath,
            style = Fill,
            brush = SolidColor(value = annotationColor)
          )
        }
      }
    }
  )
}