package com.github.k1rakishou.chan.ui.compose.snackbar

import androidx.compose.runtime.Immutable
import com.github.k1rakishou.chan.ui.controller.base.Controller

@Immutable
sealed class SnackbarType {
  val isToast: Boolean
    get() = this is Toast || this is ErrorToast

  data object Default : SnackbarType()
  data object Toast : SnackbarType()
  data object ErrorToast : SnackbarType()
}

@Immutable
sealed interface SnackbarScope {
  val layoutAnchor: LayoutAnchor?

  val tag: String
    get() {
      val layoutAnchor = layoutAnchor
      if (layoutAnchor != null) {
        return "SnackbarScope_${this::class.java.simpleName}_${layoutAnchor.name}"
      }

      return "SnackbarScope_${this::class.java.simpleName}"
    }

  data class Global(
    override val layoutAnchor: LayoutAnchor? = null
  ) : SnackbarScope

  data class PostList(
    override val layoutAnchor: LayoutAnchor? = null
  ) : SnackbarScope

  data class MediaViewer(
    override val layoutAnchor: LayoutAnchor? = null
  ) : SnackbarScope

  data class Album(
    override val layoutAnchor: LayoutAnchor
  ) : SnackbarScope

  data class ControllerSpecific(
    val clazz: Class<Controller>,
    override val layoutAnchor: LayoutAnchor? = null
  ) : SnackbarScope {
    override val tag: String = "SnackbarScope_ControllerSpecific_${clazz.name}_${layoutAnchor?.name}"
  }

  enum class LayoutAnchor {
    Catalog,
    Thread
  }

}