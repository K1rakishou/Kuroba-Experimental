package com.github.k1rakishou.chan.ui.compose.post.ui.body

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import com.github.k1rakishou.chan.core.parser.ProcessedPostComment
import com.github.k1rakishou.chan.ui.compose.KurobaTextUnit
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.post.state.PostCellTextState
import com.github.k1rakishou.chan.ui.compose.post.state.PostCommentClickable
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

internal enum class Ordering(val zIndex: Float) {
  Text(0f),
  Link(1f),
  Spoiler(2f)
}

@Composable
fun PostCellText(
  modifier: Modifier = Modifier,
  postComment: ProcessedPostComment,
  postCellTextState: PostCellTextState,
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
  onSpoilerClicked: (PostCommentClickable.Spoiler) -> Unit,
  onTextLayout: (TextLayoutResult) -> Unit
) {
  val chanTheme = LocalChanTheme.current

  val linkClickAnimationDuration = 300
  val spoilerClickAnimationDuration = 800
  val textLayoutResultState = remember { mutableStateOf<TextLayoutResult?>(null) }

  val spoilersOverlayInitializedState = remember { mutableStateOf(false) }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
  ) {
    val pointerInputModifier = if (isTextClickable) {
      Modifier.postTextClick(
        postComment = postComment,
        textLayoutResultState = textLayoutResultState,
        spoilersOverlayInitializedState = spoilersOverlayInitializedState,
        postCellTextState = postCellTextState
      )
    } else {
      Modifier
    }

    KurobaComposeText(
      modifier = modifier
        .then(pointerInputModifier)
        .then(
          Modifier.drawWithContent {
            drawContent()

            if (!spoilersOverlayInitializedState.value) {
              drawRect(color = chanTheme.backColorCompose)
            }
          }
        ),
      color = color,
      fontSize = fontSize,
      text = postComment.string,
      fontWeight = fontWeight,
      maxLines = maxLines,
      overflow = overflow,
      softWrap = softWrap,
      enabled = enabled,
      textAlign = textAlign,
      inlineContent = inlineContent,
      onTextLayout = { result ->
        textLayoutResultState.value = result
        onTextLayout(result)
      }
    )

    PostCellLinksOverlay(
      modifier = Modifier
        .fillMaxSize()
        .zIndex(Ordering.Link.zIndex),
      animationDuration = linkClickAnimationDuration,
      postCellTextState = postCellTextState
    )

    PostCellSpoilersOverlay(
      modifier = Modifier
        .fillMaxSize()
        .zIndex(Ordering.Spoiler.zIndex),
      animationDuration = spoilerClickAnimationDuration,
      postCellTextState = postCellTextState,
      textLayoutResultState = textLayoutResultState,
      onSpoilerClicked = onSpoilerClicked,
      onSpoilersOverlayInitialized = {
        spoilersOverlayInitializedState.value = true
      }
    )
  }
}

private fun Modifier.postTextClick(
  postComment: ProcessedPostComment,
  textLayoutResultState: MutableState<TextLayoutResult?>,
  spoilersOverlayInitializedState: State<Boolean>,
  postCellTextState: PostCellTextState
) = pointerInput(key1 = postComment) {
  awaitEachGesture {
    val downPointerInputChange = awaitFirstDown()

    if (!spoilersOverlayInitializedState.value) {
      downPointerInputChange.consume()
      return@awaitEachGesture
    }

    val currentLayoutResult = textLayoutResultState.value
      ?: return@awaitEachGesture

    val downTextOffset = currentLayoutResult.getOffsetForPosition(downPointerInputChange.position)

    val hitsSomething = postCellTextState.onPointerDown(
      downPointerInputChange.position,
      downTextOffset,
      currentLayoutResult
    )

    if (!hitsSomething) {
      return@awaitEachGesture
    }

    val upOrCancelPointerInputChange = waitForUpOrCancellation()
    if (upOrCancelPointerInputChange == null) {
      postCellTextState.onCanceled(downPointerInputChange.position)

      return@awaitEachGesture
    }

    downPointerInputChange.consume()
    upOrCancelPointerInputChange.consume()

    postCellTextState.onPointerUp(upOrCancelPointerInputChange.position)

    // TODO: compose post cells.
    //  onSpoilerClicked()
  }
}