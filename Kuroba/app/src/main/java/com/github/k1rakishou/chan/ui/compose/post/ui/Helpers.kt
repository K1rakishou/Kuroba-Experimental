package com.github.k1rakishou.chan.ui.compose.post.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import com.github.k1rakishou.chan.core.parser.usecase.PostCommentApplier

internal fun detectClickedAnnotations(
  pos: Offset,
  layoutResult: TextLayoutResult?,
  text: AnnotatedString
): AnnotatedString.Range<String>? {
  val result = layoutResult
    ?: return null
  val offset = findFirstNonNewLineCharReversed(result.getOffsetForPosition(pos), text)
    ?: return null
  val clickedAnnotations = text.getStringAnnotations(offset, offset)

  // Process annotations in reversed order. This way if we have a link inside of a spoiler, when it's
  // clicked, we will first remove the spoiler and then the link will become clickable.
  for (clickedAnnotation in clickedAnnotations.asReversed()) {
    if (clickedAnnotation.tag in PostCommentApplier.ALL_TAGS) {
      return clickedAnnotation
    }
  }

  return null
}

// AnnotatedString.getStringAnnotations() fails (returns no annotations) if the character
// specified by offset is a new line symbol. So we need to find the first non-newline character
// going backwards.
private fun findFirstNonNewLineCharReversed(
  inputOffset: Int,
  text: AnnotatedString
): Int? {
  var offset = inputOffset

  while (true) {
    val ch = text.getOrNull(offset)
      ?: return null

    if (ch != '\n') {
      break
    }

    --offset
  }

  return offset
}
