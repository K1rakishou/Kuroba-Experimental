package com.github.k1rakishou.chan.features.my_posts

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.StartActivityStartupHandlerHelper
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers.simpleVerticalScrollbar
import com.github.k1rakishou.chan.ui.compose.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.ProvideChanTheme
import com.github.k1rakishou.chan.ui.controller.navigation.TabPageController
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController
import com.github.k1rakishou.chan.ui.toolbar.NavigationItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.google.accompanist.insets.ProvideWindowInsets
import com.google.accompanist.insets.imePadding
import javax.inject.Inject

class MyPostsController(context: Context) :
  TabPageController(context),
  ToolbarNavigationController.ToolbarSearchCallback {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val viewModel by lazy { (context as ComponentActivity).viewModels<MyPostsViewModel>().value }

  private val startActivityCallback: StartActivityStartupHandlerHelper.StartActivityCallbacks
    get() = (context as StartActivityStartupHandlerHelper.StartActivityCallbacks)

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun rebuildNavigationItem(navigationItem: NavigationItem) {
    navigationItem.title = AppModuleAndroidUtils.getString(R.string.controller_my_posts)
    navigationItem.swipeable = false

    navigationItem.buildMenu(context)
      .withItem(R.drawable.ic_search_white_24dp) { requireToolbarNavController().showSearch() }
      .build()
  }

  override fun onTabFocused() {
  }

  override fun canSwitchTabs(): Boolean {
    return true
  }

  override fun onSearchVisibilityChanged(visible: Boolean) {
    if (!visible) {
      viewModel.updateQueryAndReload(null)
    }
  }

  override fun onSearchEntered(entered: String) {
    viewModel.updateQueryAndReload(entered)
  }

  override fun onCreate() {
    super.onCreate()

    view = ComposeView(context).apply {
      setContent {
        ProvideChanTheme(themeEngine) {
          ProvideWindowInsets {
            val chanTheme = LocalChanTheme.current

            val backColor = remember(key1 = chanTheme.backColor) {
              Color(chanTheme.backColor)
            }

            Box(modifier = Modifier
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

  @Composable
  private fun BoxScope.BuildContent() {
    val myPostsViewModelState by viewModel.myPostsViewModelState.collectAsState()

    val savedRepliesGrouped = when (val savedRepliesAsync = myPostsViewModelState.savedRepliesGroupedAsync) {
      AsyncData.NotInitialized -> return
      AsyncData.Loading -> {
        KurobaComposeProgressIndicator()
        return
      }
      is AsyncData.Error -> {
        KurobaComposeErrorMessage(error = savedRepliesAsync.throwable)
        return
      }
      is AsyncData.Data -> savedRepliesAsync.data
    }

    BuildSavedRepliesList(
      savedRepliesGrouped = savedRepliesGrouped,
      onHeaderClicked = { threadDescriptor ->
        startActivityCallback.loadThreadAndMarkPost(
          postDescriptor = threadDescriptor.toOriginalPostDescriptor(),
          animated = true
        )
      },
      onReplyClicked = { postDescriptor ->
        startActivityCallback.loadThreadAndMarkPost(
          postDescriptor = postDescriptor,
          animated = true
        )
      },
    )
  }

  @Composable
  private fun BuildSavedRepliesList(
    savedRepliesGrouped: List<MyPostsViewModel.GroupedSavedReplies>,
    onHeaderClicked: (ChanDescriptor.ThreadDescriptor) -> Unit,
    onReplyClicked: (PostDescriptor) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
    val state = rememberLazyListState()

    val dividerColor = remember(key1 = chanTheme.dividerColorCompose) {
      chanTheme.dividerColorCompose
    }

    LazyColumn(
      state = state,
      modifier = Modifier
        .fillMaxSize()
        .simpleVerticalScrollbar(state, chanTheme)
        .padding(end = ComposeHelpers.SCROLLBAR_WIDTH)
        .imePadding()
    ) {
      if (savedRepliesGrouped.isEmpty()) {
        val searchQuery = viewModel.searchQuery
        if (searchQuery.isNullOrEmpty()) {
          item(key = "nothing_found_message") {
            KurobaComposeErrorMessage(
              errorMessage = stringResource(id = R.string.search_nothing_found)
            )
          }
        } else {
          item(key = "nothing_found_by_query_message_$searchQuery") {
            KurobaComposeErrorMessage(
              errorMessage = stringResource(id = R.string.search_nothing_found_with_query, searchQuery)
            )
          }
        }

        return@LazyColumn
      }

      savedRepliesGrouped.forEach { groupedSavedReplies ->
        item {
          GroupedSavedReplyHeader(groupedSavedReplies, chanTheme, onHeaderClicked)
        }

        items(groupedSavedReplies.savedReplyDataList.size) { index ->
          val groupedSavedReplyData = groupedSavedReplies.savedReplyDataList[index]
          GroupedSavedReply(groupedSavedReplyData, chanTheme, onReplyClicked)

          if (index < groupedSavedReplies.savedReplyDataList.lastIndex) {
            Divider(
              modifier = Modifier.padding(horizontal = 4.dp),
              color = dividerColor,
              thickness = 1.dp
            )
          }
        }
      }
    }
  }

  @Composable
  private fun GroupedSavedReplyHeader(
    groupedSavedReplies: MyPostsViewModel.GroupedSavedReplies,
    chanTheme: ChanTheme,
    onHeaderClicked: (ChanDescriptor.ThreadDescriptor) -> Unit
  ) {
    Box(modifier = Modifier
      .fillMaxSize()
      .defaultMinSize(minHeight = 42.dp)
      .background(chanTheme.backColorSecondaryCompose)
      .clickable { onHeaderClicked(groupedSavedReplies.threadDescriptor) }
      .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
      KurobaComposeText(
        text = groupedSavedReplies.headerTitle,
        fontSize = 12.sp,
        color = chanTheme.textColorSecondaryCompose,
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .align(Alignment.CenterStart)
      )
    }
  }

  @Composable
  private fun GroupedSavedReply(
    groupedSavedReplyData: MyPostsViewModel.SavedReplyData,
    chanTheme: ChanTheme,
    onReplyClicked: (PostDescriptor) -> Unit
  ) {
    Box(modifier = Modifier
      .fillMaxSize()
      .defaultMinSize(minHeight = 42.dp)
      .clickable { onReplyClicked(groupedSavedReplyData.postDescriptor) }
      .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
      Column(modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
      ) {
        KurobaComposeText(
          text = groupedSavedReplyData.postHeader,
          fontSize = 12.sp,
          maxLines = 1,
          color = chanTheme.textColorSecondaryCompose,
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
        )

        Spacer(modifier = Modifier.height(2.dp))

        KurobaComposeText(
          text = groupedSavedReplyData.comment,
          fontSize = 14.sp,
          maxLines = 5,
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
        )
      }
    }
  }

}