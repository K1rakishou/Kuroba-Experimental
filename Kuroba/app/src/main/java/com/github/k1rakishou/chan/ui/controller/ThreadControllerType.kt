package com.github.k1rakishou.chan.ui.controller

import android.os.Parcelable
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarScope
import kotlinx.parcelize.Parcelize

@Parcelize
enum class ThreadControllerType : Parcelable {
  Catalog,
  Thread;

  fun asSnackbarScope(): SnackbarScope {
    return when (this) {
      Catalog -> SnackbarScope.PostList(layoutAnchor = SnackbarScope.LayoutAnchor.Catalog)
      Thread -> SnackbarScope.PostList(layoutAnchor = SnackbarScope.LayoutAnchor.Thread)
    }
  }
}