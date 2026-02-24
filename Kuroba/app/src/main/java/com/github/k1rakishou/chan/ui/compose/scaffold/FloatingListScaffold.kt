package com.github.k1rakishou.chan.ui.compose.scaffold

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeDivider
import com.github.k1rakishou.chan.ui.compose.consumeClicks
import com.github.k1rakishou.chan.ui.compose.copy
import com.github.k1rakishou.chan.ui.compose.isFullyScrolledBottom
import com.github.k1rakishou.chan.ui.compose.isFullyScrolledTop
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme

interface FloatingListScaffold : ListScaffoldShared

class FloatingListScaffoldBuilder : FloatingListScaffold {
  @Composable
  fun Content(
    boxScope: BoxScope,
    scrollState: ScrollState,
    header: (@Composable BoxScope.() -> Unit)? = null,
    body: @Composable BoxScope.(PaddingValues) -> Unit,
    footer: @Composable BoxScope.() -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    val alphaAnimatable = remember { Animatable(initialValue = 0f) }
    var headerMeasured by remember { mutableStateOf(header == null) }
    var footerMeasured by remember { mutableStateOf(false) }

    with(boxScope) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .consumeClicks()
          .background(chanTheme.backColorCompose)
          .align(Alignment.Center)
      ) {
        var contentPaddings by remember { mutableStateOf(PaddingValues.Zero) }

        if (header != null) {
          Column(
            modifier = Modifier
              .onSizeChanged { intSize ->
                contentPaddings = with(density) {
                  contentPaddings.copy(layoutDirection, top = intSize.height.toDp())
                }

                headerMeasured = true
              }
              .fillMaxWidth()
              .wrapContentHeight()
              .align(Alignment.TopCenter)
              .background(chanTheme.backColorCompose)
              .consumeClicks(enabled = true)
              .zIndex(1f)
          ) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.padding(horizontal = 8.dp)) {
              header()
            }
            Spacer(modifier = Modifier.height(8.dp))

            val fullyScrolledTop by remember { derivedStateOf { scrollState.isFullyScrolledTop() } }
            if (!fullyScrolledTop) {
              KurobaComposeDivider(modifier = Modifier.fillMaxWidth())
            }
          }
        }

        LaunchedEffect(key1 = headerMeasured, key2 = footerMeasured) {
          if (!headerMeasured || !footerMeasured) {
            return@LaunchedEffect
          }

          alphaAnimatable.animateTo(1f, tween(durationMillis = 100))
        }

        Box(
          modifier = Modifier
            .graphicsLayer { alpha = alphaAnimatable.value }
        ) {
          body(contentPaddings)
        }

        Column(
          modifier = Modifier
            .onSizeChanged { intSize ->
              contentPaddings = with(density) {
                contentPaddings.copy(layoutDirection, bottom = intSize.height.toDp())
              }

              footerMeasured = true
            }
            .fillMaxWidth()
            .wrapContentHeight()
            .background(chanTheme.backColorCompose)
            .align(Alignment.BottomCenter)
            .consumeClicks(enabled = true)
            .zIndex(1f)
        ) {
          val isFullyScrolledBottom by remember { derivedStateOf { scrollState.isFullyScrolledBottom() } }
          if (!isFullyScrolledBottom) {
            KurobaComposeDivider(modifier = Modifier.fillMaxWidth())
          }

          scrollState.scrollIndicatorState

          Spacer(modifier = Modifier.height(8.dp))
          Box(modifier = Modifier.padding(horizontal = 8.dp)) {
            footer()
          }
          Spacer(modifier = Modifier.height(8.dp))
        }
      }
    }
  }
}