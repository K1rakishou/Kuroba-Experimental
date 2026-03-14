package com.github.k1rakishou.chan.features.toolbar.state.container

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.dimensionResource
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.toolbar.KurobaToolbarState
import com.github.k1rakishou.chan.features.toolbar.KurobaToolbarTransition
import com.github.k1rakishou.chan.features.toolbar.state.KurobaToolbarSubState
import com.github.k1rakishou.chan.features.toolbar.state.container.transition.KurobaToolbarTransitionInstant
import com.github.k1rakishou.chan.features.toolbar.state.container.transition.KurobaToolbarTransitionProgress
import com.github.k1rakishou.chan.ui.compose.consumeClicks
import com.github.k1rakishou.chan.ui.compose.providers.KurobaWindowInsets
import com.github.k1rakishou.core_themes.ChanTheme

@Composable
fun KurobaContainerToolbarContent(
  isThemeOverridden: Boolean,
  chanTheme: ChanTheme,
  windowInsets: KurobaWindowInsets,
  kurobaToolbarState: KurobaToolbarState,
  childToolbar: @Composable (KurobaToolbarSubState?) -> Unit
) {
  val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
  val totalToolbarHeight = windowInsets.top + toolbarHeight

  if (!isThemeOverridden) {
    SideEffect {
      kurobaToolbarState.onToolbarHeightChanged(totalToolbarHeight)
    }
  }

  val toolbarStates by kurobaToolbarState.toolbarStateList
  val topToolbarState = toolbarStates.lastOrNull()

  if (topToolbarState is KurobaContainerToolbarSubState) {
    return
  }

  val transitionToolbarMut by kurobaToolbarState.transitionToolbarState
  val transitionToolbar = transitionToolbarMut

  val toolbarContentMovable = remember {
    movableContentOf { toolbarSubState: KurobaToolbarSubState? ->
      childToolbar(toolbarSubState)
    }
  }

  Column(
    modifier = Modifier
      .height(totalToolbarHeight)
      .graphicsLayer { alpha = kurobaToolbarState.toolbarAlpha.floatValue }
      .background(chanTheme.toolbarBackgroundComposeColor)
      .consumeClicks(enabled = true),
  ) {
    Spacer(modifier = Modifier.height(windowInsets.top))

    when (transitionToolbar) {
      null,
      is KurobaToolbarTransition.Progress -> {
        KurobaToolbarTransitionProgress(
          toolbarHeight = toolbarHeight,
          transitionToolbarState = transitionToolbar,
          topToolbarState = topToolbarState,
          topToolbarContent = toolbarContentMovable,
          transitionToolbarContent = toolbarContentMovable
        )
      }
      is KurobaToolbarTransition.Animated -> {
        KurobaToolbarTransitionInstant(
          toolbarHeight = toolbarHeight,
          transitionToolbarState = transitionToolbar,
          topToolbarState = topToolbarState,
          childToolbarMovable = toolbarContentMovable,
          childToolbar = toolbarContentMovable,
          onAnimationFinished = { kurobaToolbarState.onKurobaToolbarTransitionInstantFinished(transitionToolbar) }
        )
      }
    }
  }

}