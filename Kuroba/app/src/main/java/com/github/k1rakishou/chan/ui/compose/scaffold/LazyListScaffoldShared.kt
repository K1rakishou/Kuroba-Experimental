package com.github.k1rakishou.chan.ui.compose.scaffold

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.compose.KurobaTextUnit
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.utils.appDependencies

interface LazyListScaffoldShared {
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
    negativeButton: LazyListScaffoldShared.Button?,
    positiveButton: LazyListScaffoldShared.Button?,
    extractButton: LazyListScaffoldShared.Button? = null
  ) {
    Row(
      modifier = modifier
    ) {
      if (extractButton != null) {
        Spacer(modifier = Modifier.width(8.dp))

        KurobaComposeTextBarButton(
          modifier = Modifier
            .wrapContentSize()
            .padding(vertical = 8.dp),
          enabled = extractButton.enabled,
          onClick = extractButton.onClick,
          text = extractButton.text,
          fontSize = extractButton.fontSize
        )
      }

      Spacer(modifier = Modifier.weight(1f))

      if (negativeButton != null) {
        KurobaComposeTextBarButton(
          modifier = Modifier
            .wrapContentSize()
            .padding(vertical = 8.dp),
          enabled = negativeButton.enabled,
          onClick = negativeButton.onClick,
          text = negativeButton.text,
          fontSize = negativeButton.fontSize
        )

        Spacer(modifier = Modifier.width(16.dp))
      }

      if (positiveButton != null) {
        KurobaComposeTextBarButton(
          modifier = Modifier
            .wrapContentSize()
            .padding(vertical = 8.dp),
          enabled = positiveButton.enabled,
          onClick = positiveButton.onClick,
          text = positiveButton.text,
          fontSize = positiveButton.fontSize
        )

        Spacer(modifier = Modifier.width(8.dp))
      }
    }
  }

  data class Button(
    val text: String,
    val enabled: Boolean = true,
    val fontSize: KurobaTextUnit = 14.ktu,
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