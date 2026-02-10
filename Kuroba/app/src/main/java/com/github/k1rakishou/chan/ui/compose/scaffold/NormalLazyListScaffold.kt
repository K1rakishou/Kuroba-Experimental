package com.github.k1rakishou.chan.ui.compose.scaffold

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.github.k1rakishou.chan.ui.compose.consumeClicks
import com.github.k1rakishou.chan.ui.compose.copy
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.providers.LocalContentPaddings
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey

interface NormalLazyListScaffold : LazyListScaffoldShared

class NormalLazyListScaffoldBuilder : NormalLazyListScaffold {
  @Composable
  fun Content(
    boxScope: BoxScope,
    controllerKey: ControllerKey,
    header: (@Composable BoxScope.() -> Unit)? = null,
    body: @Composable BoxScope.(PaddingValues) -> Unit,
    footer: @Composable BoxScope.(Dp) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val contentPaddings = LocalContentPaddings.current

    val alphaAnimatable = remember { Animatable(initialValue = 0f) }
    var headerMeasured by remember { mutableStateOf(header == null) }
    var footerMeasured by remember { mutableStateOf(false) }

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

                headerMeasured = true
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
          body(lazyListPaddings)
        }

        Column(
          modifier = Modifier
            .onSizeChanged { intSize ->
              lazyListPaddings = with(density) {
                lazyListPaddings.copy(
                  layoutDirection = layoutDirection,
                  bottom = intSize.height.toDp()
                )
              }

              footerMeasured = true
            }
            .fillMaxWidth()
            .wrapContentHeight()
            .align(Alignment.BottomCenter)
            .consumeClicks(enabled = true)
            .zIndex(1f)
            .shadow(elevation = 4.dp)
        ) {
          Box {
            footer(contentPaddings.calculateBottomPadding(controllerKey))
          }
        }
      }
    }
  }
}