package com.github.k1rakishou.chan.features.site_archive

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.compose.viewModelProviderFactoryOf
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.ui.compose.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.ProvideChanTheme
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.pxToDp
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.google.accompanist.insets.ProvideWindowInsets
import javax.inject.Inject

class BoardArchiveController(
  context: Context,
  private val catalogDescriptor: ChanDescriptor.CatalogDescriptor,
  private val onThreadClicked: (ChanDescriptor.ThreadDescriptor) -> Unit
) : Controller(context), WindowInsetsListener {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private var blockClicking = false

  private var topPadding by mutableStateOf(0)
  private var bottomPadding by mutableStateOf(0)

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    navigation.title = "/${catalogDescriptor.boardCode()}/ Archive"

    globalWindowInsetsManager.addInsetsUpdatesListener(this)
    onInsetsChanged()

    view = ComposeView(context).apply {
      setContent {
        MaterialTheme {
          ProvideChanTheme(themeEngine) {
            ProvideWindowInsets {
              val backColor = remember(key1 = themeEngine.chanTheme.backColor) {
                Color(themeEngine.chanTheme.backColor)
              }

              Box(modifier = Modifier
                .padding(top = topPadding.dp, bottom = bottomPadding.dp)
                .fillMaxSize()
                .background(backColor)
              ) {
                BuildContent()
              }
            }
          }
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
  }

  override fun onInsetsChanged() {
    topPadding = pxToDp(getDimen(R.dimen.toolbar_height) + globalWindowInsetsManager.top())
    bottomPadding = pxToDp(getDimen(R.dimen.navigation_view_size) + globalWindowInsetsManager.bottom())
  }

  @Composable
  private fun BuildContent() {
    val viewModel = viewModel<BoardArchiveViewModel>(
      key = catalogDescriptor.serializeToString(),
      factory = viewModelProviderFactoryOf { BoardArchiveViewModel(catalogDescriptor) }
    )
    val boardArchiveControllerState = viewModel.state.collectAsState()

    val archiveThreads = when (val archiveThreadsAsync = boardArchiveControllerState.value.archiveThreadsAsync) {
      is AsyncData.NotInitialized -> {
        return
      }
      is AsyncData.Loading -> {
        KurobaComposeProgressIndicator()
        return
      }
      is AsyncData.Error -> {
        KurobaComposeErrorMessage(archiveThreadsAsync.throwable.errorMessageOrClassName())
        return
      }
      is AsyncData.Data -> archiveThreadsAsync.data
    }

    if (archiveThreads.isEmpty()) {
      KurobaComposeErrorMessage(errorMessage = "Nothing found")
      return
    }

    BuildListOfArchiveThreads(
      archiveThreads = archiveThreads
      ) { threadNo ->
        if (blockClicking) {
          return@BuildListOfArchiveThreads
        }

        val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(catalogDescriptor, threadNo)
        onThreadClicked(threadDescriptor)
        requireNavController().popController()

        blockClicking = true
      }
  }

  @Composable
  private fun BuildListOfArchiveThreads(
    archiveThreads: List<BoardArchiveViewModel.ArchiveThread>,
    onThreadClicked: (Long) -> Unit
  ) {
    val dividerColor = remember(key1 = themeEngine.chanTheme.dividerColor) {
      Color(themeEngine.chanTheme.dividerColor)
    }

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
    ) {
      items(count = archiveThreads.size) { index ->
        ArchiveThreadItem(index, archiveThreads[index], onThreadClicked)

        if (index >= 0 && index < archiveThreads.size) {
          Divider(
            modifier = Modifier.padding(horizontal = 2.dp),
            color = dividerColor,
            thickness = 1.dp
          )
        }
      }
    }
  }

  @Composable
  private fun ArchiveThreadItem(
    position: Int,
    archiveThread: BoardArchiveViewModel.ArchiveThread,
    onThreadClicked: (Long) -> Unit
  ) {
    Column(modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .clickable { onThreadClicked(archiveThread.threadNo) }
      .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
      val threadNo = remember(key1 = archiveThread.threadNo) {
        "#${position + 1} No. ${archiveThread.threadNo}"
      }

      KurobaComposeText(text = threadNo)
      KurobaComposeText(text = archiveThread.comment)
    }
  }

}