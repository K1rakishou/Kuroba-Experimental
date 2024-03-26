package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ContentAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.compose.KurobaTextUnit
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine


@Composable
fun KurobaComposeCustomTextField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  textColor: Color = Color.Unspecified,
  parentBackgroundColor: Color = Color.Unspecified,
  drawBottomIndicator: Boolean = true,
  fontSize: KurobaTextUnit = 16.ktu,
  maxLines: Int = Int.MAX_VALUE,
  singleLine: Boolean = false,
  labelText: String? = null,
  maxTextLength: Int = Int.MAX_VALUE,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = remember { KeyboardActions() },
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
  val chanTheme = LocalChanTheme.current
  val textFieldColors = chanTheme.textFieldColors()
  val cursorBrush = remember(key1 = chanTheme) { SolidColor(chanTheme.accentColorCompose) }
  val lineTotalHeight = if (drawBottomIndicator) 4.dp else 0.dp
  val labelTextBottomOffset = if (drawBottomIndicator) 2.dp else 0.dp

  val actualTextColor = if (!textColor.isUnspecified) {
    textColor
  } else {
    if (ThemeEngine.isDarkColor(parentBackgroundColor)) {
      Color.White
    } else {
      Color.Black
    }
  }

  val textStyle = remember(key1 = actualTextColor, key2 = fontSize) {
    TextStyle.Default.copy(color = actualTextColor, fontSize = fontSize.value)
  }

  val indicatorLineModifier = if (drawBottomIndicator) {
    val indicatorColorState = textFieldColors.indicatorColor(
      enabled = enabled,
      isError = false,
      interactionSource = interactionSource
    )

    Modifier.drawIndicatorLine(
      color = indicatorColorState.value,
      lineWidth = 2.dp,
      verticalOffset = 2.dp
    )
  } else {
    Modifier
  }

  var localInput by remember { mutableStateOf(value) }

  KurobaComposeCustomTextFieldInternal(
    modifier = modifier,
    labelText = labelText,
    labelTextBottomOffset = labelTextBottomOffset,
    maxTextLength = maxTextLength,
    labelTextContent = {
      val isFocused by interactionSource.collectIsFocusedAsState()

      AnimatedVisibility(
        visible = !enabled || (!isFocused && localInput.isEmpty()),
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        val alpha = if (enabled) {
          ContentAlpha.medium
        } else {
          ContentAlpha.disabled
        }

        val hintColor = remember(key1 = parentBackgroundColor, key2 = alpha) {
          if (parentBackgroundColor.isUnspecified) {
            Color.DarkGray.copy(alpha = alpha)
          } else {
            if (ThemeEngine.isDarkColor(parentBackgroundColor)) {
              Color.LightGray.copy(alpha = alpha)
            } else {
              Color.DarkGray.copy(alpha = alpha)
            }
          }
        }

        ComposeText(
          text = labelText!!,
          fontSize = fontSize,
          color = hintColor
        )
      }
    },
    textFieldContent = {
      BasicTextField(
        modifier = indicatorLineModifier
          .padding(bottom = lineTotalHeight),
        enabled = enabled,
        textStyle = textStyle,
        singleLine = singleLine,
        maxLines = maxLines,
        cursorBrush = cursorBrush,
        value = value,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        onValueChange = { text ->
          localInput = text
          onValueChange(text)
        }
      )
    },
    textCounterContent = {
      val currentCounter = localInput.length
      val maxCounter = maxTextLength
      val counterText = remember(key1 = currentCounter, key2 = maxCounter) { "$currentCounter / $maxCounter" }
      val counterTextColor = if (currentCounter > maxCounter) {
        chanTheme.errorColorCompose
      } else {
        chanTheme.textColorHintCompose
      }

      Column {
        ComposeText(
          text = counterText,
          fontSize = 12.ktu,
          color = counterTextColor,
        )
      }
    }
  )
}

@Composable
fun KurobaComposeCustomTextField(
  value: TextFieldValue,
  onValueChange: (TextFieldValue) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  textColor: Color = Color.Unspecified,
  parentBackgroundColor: Color = Color.Unspecified,
  drawBottomIndicator: Boolean = true,
  fontSize: KurobaTextUnit = 16.ktu,
  maxLines: Int = Int.MAX_VALUE,
  singleLine: Boolean = false,
  labelText: String? = null,
  maxTextLength: Int = Int.MAX_VALUE,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = remember { KeyboardActions() },
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
  val chanTheme = LocalChanTheme.current
  val textFieldColors = chanTheme.textFieldColors()
  val cursorBrush = remember(key1 = chanTheme) { SolidColor(chanTheme.accentColorCompose) }
  val lineTotalHeight = if (drawBottomIndicator) 4.dp else 0.dp
  val labelTextBottomOffset = if (drawBottomIndicator) 2.dp else 0.dp

  val actualTextColor = if (!textColor.isUnspecified) {
    textColor
  } else {
    if (ThemeEngine.isDarkColor(parentBackgroundColor)) {
      Color.White
    } else {
      Color.Black
    }
  }

  val textStyle = remember(key1 = actualTextColor, key2 = fontSize) {
    TextStyle.Default.copy(color = actualTextColor, fontSize = fontSize.value)
  }

  val indicatorLineModifier = if (drawBottomIndicator) {
    val indicatorColorState = textFieldColors.indicatorColor(
      enabled = enabled,
      isError = false,
      interactionSource = interactionSource
    )

    Modifier.drawIndicatorLine(
      color = indicatorColorState.value,
      lineWidth = 2.dp,
      verticalOffset = 2.dp
    )
  } else {
    Modifier
  }

  var localInput by remember { mutableStateOf(value) }

  KurobaComposeCustomTextFieldInternal(
    modifier = modifier,
    labelText = labelText,
    labelTextBottomOffset = labelTextBottomOffset,
    maxTextLength = maxTextLength,
    labelTextContent = {
      val isFocused by interactionSource.collectIsFocusedAsState()

      AnimatedVisibility(
        visible = !enabled || (!isFocused && localInput.text.isEmpty()),
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        val alpha = if (enabled) {
          ContentAlpha.medium
        } else {
          ContentAlpha.disabled
        }

        val hintColor = remember(key1 = parentBackgroundColor, key2 = alpha) {
          if (parentBackgroundColor.isUnspecified) {
            Color.DarkGray.copy(alpha = alpha)
          } else {
            if (ThemeEngine.isDarkColor(parentBackgroundColor)) {
              Color.LightGray.copy(alpha = alpha)
            } else {
              Color.DarkGray.copy(alpha = alpha)
            }
          }
        }

        ComposeText(
          text = labelText!!,
          fontSize = fontSize,
          color = hintColor
        )
      }
    },
    textFieldContent = {
      BasicTextField(
        modifier = indicatorLineModifier
          .padding(bottom = lineTotalHeight),
        enabled = enabled,
        textStyle = textStyle,
        singleLine = singleLine,
        maxLines = maxLines,
        cursorBrush = cursorBrush,
        value = value,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        onValueChange = { text ->
          localInput = text
          onValueChange(text)
        }
      )
    },
    textCounterContent = {
      val currentCounter = localInput.text.length
      val maxCounter = maxTextLength
      val counterText = remember(key1 = currentCounter, key2 = maxCounter) { "$currentCounter / $maxCounter" }
      val counterTextColor = if (currentCounter > maxCounter) {
        chanTheme.errorColorCompose
      } else {
        chanTheme.textColorHintCompose
      }

      Column {
        ComposeText(
          text = counterText,
          fontSize = 12.ktu,
          color = counterTextColor,
        )
      }
    }
  )
}

@Composable
private fun KurobaComposeCustomTextFieldInternal(
  modifier: Modifier,
  labelText: String?,
  labelTextBottomOffset: Dp,
  maxTextLength: Int,
  labelTextContent: @Composable () -> Unit,
  textFieldContent: @Composable () -> Unit,
  textCounterContent: @Composable () -> Unit
) {
  val componentsCount = 3
  val labelTextSlotId = 0
  val textCounterSlotId = 1
  val textSlotId = 2

  val labelTextBottomOffsetPx = with(LocalDensity.current) { labelTextBottomOffset.toPx().toInt() }

  SubcomposeLayout(
    modifier = modifier,
    measurePolicy = { constraints ->
      val measurables = arrayOfNulls<Measurable?>(componentsCount)

      if (labelText != null) {
        val labelTextMeasurable = this.subcompose(
          slotId = labelTextSlotId,
          content = { labelTextContent() }
        ).firstOrNull()

        measurables[labelTextSlotId] = labelTextMeasurable
      }

      if (maxTextLength != Int.MAX_VALUE) {
        val textCounterMeasurable = this.subcompose(
          slotId = textCounterSlotId,
          content = { textCounterContent() }
        ).firstOrNull()

        measurables[textCounterSlotId] = textCounterMeasurable
      }

      val textFieldMeasurable = this.subcompose(
        slotId = textSlotId,
        content = { textFieldContent() }
      ).firstOrNull()

      measurables[textSlotId] = textFieldMeasurable

      var maxHeight = 0
      val placeables = arrayOfNulls<Placeable>(componentsCount)

      measurables[labelTextSlotId]?.let { labelTextMeasurable ->
        val placeable = labelTextMeasurable.measure(constraints)
        maxHeight = Math.max(maxHeight, placeable.height)

        placeables[labelTextSlotId] = placeable
      }

      // We are always supposed to at least have the text
      measurables[textSlotId]!!.let { textMeasurable ->
        val textCounterPlaceable = measurables[textCounterSlotId]?.let { textCounterMeasurable ->
          val placeable = textCounterMeasurable.measure(Constraints(maxWidth = constraints.maxWidth))
          placeables[textCounterSlotId] = placeable

          return@let placeable
        }

        val textCounterHeight = (textCounterPlaceable?.height ?: 0)
        val newMaxHeight = (constraints.maxHeight - textCounterHeight).coerceAtLeast(constraints.minHeight)

        val textPlaceable = textMeasurable.measure(constraints.copy(maxHeight = newMaxHeight))
        placeables[textSlotId] = textPlaceable

        maxHeight = Math.max(
          maxHeight,
          textPlaceable.height + textCounterHeight
        )
      }

      layout(constraints.maxWidth, maxHeight) {
        for ((index, placeable) in placeables.withIndex()) {
          if (placeable == null) {
            continue
          }

          when (index) {
            labelTextSlotId -> {
              if (maxHeight > placeable.height + labelTextBottomOffsetPx) {
                val y = maxHeight - (placeable.height + labelTextBottomOffsetPx)
                placeable.placeRelative(0, y)
              } else {
                placeable.placeRelative(0, 0)
              }
            }

            textCounterSlotId -> {
              placeable.placeRelative(0, 0)
            }

            textSlotId -> {
              val textCounterHeight = placeables[textCounterSlotId]?.height ?: 0
              placeable.placeRelative(0, textCounterHeight)
            }

            else -> {
              // no-op
            }
          }
        }
      }
    }
  )
}