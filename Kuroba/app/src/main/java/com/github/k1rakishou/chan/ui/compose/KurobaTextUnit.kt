package com.github.k1rakishou.chan.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.ChanSettings
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow

data class KurobaTextUnit(
  val value: TextUnit,
  val min: TextUnit? = null,
  val max: TextUnit? = null
) {

  fun fixedSize(): KurobaTextUnit {
    return copy(min = value, max = value)
  }

  fun toDp(density: Density): Dp {
    return with(density) { value.toDp() }
  }

}

@Stable
val Int.ktu: KurobaTextUnit
  get() = KurobaTextUnit(this.sp)

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

@Composable
fun collectTextFontSize(defaultFontSize: TextUnit): TextUnit {
  val defaultFontSizeKurobaUnits = rememberKurobaTextUnit(fontSize = defaultFontSize)

  return collectTextFontSize(defaultFontSize = defaultFontSizeKurobaUnits)
}

@Composable
fun collectTextFontSize(defaultFontSize: KurobaTextUnit): TextUnit {
  val textUnit = defaultFontSize.value
  val min = defaultFontSize.min
  val max = defaultFontSize.max

  if (textUnit.isUnspecified) {
    return textUnit
  }

  val globalFontSizeMultiplier = collectGlobalFontSizeMultiplierAsState()
  var updatedTextUnit = (textUnit * globalFontSizeMultiplier)

  if (min != null && updatedTextUnit < min) {
    updatedTextUnit = min
  }

  if (max != null && updatedTextUnit > max) {
    updatedTextUnit = max
  }

  return updatedTextUnit
}

@Composable
fun collectGlobalFontSizeMultiplierAsState(): Float {
  val coroutineScope = rememberCoroutineScope()
  var globalFontSizeMultiplier by remember { mutableFloatStateOf(calculateFontSizeMultiplier()) }

  LaunchedEffect(
    key1 = Unit,
    block = {
      coroutineScope.launch {
        ChanSettings.fontSize.listenForChanges()
          .asFlow()
          .collectLatest { globalFontSizeMultiplier = calculateFontSizeMultiplier() }
      }
    }
  )

  return globalFontSizeMultiplier
}

private fun calculateFontSizeMultiplier(): Float {
  val defaultFontSizeFromSettings = ChanSettings.defaultFontSize().toFloat()
  val fontSize = ChanSettings.fontSize.get().toIntOrNull()?.toFloat()
    ?: ChanSettings.defaultFontSize().toFloat()

  return fontSize / defaultFontSizeFromSettings
}