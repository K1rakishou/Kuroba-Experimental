package com.github.k1rakishou.chan.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeDivider
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.utils.appDependencies

interface FloatingLazyListScaffold

class FloatingLazyListScaffoldBuilder : FloatingLazyListScaffold {
  @Composable
  fun Content(
    boxScope: BoxScope,
    lazyListState: LazyListState,
    header: (@Composable () -> Unit)? = null,
    body: @Composable (PaddingValues) -> Unit,
    footer: @Composable () -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

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
                  lazyListPaddings.copy(layoutDirection, top = intSize.height.toDp())
                }
              }
              .fillMaxWidth()
              .wrapContentHeight()
              .align(Alignment.TopCenter)
          ) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.padding(horizontal = 8.dp)) {
              header()
            }
            Spacer(modifier = Modifier.height(8.dp))

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
                lazyListPaddings.copy(layoutDirection, bottom = intSize.height.toDp())
              }
            }
            .fillMaxWidth()
            .wrapContentHeight()
            .align(Alignment.BottomCenter)
        ) {
          val isFullyScrolledBottom by remember { derivedStateOf { lazyListState.isFullyScrolledBottom() } }
          if (!isFullyScrolledBottom) {
            KurobaComposeDivider(modifier = Modifier.fillMaxWidth())
          }

          Spacer(modifier = Modifier.height(8.dp))
          Box(modifier = Modifier.padding(horizontal = 8.dp)) {
            footer()
          }
          Spacer(modifier = Modifier.height(8.dp))
        }
      }
    }
  }

  @Composable
  fun Header(modifier: Modifier, title: String) {
    Header(
      modifier = modifier,
      title = remember(key1 = title) { AnnotatedString(title) }
    )
  }

  @Composable
  fun Header(modifier: Modifier, title: AnnotatedString) {
    KurobaComposeText(
      modifier = modifier,
      text = title,
      fontSize = 18.ktu
    )
  }

  @Composable
  fun Footer(
    modifier: Modifier,
    negativeButton: Button,
    positiveButton: Button,
    extractButton: Button? = null
  ) {
    Row(
      modifier = modifier
    ) {
      if (extractButton != null) {
        KurobaComposeTextBarButton(
          modifier = Modifier
            .wrapContentSize(),
          enabled = extractButton.enabled,
          onClick = extractButton.onClick,
          text = extractButton.text
        )
      }

      Spacer(modifier = Modifier.weight(1f))

      KurobaComposeTextBarButton(
        modifier = Modifier
          .wrapContentSize(),
        enabled = negativeButton.enabled,
        onClick = negativeButton.onClick,
        text = negativeButton.text
      )

      Spacer(modifier = Modifier.width(16.dp))

      KurobaComposeTextBarButton(
        modifier = Modifier
          .wrapContentSize(),
        enabled = positiveButton.enabled,
        onClick = positiveButton.onClick,
        text = positiveButton.text
      )
    }
  }

  data class Button(
    val text: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit
  ) {
    companion object {
      fun ok(enabled: Boolean = true, onClick: () -> Unit): Button {
        return Button(
          text = appDependencies().appResources.string(R.string.ok),
          enabled = enabled,
          onClick = onClick
        )
      }

      fun cancel(enabled: Boolean = true, onClick: () -> Unit): Button {
        return Button(
          text = appDependencies().appResources.string(R.string.cancel),
          enabled = enabled,
          onClick = onClick
        )
      }
    }
  }
}