package com.github.k1rakishou.chan.features.reply.data

import androidx.compose.runtime.Immutable

@Immutable
data class SyntheticReplyAttachable(
  val id: SyntheticFileId,
  val state: SyntheticReplyAttachableState
)

@Immutable
sealed interface SyntheticFileId {
  data class FilePath(val path: String) : SyntheticFileId
  data class Url(val url: String) : SyntheticFileId
}

@Immutable
sealed interface SyntheticReplyAttachableState {
  data object Initializing : SyntheticReplyAttachableState
  data object Downloading : SyntheticReplyAttachableState
  data object Decoding : SyntheticReplyAttachableState
  data object Done : SyntheticReplyAttachableState
}