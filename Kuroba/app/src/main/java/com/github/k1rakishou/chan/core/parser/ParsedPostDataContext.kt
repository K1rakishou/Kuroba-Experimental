package com.github.k1rakishou.chan.core.parser

import androidx.compose.runtime.Immutable
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.ChanSettings.BoardPostViewMode
import com.github.k1rakishou.model.data.descriptor.PostDescriptor

@Immutable
data class ParsedPostDataContext(
  val isParsingCatalog: Boolean,
  val postViewMode: PostViewMode,
  val postCommentFontSizePixels: Int,
  val boldPostDescriptor: PostDescriptor? = null
) {
  val isParsingThread: Boolean = !isParsingCatalog
}

@Immutable
enum class PostViewMode {
  List,
  Grid,
  StaggeredGrid;

  fun toPostViewModeSetting(): ChanSettings.BoardPostViewMode {
    return when (this) {
      PostViewMode.List -> ChanSettings.BoardPostViewMode.LIST
      PostViewMode.Grid -> ChanSettings.BoardPostViewMode.GRID
      PostViewMode.StaggeredGrid -> ChanSettings.BoardPostViewMode.STAGGER
    }
  }

  companion object {
    fun fromBoardPostViewMode(boardPostViewMode: BoardPostViewMode): PostViewMode {
      return when (boardPostViewMode) {
        BoardPostViewMode.LIST -> PostViewMode.List
        BoardPostViewMode.GRID -> PostViewMode.Grid
        BoardPostViewMode.STAGGER -> PostViewMode.StaggeredGrid
      }
    }
  }

}
