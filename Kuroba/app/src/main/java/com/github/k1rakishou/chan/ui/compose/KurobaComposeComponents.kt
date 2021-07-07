package com.github.k1rakishou.chan.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.common.errorMessageOrClassName

@Composable
fun KurobaComposeProgressIndicator(modifier: Modifier = Modifier) {
  Box(modifier = Modifier
    .fillMaxSize()
    .then(modifier)) {
    val chanTheme = LocalChanTheme.current
    val accentColor = remember(key1 = chanTheme.accentColor) {
      Color(chanTheme.accentColor)
    }

    CircularProgressIndicator(
      color = accentColor,
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
  textAlign: TextAlign? = null
) {
  KurobaComposeText(
    text = AnnotatedString(text),
    modifier = modifier,
    color = color,
    fontSize = fontSize,
    fontWeight = fontWeight,
    maxLines = maxLines,
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
    colors = chanTheme.textFieldColors()
  )
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
      .width(112.dp)
      .height(36.dp)
      .then(modifier),
    content = buttonContent,
    colors = chanTheme.buttonColors()
  )
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