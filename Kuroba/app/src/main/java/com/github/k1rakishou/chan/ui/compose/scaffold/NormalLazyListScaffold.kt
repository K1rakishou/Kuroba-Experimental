package com.github.k1rakishou.chan.ui.compose.scaffold

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeDivider
import com.github.k1rakishou.chan.ui.compose.consumeClicks
import com.github.k1rakishou.chan.ui.compose.copy
import com.github.k1rakishou.chan.ui.compose.isFullyScrolledBottom
import com.github.k1rakishou.chan.ui.compose.isFullyScrolledTop
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.providers.LocalContentPaddings
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey

interface NormalLazyListScaffold : LazyListScaffoldShared

class NormalLazyListScaffoldBuilder : NormalLazyListScaffold {
  @Composable
  fun Content(
    boxScope: BoxScope,
    lazyListState: LazyListState,
    controllerKey: ControllerKey,
    header: (@Composable BoxScope.() -> Unit)? = null,
    body: @Composable BoxScope.(PaddingValues) -> Unit,
    footer: @Composable BoxScope.(Dp) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val contentPaddings = LocalContentPaddings.current

    with(boxScope) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .consumeClicks()
          .align(Alignment.Center)
          .background(chanTheme.backColorCompose)
      ) {
        var lazyListPaddings by remember { mutableStateOf(PaddingValues.Zero) }

        if (header != null) {
          Column(
            modifier = Modifier
              .onSizeChanged { intSize ->
                lazyListPaddings = with(density) {
                  lazyListPaddings.copy(
                    layoutDirection = layoutDirection,
                    top = intSize.height.toDp()
                  )
                }
              }
              .fillMaxWidth()
              .wrapContentHeight()
              .align(Alignment.TopCenter)
              .consumeClicks(enabled = true)
              .zIndex(1f)
              .shadow(elevation = 4.dp)
          ) {
            Spacer(modifier = Modifier.height(contentPaddings.calculateTopPadding()))

            Box {
              header()
            }

            val fullyScrolledTop by remember { derivedStateOf { lazyListState.isFullyScrolledTop() } }
            if (!fullyScrolledTop) {
              KurobaComposeDivider(modifier = Modifier.fillMaxWidth())
            }
          }
        }

        body(lazyListPaddings)

        Column(
          modifier = Modifier
            .onSizeChanged { intSize ->
              lazyListPaddings = with(density) {
                lazyListPaddings.copy(
                  layoutDirection = layoutDirection,
                  bottom = intSize.height.toDp()
                )
              }
            }
            .fillMaxWidth()
            .wrapContentHeight()
            .align(Alignment.BottomCenter)
            .consumeClicks(enabled = true)
            .zIndex(1f)
            .shadow(elevation = 4.dp)
        ) {
          val isFullyScrolledBottom by remember { derivedStateOf { lazyListState.isFullyScrolledBottom() } }
          if (!isFullyScrolledBottom) {
            KurobaComposeDivider(modifier = Modifier.fillMaxWidth())
          }

          Box {
            footer(contentPaddings.calculateBottomPadding(controllerKey))
          }
        }
      }
    }
  }
}