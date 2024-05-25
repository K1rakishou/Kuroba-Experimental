package com.github.k1rakishou.chan.core.parser

import androidx.compose.runtime.Immutable

@Immutable
data class RevealedSpoiler(
  val start: Int,
  val end: Int
)