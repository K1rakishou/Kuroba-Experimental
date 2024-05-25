package com.github.k1rakishou.chan.core.parser

import androidx.compose.runtime.Immutable

@Immutable
sealed interface PostIcon {
  @Immutable
  data class CountryFlag(
    val flagId: String,
    val flagName: String?
  ) : PostIcon

  @Immutable
  data class BoardFlag(
    val flagId: String,
    val flagName: String?
  ) : PostIcon
}