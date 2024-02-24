package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme

@Composable
fun KurobaComposeClickableText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
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