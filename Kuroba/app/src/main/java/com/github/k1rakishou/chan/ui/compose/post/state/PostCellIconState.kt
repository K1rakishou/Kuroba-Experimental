package com.github.k1rakishou.chan.ui.compose.post.state

import androidx.compose.runtime.Stable
import okhttp3.HttpUrl

// TODO: compose post cells. I think this can be removed?
@Stable
data class PostCellIconState(
  val iconUrl: HttpUrl,
  val iconName: String
)