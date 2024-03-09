package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import com.github.k1rakishou.chan.ui.compose.KurobaTextUnit
import com.github.k1rakishou.chan.ui.compose.Shimmer
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.common.ELLIPSIS_SYMBOL
import com.github.k1rakishou.common.substringSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun KurobaComposeMiddleEllipsisText(
  modifier: Modifier = Modifier,
  text: String,
  color: Color = Color.White,
  fontSize: KurobaTextUnit = 14.ktu
) {
  val density = LocalDensity.current
  var visible by remember { mutableStateOf(false) }

  val textMeasurer = rememberTextMeasurer()

  BoxWithConstraints {
    val availableWidthPx = with(density) { maxWidth.toPx() }

    val fileName by produceState(
      initialValue = text,
      key1 = text,
      key2 = availableWidthPx
    ) {
      withContext(Dispatchers.Default) {
        if (text.isBlank()) {
          value = text
          visible = true
          return@withContext
        }

        val textLayoutResult = textMeasurer.measure(text)
        if (textLayoutResult.size.width <= availableWidthPx) {
          // Fast path. The text can fit into the width
          value = text
          visible = true
          return@withContext
        }

        val letterWidth = textLayoutResult.size.width / text.length
        val textWidth = (availableWidthPx - (ELLIPSIS_SYMBOL.length * letterWidth)).toInt()
        val textLength = textWidth / letterWidth
        val halfTextLength = textLength / 2

        value = buildString(capacity = textLength) {
          append(text.substringSafe(0, halfTextLength))
          append(ELLIPSIS_SYMBOL)
          append(text.substringSafe(text.length - halfTextLength, text.length))
        }

        visible = true
      }
    }

    if (visible) {
      if (fileName.isNotBlank()) {
        KurobaComposeText(
          modifier = modifier,
          text = fileName,
          textAlign = TextAlign.Center,
          maxLines = 1,
          color = color,
          fontSize = fontSize
        )
      }
    } else {
      val height = with(LocalDensity.current) { fontSize.toDp(this) }

      Shimmer(
        modifier = modifier
          .height(height)
      )
    }
  }
}