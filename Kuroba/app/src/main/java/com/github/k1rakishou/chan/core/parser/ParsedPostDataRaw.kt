package com.github.k1rakishou.chan.core.parser

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import com.github.k1rakishou.chan.ui.compose.data.PostDescriptorUi

@Immutable
data class ParsedPostDataRaw(
  val parsedPostParts: List<TextPart>,
  val repliesTo: Set<PostDescriptorUi>,
  val parsedPostComment: String,
  val parsedPostSubject: String,
  val processedPostComment: ProcessedPostComment,
  val processedPostSubject: AnnotatedString,
  val postFooterText: AnnotatedString?,
  val isPostMarkedAsMine: Boolean,
  val isReplyToPostMarkedAsMine: Boolean,
  val parsedPostDataContext: ParsedPostDataContext
)