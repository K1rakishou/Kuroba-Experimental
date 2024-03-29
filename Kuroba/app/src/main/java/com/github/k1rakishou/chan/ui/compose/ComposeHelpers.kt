package com.github.k1rakishou.chan.ui.compose

import android.os.Bundle
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.core.text.getSpans
import androidx.lifecycle.ViewModel
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.common.requireComponentActivity
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_spannable.BackgroundColorIdSpan
import com.github.k1rakishou.core_spannable.ForegroundColorIdSpan
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ChanThemeColorId
import com.github.k1rakishou.core_themes.ThemeEngine

private const val TAG = "ComposeHelpers"
const val enableDebugCompositionLogs = true
private val DefaultPaddingValues = PaddingValues(0.dp)
val SCROLLBAR_WIDTH = 8.dp

@Composable
inline fun LogCompositions(tag: String) {
  if (enableDebugCompositionLogs && isDevBuild()) {
    val ref = remember { DebugRef(0) }
    SideEffect { ref.value++ }

    Logger.d("Compositions", "${tag} Count: ${ref.value}, ref=${ref.hashCode()}")
  }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.consumeClicks(enabled: Boolean = true): Modifier {
  if (!enabled) {
    return this
  }

  return combinedClickable(
    interactionSource = null,
    indication = null,
    enabled = true,
    onClick = {}
  )
}

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.passClicksThrough(passClicks: Boolean = true): Modifier {
  if (!passClicks) {
    return this
  }

  return pointerInteropFilter(onTouchEvent = { false })
}

fun Modifier.simpleVerticalScrollbar(
  state: LazyGridState,
  chanTheme: ChanTheme,
  contentPadding: PaddingValues = DefaultPaddingValues,
  width: Dp = SCROLLBAR_WIDTH
): Modifier {
  return composed {
    val targetAlpha = if (state.isScrollInProgress) 0.8f else 0f
    val duration = if (state.isScrollInProgress) 10 else 1500

    val alpha by animateFloatAsState(
      targetValue = targetAlpha,
      animationSpec = tween(durationMillis = duration)
    )

    this.then(
      Modifier.drawWithContent {
        drawContent()

        val layoutInfo = state.layoutInfo
        val firstVisibleElementIndex = layoutInfo.visibleItemsInfo.firstOrNull()?.index
        val needDrawScrollbar = layoutInfo.totalItemsCount > layoutInfo.visibleItemsInfo.size
                && (state.isScrollInProgress || alpha > 0.0f)

        // Draw scrollbar if total item count is greater than visible item count and either
        // currently scrolling or if the animation is still running and lazy column has content
        if (!needDrawScrollbar || firstVisibleElementIndex == null) {
          return@drawWithContent
        }

        val topPaddingPx = contentPadding.calculateTopPadding().toPx()
        val bottomPaddingPx = contentPadding.calculateBottomPadding().toPx()
        val totalHeightWithoutPaddings = this.size.height - topPaddingPx - bottomPaddingPx

        val elementHeight = totalHeightWithoutPaddings / layoutInfo.totalItemsCount
        val scrollbarOffsetY = firstVisibleElementIndex * elementHeight
        val scrollbarHeight = layoutInfo.visibleItemsInfo.size * elementHeight

        drawRect(
          color = chanTheme.textColorHintCompose,
          topLeft = Offset(this.size.width - width.toPx(), topPaddingPx + scrollbarOffsetY),
          size = Size(width.toPx(), scrollbarHeight),
          alpha = alpha
        )
      }
    )
  }
}

fun Modifier.simpleVerticalScrollbar(
  state: LazyListState,
  chanTheme: ChanTheme,
  contentPadding: PaddingValues = DefaultPaddingValues,
  width: Dp = SCROLLBAR_WIDTH
): Modifier {
  return composed {
    val targetAlpha = if (state.isScrollInProgress) 0.8f else 0f
    val duration = if (state.isScrollInProgress) 10 else 1500

    val alpha by animateFloatAsState(
      targetValue = targetAlpha,
      animationSpec = tween(durationMillis = duration)
    )

    this.then(
      Modifier.drawWithContent {
        drawContent()

        val layoutInfo = state.layoutInfo
        val firstVisibleElementIndex = layoutInfo.visibleItemsInfo.firstOrNull()?.index
        val needDrawScrollbar = layoutInfo.totalItemsCount > layoutInfo.visibleItemsInfo.size
                && (state.isScrollInProgress || alpha > 0.0f)

        // Draw scrollbar if total item count is greater than visible item count and either
        // currently scrolling or if the animation is still running and lazy column has content
        if (!needDrawScrollbar || firstVisibleElementIndex == null) {
          return@drawWithContent
        }

        val topPaddingPx = contentPadding.calculateTopPadding().toPx()
        val bottomPaddingPx = contentPadding.calculateBottomPadding().toPx()
        val totalHeightWithoutPaddings = this.size.height - topPaddingPx - bottomPaddingPx

        val elementHeight = totalHeightWithoutPaddings / layoutInfo.totalItemsCount
        val scrollbarOffsetY = firstVisibleElementIndex * elementHeight
        val scrollbarHeight = layoutInfo.visibleItemsInfo.size * elementHeight

        drawRect(
          color = chanTheme.textColorHintCompose,
          topLeft = Offset(this.size.width - width.toPx(), topPaddingPx + scrollbarOffsetY),
          size = Size(width.toPx(), scrollbarHeight),
          alpha = alpha
        )
      }
    )
  }
}

/**
 * Vertical scrollbar for Composables that use ScrollState (like verticalScroll())
 * */
fun Modifier.verticalScrollbar(
  contentPadding: PaddingValues,
  scrollState: ScrollState,
  thumbColor: Color,
  enabled: Boolean = true
): Modifier {
  if (!enabled) {
    return this
  }

  return composed {
    val density = LocalDensity.current

    val scrollbarWidth = with(density) { 4.dp.toPx() }
    val scrollbarHeight = with(density) { 16.dp.toPx() }

    val scrollStateUpdated by rememberUpdatedState(newValue = scrollState)
    val currentPositionPx by remember { derivedStateOf { scrollStateUpdated.value } }
    val maxScrollPositionPx by remember { derivedStateOf { scrollStateUpdated.maxValue } }

    val topPaddingPx = with(density) {
      remember(key1 = contentPadding) { contentPadding.calculateTopPadding().toPx() }
    }
    val bottomPaddingPx = with(density) {
      remember(key1 = contentPadding) { contentPadding.calculateBottomPadding().toPx() }
    }

    val duration = if (scrollStateUpdated.isScrollInProgress) 150 else 1000
    val delay = if (scrollStateUpdated.isScrollInProgress) 0 else 1000
    val targetThumbAlpha = if (scrollStateUpdated.isScrollInProgress) 0.8f else 0f

    val thumbAlphaAnimated by animateFloatAsState(
      targetValue = targetThumbAlpha,
      animationSpec = tween(
        durationMillis = duration,
        delayMillis = delay
      )
    )

    return@composed Modifier.drawWithContent {
      drawContent()

      if (maxScrollPositionPx == Int.MAX_VALUE || maxScrollPositionPx == 0) {
        return@drawWithContent
      }

      val availableHeight = this.size.height - scrollbarHeight - topPaddingPx - bottomPaddingPx
      val unit = availableHeight / maxScrollPositionPx.toFloat()
      val scrollPosition = currentPositionPx * unit

      val offsetX = this.size.width - scrollbarWidth
      val offsetY = topPaddingPx + scrollPosition

      drawRect(
        color = thumbColor,
        topLeft = Offset(offsetX, offsetY),
        size = Size(scrollbarWidth, scrollbarHeight),
        alpha = thumbAlphaAnimated
      )
    }
  }
}

fun Spanned.toAnnotatedString(themeEngine: ThemeEngine): AnnotatedString {
  val theme = themeEngine.chanTheme
  val annotatedStringBuilder = AnnotatedString.Builder(this.toString())

  getSpans<CharacterStyle>().forEach { characterStyle ->
    val spanStyle = when (characterStyle) {
      is AbsoluteSizeSpan -> {
        SpanStyle(fontSize = TextUnit(characterStyle.size.toFloat(), TextUnitType.Sp))
      }
      is BackgroundColorSpan -> {
        SpanStyle(background = Color(characterStyle.backgroundColor))
      }
      is BackgroundColorIdSpan -> {
        val color = getColorByColorId(
          themeEngine = themeEngine,
          chanThemeColorId = characterStyle.chanThemeColorId,
          factor = characterStyle.colorModificationFactor
        )

        SpanStyle(background = color)
      }
      is ForegroundColorSpan -> {
        SpanStyle(color = Color(characterStyle.foregroundColor))
      }
      is ForegroundColorIdSpan -> {
        SpanStyle(color = getColorByColorId(themeEngine, characterStyle.chanThemeColorId, null))
      }
      is PostLinkable -> {
        mapPostLinkable(characterStyle, theme)
      }
      else -> null
    }

    if (spanStyle == null) {
      Logger.d(TAG, "Unsupported character style: ${characterStyle::class.java.simpleName}")
      return@forEach
    }

    annotatedStringBuilder
      .addStyle(spanStyle, getSpanStart(characterStyle), getSpanEnd(characterStyle))
  }

  return annotatedStringBuilder.toAnnotatedString()
}

private fun mapPostLinkable(characterStyle: PostLinkable, theme: ChanTheme): SpanStyle {
  var color = Color.Magenta
  var background = Color.Unspecified
  var fontWeight: FontWeight? = null
  var textDecoration: TextDecoration? = null

  when (characterStyle.type) {
    PostLinkable.Type.QUOTE,
    PostLinkable.Type.QUOTE_TO_HIDDEN_OR_REMOVED_POST,
    PostLinkable.Type.LINK,
    PostLinkable.Type.THREAD,
    PostLinkable.Type.BOARD,
    PostLinkable.Type.SEARCH,
    PostLinkable.Type.DEAD,
    PostLinkable.Type.ARCHIVE -> {
      if (characterStyle.type == PostLinkable.Type.QUOTE) {
        if (characterStyle.isMarkedNo()) {
          color = Color(theme.postHighlightQuoteColor)
          fontWeight = FontWeight.Bold
        } else {
          color = Color(theme.postQuoteColor)
        }
      } else if (characterStyle.type == PostLinkable.Type.LINK) {
        color = Color(theme.postLinkColor)
      } else {
        color = Color(theme.postQuoteColor)
      }

      if (
        characterStyle.type == PostLinkable.Type.DEAD ||
        characterStyle.type == PostLinkable.Type.QUOTE_TO_HIDDEN_OR_REMOVED_POST
      ) {
        textDecoration = TextDecoration.LineThrough
      } else {
        textDecoration = TextDecoration.Underline
      }
    }
    PostLinkable.Type.SPOILER -> {
      background = Color(theme.postSpoilerColor)
      textDecoration = TextDecoration.None

      if (!characterStyle.isSpoilerVisible) {
        color = Color(theme.postSpoilerColor)
      } else {
        color = Color(theme.postSpoilerRevealTextColor)
      }
    }
  }

  return SpanStyle(
    color = color,
    background = background,
    fontWeight = fontWeight,
    textDecoration = textDecoration
  )
}

private fun getColorByColorId(
  themeEngine: ThemeEngine,
  chanThemeColorId: ChanThemeColorId,
  factor: Float?
): Color {
  var color = themeEngine.chanTheme.getColorByColorId(chanThemeColorId)

  if (factor != null) {
    color = ThemeEngine.manipulateColor(color, factor)
  }

  return Color(color)
}

class DebugRef(var value: Int)

fun PaddingValues.update(
  layoutDirection: LayoutDirection,
  start: Dp = calculateStartPadding(layoutDirection),
  top: Dp = calculateTopPadding(),
  end: Dp = calculateEndPadding(layoutDirection),
  bottom: Dp = calculateBottomPadding(),
): PaddingValues {
  return PaddingValues(
    start = start,
    top = top,
    end = end,
    bottom = bottom
  )
}

fun FocusRequester.requestFocusSafe() {
  try {
    // Sometimes crashes
    requestFocus()
  } catch (ignored: Throwable) {
    // no-op
  }
}

fun FocusRequester.freeFocusSafe() {
  try {
    // Sometimes crashes
    freeFocus()
  } catch (ignored: Throwable) {
    // no-op
  }
}

fun FocusManager.clearFocusSafe(force: Boolean = false) {
  try {
    // Sometimes crashes
    clearFocus(force)
  } catch (ignored: Throwable) {
    // no-op
  }
}

@Composable
inline fun <reified VM : ViewModel> rememberViewModel(
  key: String? = null,
  noinline defaultArgs: (@DisallowComposableCalls () -> Bundle)? = null
): VM {
  val context = LocalContext.current

  return remember(key1 = VM::class.java, key2 = key) {
    context.requireComponentActivity().viewModelByKey<VM>(
      key = key,
      defaultArgs = defaultArgs?.invoke()
    )
  }
}

fun List<Measurable>.ensureSingleMeasurable(): Measurable {
  if (size != 1) {
    error(
      "Expected subcompose() to have only return a single measurable but got ${size} instead. " +
              "Most likely you are trying to emit multiple composables inside of the content() lambda. " +
              "Wrap those composables into any container (Box/Column/Row/etc.) and this crash should go away."
    )
  }

  return first()
}

fun TextFieldBuffer.clearText() {
  delete(0, length)
}