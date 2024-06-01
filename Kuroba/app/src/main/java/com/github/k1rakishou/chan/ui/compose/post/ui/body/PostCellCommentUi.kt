package com.github.k1rakishou.chan.ui.compose.post.ui.body

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.compose.Shimmer
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeClickableText
import com.github.k1rakishou.chan.ui.compose.post.state.PostCellState
import com.github.k1rakishou.chan.ui.compose.post.state.ThreadState
import com.github.k1rakishou.chan.ui.compose.post.ui.detectClickedAnnotations

@Composable
internal fun PostCellCommentUi(
  modifier: Modifier,
  threadState: ThreadState,
  postCellState: PostCellState
) {
  val postCommentMut by postCellState.postComment
  val postComment = postCommentMut

  val postCommentFontSizeMut by threadState.postCommentFontSize.collectAsState()
  val postCommentFontSize = postCommentFontSizeMut

  val isInTextSelectionMode by threadState.isInTextSelectionMode
  val detectLinkableClicks by threadState.detectLinkableClicks
  val isInPostSelectionMode by threadState.isInPostSelectionMode
  val clickedTextBackgroundColorMap by threadState.clickedTextBackgroundColorMap

  if (postComment == null || postCommentFontSize == null) {
    Shimmer(
      modifier = modifier
        then Modifier.height(42.dp)
    )

    return
  }

  PostCellCommentSelectionWrapper(
    // TODO: compose post cells. ComposeCustomTextSelection needs to be updated!!!
    textSelectionEnabled = false, // textSelectionEnabled && !isInPostSelectionMode,
    onCopySelectedText = { selectedText -> threadState.onCopySelectedText(selectedText) },
    onQuoteSelectedText = { withText, selectedText -> threadState.onQuoteSelectedText(postCellState, withText, selectedText) },
    onTextSelectionModeChanged = { inSelectionMode ->
      threadState.onTextSelectionModeChanged(postCellState, inSelectionMode)
    }
  ) { textModifier, onTextLayout ->
    val isTextClickable = detectLinkableClicks && !isInTextSelectionMode && !isInPostSelectionMode

    KurobaComposeClickableText(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .then(textModifier),
      text = postComment,
      isTextClickable = isTextClickable,
      annotationBgColors = clickedTextBackgroundColorMap,
      inlineContent = inlinedContentForPostCellComment(postCellState),
      detectClickedAnnotations = { offset, textLayoutResult, text ->
        return@KurobaComposeClickableText detectClickedAnnotations(offset, textLayoutResult, text)
      },
      onTextAnnotationClicked = { text, offset -> threadState.onPostCellCommentClicked(postCellState, text, offset) },
      onTextAnnotationLongClicked = { text, offset -> threadState.onPostCellCommentLongClicked(postCellState, text, offset) },
      onTextLayout = onTextLayout
    )
  }
}