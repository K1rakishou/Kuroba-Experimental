package com.github.k1rakishou.chan.core.parser

import androidx.compose.ui.text.AnnotatedString
import kotlinx.collections.immutable.ImmutableList

data class ProcessedPostComment(
  val string: AnnotatedString,
  val spans: ImmutableList<AppliedSpan>
) {
  val text: String
    get() = string.text

  fun isNotEmpty(): Boolean {
    return text.isNotEmpty()
  }

}