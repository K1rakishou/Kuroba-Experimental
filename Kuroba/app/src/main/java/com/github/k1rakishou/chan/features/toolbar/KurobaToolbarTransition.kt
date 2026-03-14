package com.github.k1rakishou.chan.features.toolbar

import androidx.compose.runtime.Immutable
import com.github.k1rakishou.chan.features.toolbar.state.KurobaToolbarSubState
import com.github.k1rakishou.chan.ui.controller.base.transition.TransitionMode

@Immutable
sealed interface KurobaToolbarTransition {
  val transitionMode: TransitionMode
  val transitionToolbarState: KurobaToolbarSubState?

  data class Progress(
    override val transitionMode: TransitionMode,
    override val transitionToolbarState: KurobaToolbarSubState?,
    val progress: Float
  ) : KurobaToolbarTransition

  data class Animated(
    override val transitionMode: TransitionMode,
    override val transitionToolbarState: KurobaToolbarSubState,
  ) : KurobaToolbarTransition

}