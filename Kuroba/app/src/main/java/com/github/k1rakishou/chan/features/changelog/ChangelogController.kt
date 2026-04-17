package com.github.k1rakishou.chan.features.changelog

import android.content.Context
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.compose.AsyncUiData
import com.github.k1rakishou.chan.core.di.component.controller.ControllerComponent
import com.github.k1rakishou.chan.features.toolbar.BackArrowMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMiddleContent
import com.github.k1rakishou.chan.features.toolbar.ToolbarText
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.lazylist.scrollbar
import com.github.k1rakishou.chan.ui.compose.plus
import com.github.k1rakishou.chan.ui.compose.providers.LocalContentPaddings
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarScope
import com.github.k1rakishou.chan.ui.controller.base.BaseComposeController
import com.github.k1rakishou.chan.ui.controller.base.DeprecatedNavigationFlags
import com.github.k1rakishou.chan.utils.ViewModelScope

class ChangelogController(context: Context) : BaseComposeController<
  ChangelogControllerViewModel,
  Nothing,
  Nothing
>(context, ChangelogControllerViewModel::class.java, null) {

  override val viewModelScope: ViewModelScope
    get() = ViewModelScope.ControllerScope(this)

  override fun injectControllerDependencies(component: ControllerComponent) {
    component.inject(this)
  }

  override val layoutAnchor: SnackbarScope.LayoutAnchor? = null

  override fun setupNavigation() {
    updateNavigationFlags(
      newNavigationFlags = DeprecatedNavigationFlags()
    )

    toolbarState.enterDefaultMode(
      leftItem = BackArrowMenuItem(
        onClick = { requireNavController().popController() }
      ),
      middleContent = ToolbarMiddleContent.Title(
        title = ToolbarText.String(
          appResources.string(
            R.string.changelog_controller_toolbar_title,
            BuildConfig.VERSION_NAME
          )
        ),
        subtitle = null
      )
    )
  }

  @Composable
  override fun ScreenContent() {
    val contentPaddings = LocalContentPaddings.current
    val layoutDirection = LocalLayoutDirection.current
    val paddings = remember(contentPaddings, layoutDirection) {
      return@remember contentPaddings.asPaddingValues().plus(
        layoutDirection = layoutDirection,
        other = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
      )
    }

    val changelogStateMut by viewModel.changelog
    val changelogText = when (val changelogState = changelogStateMut) {
      AsyncUiData.NotInitialized -> return
      AsyncUiData.Loading -> {
        KurobaComposeProgressIndicator(modifier = Modifier.fillMaxSize())
        return
      }
      is AsyncUiData.Error -> {
        KurobaComposeErrorMessage(
          modifier = Modifier.fillMaxSize(),
          error = changelogState.throwable
        )
        return
      }
      is AsyncUiData.UiData<AnnotatedString> -> changelogState.data
    }

    val scrollState = rememberScrollState()

    KurobaComposeText(
      modifier = Modifier
        .fillMaxSize()
        .scrollable(
          state = scrollState,
          orientation = Orientation.Vertical,
        )
        .scrollbar(
          paddings = paddings,
          scrollState = scrollState
        )
        .padding(paddings),
      text = changelogText,
    )
  }
}