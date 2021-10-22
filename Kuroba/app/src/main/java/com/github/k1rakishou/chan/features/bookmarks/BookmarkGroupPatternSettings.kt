package com.github.k1rakishou.chan.features.bookmarks

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers.consumeClicks
import com.github.k1rakishou.chan.ui.compose.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.kurobaClickable
import com.github.k1rakishou.chan.ui.controller.BaseFloatingComposeController

class BookmarkGroupPatternSettings(
  context: Context,
  private val bookmarkGroupId: String
) : BaseFloatingComposeController(context) {

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  @Composable
  override fun BoxScope.BuildContent() {
    val chanTheme = LocalChanTheme.current

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .align(Alignment.Center)
        .background(chanTheme.backColorCompose)
        .consumeClicks()
    ) {
      BuildContentInternal(
        onHelpClicked = { /* TODO(KurobaEx): */ }
      )
    }
  }

  @Composable
  private fun BuildContentInternal(
    onHelpClicked: () -> Unit
  ) {
    val onHelpClickedRemembered = rememberUpdatedState(newValue = onHelpClicked)

    Column(
      modifier = Modifier
        .wrapContentHeight()
        .fillMaxWidth()
    ) {
      KurobaComposeIcon(
        modifier = Modifier
          .align(Alignment.CenterHorizontally)
          .kurobaClickable(onClick = { onHelpClickedRemembered.value.invoke() }),
        drawableId = R.drawable.ic_help_outline_white_24dp,
        themeEngine = themeEngine
      )
    }
  }
}