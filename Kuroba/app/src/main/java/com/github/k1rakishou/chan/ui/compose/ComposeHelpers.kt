package com.github.k1rakishou.chan.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.annotation.FrequentlyChangingValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.activityDependencies
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

private const val TAG = "ComposeHelpers"
const val enableDebugCompositionLogs = true

class DebugRef(var value: Int)

@Composable
inline fun LogCompositions(tag: String) {
  if (enableDebugCompositionLogs && AppModuleAndroidUtils.isDevBuild) {
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


fun PaddingValues.copy(
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

fun PaddingValues.addBottom(layoutDirection: LayoutDirection, bottom: Dp): PaddingValues {
  return PaddingValues(
    start = calculateStartPadding(layoutDirection),
    end = calculateEndPadding(layoutDirection),
    top = calculateTopPadding(),
    bottom = calculateBottomPadding() + bottom
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

fun TextFieldState.textAsFlow(): Flow<CharSequence> {
  return snapshotFlow { text }
}

@Composable
fun TextFieldState.collectText(): CharSequence {
  var currentText by remember { mutableStateOf(text) }

  LaunchedEffect(key1 = this) {
    forEachTextValue { text -> currentText = text }
  }

  return currentText
}

suspend fun TextFieldState.forEachTextValue(block: (CharSequence) -> Unit): Nothing {
  textAsFlow()
    .collectLatest { text -> block(text) }

  error("forEachTextValue doesn't return normally")
}

@Stable
fun Modifier.clearFocusOnKeyboardDismiss(): Modifier = composed {
  var isFocused by remember { mutableStateOf(false) }
  var keyboardAppearedSinceLastFocused by remember { mutableStateOf(false) }

  if (isFocused) {
    val imeIsVisible = activityDependencies().globalWindowInsetsManager.isKeyboardOpened
    val focusManager = LocalFocusManager.current

    LaunchedEffect(imeIsVisible) {
      if (imeIsVisible) {
        keyboardAppearedSinceLastFocused = true
      } else if (keyboardAppearedSinceLastFocused) {
        focusManager.clearFocus()
      }
    }
  }

  onFocusEvent { focusState ->
    if (isFocused != focusState.isFocused) {
      isFocused = focusState.isFocused
      if (isFocused) {
        keyboardAppearedSinceLastFocused = false
      }
    }
  }
}

@FrequentlyChangingValue
fun LazyListState.isFullyScrolledTop(): Boolean {
  return firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0
}

@FrequentlyChangingValue
fun LazyListState.isFullyScrolledBottom(): Boolean {
  val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
    ?: return false

  return lastVisibleItem.index == layoutInfo.totalItemsCount - 1 &&
    lastVisibleItem.offset + lastVisibleItem.size <= layoutInfo.viewportEndOffset
}