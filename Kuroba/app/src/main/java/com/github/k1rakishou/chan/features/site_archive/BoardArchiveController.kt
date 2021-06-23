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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers.simpleVerticalScrollbar
import com.github.k1rakishou.chan.ui.compose.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.ProvideChanTheme
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.pxToDp
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.google.accompanist.insets.ProvideWindowInsets
import com.google.accompanist.insets.imePadding
import javax.inject.Inject

class BoardArchiveController(
  context: Context,
  private val catalogDescriptor: ChanDescriptor.CatalogDescriptor,
  private val onThreadClicked: (ChanDescriptor.ThreadDescriptor) -> Unit
) : Controller(context), WindowInsetsListener, ToolbarNavigationController.ToolbarSearchCallback {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private var blockClicking = false
  private var topPadding by mutableStateOf(0)
  private var bottomPadding by mutableStateOf(0)

  private val viewModel by lazy {
    val key = catalogDescriptor.serializeToString()
    requireComponentActivity().viewModelByKey(key, { BoardArchiveViewModel(catalogDescriptor) })
  }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    navigation.title = getString(R.string.controller_board_archive_title, catalogDescriptor.boardCode())

    navigation.buildMenu(context)
      .withItem(R.drawable.ic_search_white_24dp) { requireToolbarNavController().showSearch() }
      .build()

    globalWindowInsetsManager.addInsetsUpdatesListener(this)
    onInsetsChanged()

    view = ComposeView(context).apply {
      setContent {
        ProvideChanTheme(themeEngine) {
          ProvideWindowInsets {
            val chanTheme = LocalChanTheme.current

            Box(modifier = Modifier
              .fillMaxSize()
              .padding(top = topPadding.dp, bottom = bottomPadding.dp)
              .background(chanTheme.backColorCompose)
            ) {
              BuildContent()
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

  override fun onSearchVisibilityChanged(visible: Boolean) {
    if (!visible) {
      viewModel.updateQueryAndReload(null)
    }
  }

  override fun onSearchEntered(entered: String) {
    viewModel.updateQueryAndReload(entered)
  }

  @Composable
  private fun BuildContent() {
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
        KurobaComposeErrorMessage(archiveThreadsAsync.throwable)
        return
      }
      is AsyncData.Data -> archiveThreadsAsync.data
    }

    BuildListOfArchiveThreads(
      archiveThreads = archiveThreads,
      viewModel = viewModel
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
    viewModel: BoardArchiveViewModel,
    archiveThreads: List<BoardArchiveViewModel.ArchiveThread>,
    onThreadClicked: (Long) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current

    val state = rememberLazyListState()

    LazyColumn(
      state = state,
      modifier = Modifier
        .fillMaxSize()
        .simpleVerticalScrollbar(state, chanTheme)
        .imePadding()
    ) {
      if (archiveThreads.isEmpty()) {
        val searchQuery by viewModel.searchQuery
        if (searchQuery.isNullOrEmpty()) {
          item(key = "nothing_found_message") {
            KurobaComposeErrorMessage(
              errorMessage = stringResource(id = R.string.search_nothing_found)
            )
          }
        } else {
          item(key = "nothing_found_by_query_message_$searchQuery") {
            KurobaComposeErrorMessage(
              errorMessage = stringResource(id = R.string.search_nothing_found_with_query, searchQuery!!)
            )
          }
        }
      } else {
        items(count = archiveThreads.size) { index ->
          ArchiveThreadItem(index, archiveThreads[index], onThreadClicked)

          if (index >= 0 && index < archiveThreads.size) {
            Divider(
              modifier = Modifier.padding(horizontal = 2.dp),
              color = chanTheme.dividerColorCompose,
              thickness = 1.dp
            )
          }
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
    val chanTheme = LocalChanTheme.current

    Column(modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .clickable { onThreadClicked(archiveThread.threadNo) }
      .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
      val threadNo = remember(key1 = archiveThread.threadNo) {
        "#${position + 1} No. ${archiveThread.threadNo}"
      }

      KurobaComposeText(text = threadNo, color = chanTheme.textColorHintCompose, fontSize = 12.sp)
      KurobaComposeText(text = archiveThread.comment, fontSize = 14.sp)
    }
  }

}