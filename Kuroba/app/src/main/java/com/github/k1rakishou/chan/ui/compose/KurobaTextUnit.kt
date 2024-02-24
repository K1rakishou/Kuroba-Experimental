package com.github.k1rakishou.chan.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import com.github.k1rakishou.ChanSettings
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow

@Composable
fun rememberKurobaTextUnit(
  fontSize: TextUnit,
  min: TextUnit? = null,
  max: TextUnit? = null
): KurobaTextUnit {
  return remember(fontSize, min, max) {
    KurobaTextUnit(fontSize, min, max)
  }
}

data class KurobaTextUnit(
  val fontSize: TextUnit,
  val min: TextUnit? = null,
  val max: TextUnit? = null
)

@Composable
fun collectTextFontSize(defaultFontSize: TextUnit): TextUnit {
  val defaultFontSizeKurobaUnits = rememberKurobaTextUnit(fontSize = defaultFontSize)

  return collectTextFontSize(defaultFontSize = defaultFontSizeKurobaUnits)
}

@Composable
fun collectTextFontSize(defaultFontSize: KurobaTextUnit): TextUnit {
  val textUnit = defaultFontSize.fontSize
  val min = defaultFontSize.min
  val max = defaultFontSize.max

  if (textUnit.isUnspecified) {
    return textUnit
  }

  val coroutineScope = rememberCoroutineScope()

  var globalFontSizeMultiplier by remember {
    val fontSizeMultiplier = calculateFontSizeMultiplier()

    mutableFloatStateOf(fontSizeMultiplier / 100f)
  }

  LaunchedEffect(
    key1 = Unit,
    block = {
      coroutineScope.launch {
        ChanSettings.fontSize.listenForChanges().asFlow()
          .collectLatest { value ->
            val fontSizeMultiplier = calculateFontSizeMultiplier()
            globalFontSizeMultiplier = fontSizeMultiplier / 100f
          }
      }
    }
  )

  var updatedTextUnit = (textUnit * globalFontSizeMultiplier)

  if (min != null && updatedTextUnit < min) {
    updatedTextUnit = min
  }

  if (max != null && updatedTextUnit > max) {
    updatedTextUnit = max
  }

  return updatedTextUnit
}

private fun calculateFontSizeMultiplier(): Float {
  val defaultFontSizeFromSettings = ChanSettings.defaultFontSize().toFloat()
  val fontSize = ChanSettings.fontSize.get().toIntOrNull()?.toFloat()
    ?: ChanSettings.defaultFontSize().toFloat()

  return fontSize / defaultFontSizeFromSettings
}