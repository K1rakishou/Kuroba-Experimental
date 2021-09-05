package com.github.k1rakishou.chan.ui.compose

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import java.util.*

@Composable
fun KurobaComposeProgressIndicator(modifier: Modifier = Modifier, overrideColor: Color? = null) {
  Box(modifier = Modifier
    .fillMaxSize()
    .then(modifier)) {

    val color = if (overrideColor == null) {
      val chanTheme = LocalChanTheme.current
      remember(key1 = chanTheme.accentColor) { Color(chanTheme.accentColor) }
    } else {
      overrideColor
    }

    CircularProgressIndicator(
      color = color,
      modifier = Modifier
        .align(Alignment.Center)
        .size(42.dp, 42.dp)
    )
  }
}

@Composable
fun KurobaComposeErrorMessage(error: Throwable, modifier: Modifier = Modifier) {
  KurobaComposeErrorMessage(error.errorMessageOrClassName(), modifier)
}

@Composable
fun KurobaComposeErrorMessage(errorMessage: String, modifier: Modifier = Modifier) {
  Box(modifier = Modifier
    .fillMaxSize()
    .padding(8.dp)
    .then(modifier)
  ) {
    KurobaComposeText(errorMessage, modifier = Modifier.align(Alignment.Center))
  }
}

@Composable
fun KurobaComposeText(
  text: String,
  modifier: Modifier = Modifier,
  color: Color? = null,
  fontSize: TextUnit = TextUnit.Unspecified,
  fontWeight: FontWeight? = null,
  maxLines: Int = Int.MAX_VALUE,
  overflow: TextOverflow = TextOverflow.Clip,
  softWrap: Boolean = true,
  textAlign: TextAlign? = null
) {
  KurobaComposeText(
    text = AnnotatedString(text),
    modifier = modifier,
    color = color,
    fontSize = fontSize,
    fontWeight = fontWeight,
    maxLines = maxLines,
    overflow = overflow,
    softWrap = softWrap,
    textAlign = textAlign
  )
}

@Composable
fun KurobaComposeText(
  text: AnnotatedString,
  modifier: Modifier = Modifier,
  color: Color? = null,
  fontSize: TextUnit = TextUnit.Unspecified,
  fontWeight: FontWeight? = null,
  maxLines: Int = Int.MAX_VALUE,
  overflow: TextOverflow = TextOverflow.Clip,
  softWrap: Boolean = true,
  textAlign: TextAlign? = null
) {
  val textColorPrimary = if (color == null) {
    val chanTheme = LocalChanTheme.current

    remember(key1 = chanTheme.textColorPrimary) {
      Color(chanTheme.textColorPrimary)
    }
  } else {
    color
  }

  Text(
    color = textColorPrimary,
    text = text,
    fontSize = fontSize,
    maxLines = maxLines,
    overflow = overflow,
    softWrap = softWrap,
    textAlign = textAlign,
    fontWeight = fontWeight,
    modifier = modifier
  )
}

@Composable
fun KurobaComposeTextField(
  value: String,
  modifier: Modifier = Modifier,
  onValueChange: (String) -> Unit,
  maxLines: Int = Int.MAX_VALUE,
  singleLine: Boolean = false,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions(),
  textStyle: TextStyle = LocalTextStyle.current,
  label: @Composable (() -> Unit)? = null,
) {
  val chanTheme = LocalChanTheme.current

  TextField(
    value = value,
    label = label,
    onValueChange = onValueChange,
    maxLines = maxLines,
    singleLine = singleLine,
    modifier = modifier,
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions,
    colors = chanTheme.textFieldColors(),
    textStyle = textStyle
  )
}

@Composable
fun KurobaComposeCustomTextField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  textColor: Color = Color.White,
  drawBottomIndicator: Boolean = true,
  fontSize: TextUnit = TextUnit.Unspecified,
  maxLines: Int = Int.MAX_VALUE,
  singleLine: Boolean = false,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions(),
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
  val chanTheme = LocalChanTheme.current
  val textFieldColors = chanTheme.textFieldColors()

  val cursorBrush = remember(key1 = chanTheme) { SolidColor(chanTheme.accentColorCompose) }
  val textStyle = remember(key1 = textColor, key2 = fontSize) {
    TextStyle.Default.copy(color = textColor, fontSize = fontSize)
  }

  val indicatorLineModifier = if (drawBottomIndicator) {
    val indicatorColorState = textFieldColors.indicatorColor(
      enabled = true,
      isError = false,
      interactionSource = interactionSource
    )

    Modifier.drawIndicatorLine(
      color = indicatorColorState.value,
      lineWidth = 2.dp,
      verticalOffset = 4.dp
    )
  } else {
    Modifier
  }

  BasicTextField(
    modifier = modifier.then(indicatorLineModifier),
    textStyle = textStyle,
    singleLine = singleLine,
    maxLines = maxLines,
    cursorBrush = cursorBrush,
    value = value,
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions,
    interactionSource = interactionSource,
    onValueChange = onValueChange
  )

}

private fun Modifier.drawIndicatorLine(
  color: Color,
  lineWidth: Dp = 1.dp,
  verticalOffset: Dp = Dp.Unspecified
): Modifier {
  return drawBehind {
    val strokeWidth = lineWidth.value * density
    val y = size.height - strokeWidth / 2

    val drawFunc = {
      drawLine(
        color,
        Offset(0f, y),
        Offset(size.width, y),
        strokeWidth
      )
    }

    if (verticalOffset.isSpecified) {
      translate(top = verticalOffset.toPx()) {
        drawFunc()
      }
    } else {
      drawFunc()
    }
  }
}

@Composable
fun KurobaComposeCheckbox(
  currentlyChecked: Boolean,
  onCheckChanged: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  text: String? = null
) {
  val chanTheme = LocalChanTheme.current
  var isChecked by remember(key1 = currentlyChecked) { mutableStateOf(currentlyChecked) }

  Row(modifier = Modifier
    .clickable {
      isChecked = isChecked.not()
      onCheckChanged(isChecked)
    }
    .then(modifier)
  ) {
    Checkbox(
      modifier = Modifier.align(Alignment.CenterVertically),
      checked = isChecked,
      onCheckedChange = { checked ->
        isChecked = checked
        onCheckChanged(isChecked)
      },
      colors = chanTheme.checkBoxColors()
    )

    if (text != null) {
      Spacer(modifier = Modifier.width(8.dp))

      KurobaComposeText(text = text)
    }
  }
}

@Composable
fun KurobaComposeTextButton(
  onClick: () -> Unit,
  text: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  KurobaComposeButton(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier,
    buttonContent = {
      Text(
        text = text,
        modifier = Modifier.fillMaxSize(),
        textAlign = TextAlign.Center
      )
    }
  )
}

@Composable
fun KurobaComposeButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  buttonContent: @Composable RowScope.() -> Unit
) {
  val chanTheme = LocalChanTheme.current

  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier
      .wrapContentWidth()
      .height(36.dp)
      .then(modifier),
    content = buttonContent,
    colors = chanTheme.buttonColors()
  )
}

@Composable
fun KurobaComposeTextBarButton(
  onClick: () -> Unit,
  text: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  val chanTheme = LocalChanTheme.current

  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier
      .wrapContentSize()
      .then(modifier),
    content = {
      val textColor = if (enabled) {
        chanTheme.textColorPrimaryCompose
      } else {
        chanTheme.textColorPrimaryCompose.copy(alpha = ContentAlpha.disabled)
      }

      Text(
        text = text.uppercase(Locale.ENGLISH),
        color = textColor,
        modifier = Modifier.wrapContentSize().align(Alignment.CenterVertically),
        textAlign = TextAlign.Center
      )
    },
    elevation = null,
    colors = chanTheme.barButtonColors()
  )
}

@Composable
fun KurobaComposeSlider(
  value: Float,
  onValueChange: (Float) -> Unit,
  modifier: Modifier = Modifier
) {
  val chanTheme = LocalChanTheme.current

  Slider(
    value = value,
    modifier = modifier,
    onValueChange = onValueChange,
    colors = chanTheme.sliderColors()
  )
}

@Composable
fun KurobaComposeIcon(
  @DrawableRes drawableId: Int,
  themeEngine: ThemeEngine,
  modifier: Modifier = Modifier,
  colorBelowIcon: Color? = null
) {
  val chanTheme = LocalChanTheme.current
  val tintColor = remember(key1 = chanTheme) {
    if (colorBelowIcon == null) {
      Color(themeEngine.resolveDrawableTintColor())
    } else {
      Color(themeEngine.resolveDrawableTintColor(ThemeEngine.isDarkColor(colorBelowIcon.value)))
    }
  }

  Image(
    modifier = modifier,
    painter = painterResource(id = drawableId),
    colorFilter = ColorFilter.tint(tintColor),
    contentDescription = null
  )
}

private val defaultNoopClickCallback = { }

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.kurobaClickable(
  bounded: Boolean = false,
  onLongClick: (() -> Unit)? = null,
  onClick: (() -> Unit)? = null
): Modifier {
  if (onLongClick == null && onClick == null) {
    error("At least one of the callbacks must be non-null")
  }

  return composed {
    val chanTheme = LocalChanTheme.current

    val color = remember(key1 = chanTheme) {
      if (chanTheme.isLightTheme) {
        Color(0x40000000)
      } else {
        Color(0x40ffffff)
      }
    }

    then(
      Modifier.combinedClickable(
        indication = rememberRipple(bounded = bounded, color = color),
        interactionSource = remember { MutableInteractionSource() },
        onLongClick = onLongClick,
        onClick = onClick ?: defaultNoopClickCallback
      )
    )
  }
}

@Composable
fun KurobaComposeCardView(
  modifier: Modifier = Modifier,
  backgroundColor: Color? = null,
  shape: Shape = RoundedCornerShape(2.dp),
  content: @Composable () -> Unit
) {
  val chanTheme = LocalChanTheme.current

  Card(
    modifier = modifier,
    shape = shape,
    backgroundColor = backgroundColor ?: chanTheme.backColorCompose
  ) {
    content()
  }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun KurobaSearchInput(
  modifier: Modifier = Modifier,
  chanTheme: ChanTheme,
  themeEngine: ThemeEngine,
  backgroundColor: Color,
  searchQuery: MutableState<String>,
  onSearchQueryChanged: (String) -> Unit
) {
  var localQuery by remember { searchQuery }

  Row(modifier = modifier) {
    Row(modifier = Modifier.wrapContentHeight()) {
      Box(modifier = Modifier
        .wrapContentHeight()
        .weight(1f)
        .align(Alignment.CenterVertically)
        .padding(horizontal = 4.dp)
      ) {
        val interactionSource = remember { MutableInteractionSource() }

        val textColor = remember(key1 = backgroundColor) {
          if (ThemeEngine.isDarkColor(backgroundColor)) {
            Color.White
          } else {
            Color.Black
          }
        }

        KurobaComposeCustomTextField(
          modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth(),
          textColor = textColor,
          fontSize = 16.sp,
          singleLine = true,
          maxLines = 1,
          value = localQuery,
          onValueChange = { newValue ->
            localQuery = newValue
            onSearchQueryChanged(newValue)
          },
          interactionSource = interactionSource
        )

        val isFocused by interactionSource.collectIsFocusedAsState()

        androidx.compose.animation.AnimatedVisibility(
          visible = !isFocused && localQuery.isEmpty(),
          enter = fadeIn(),
          exit = fadeOut()
        ) {
          val alpha = ContentAlpha.medium

          val hintColor = remember(key1 = backgroundColor) {
            if (ThemeEngine.isDarkColor(backgroundColor)) {
              Color.LightGray.copy(alpha = alpha)
            } else {
              Color.DarkGray.copy(alpha = alpha)
            }
          }

          Text(
            text = stringResource(id = R.string.search_hint),
            fontSize = 16.sp,
            color = hintColor
          )
        }
      }

      AnimatedVisibility(
        visible = localQuery.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        KurobaComposeIcon(
          modifier = Modifier
            .align(Alignment.CenterVertically)
            .kurobaClickable(
              bounded = false,
              onClick = {
                localQuery = ""
                onSearchQueryChanged("")
              }
            ),
          drawableId = R.drawable.ic_clear_white_24dp,
          themeEngine = themeEngine,
          colorBelowIcon = chanTheme.primaryColorCompose
        )
      }
    }
  }
}