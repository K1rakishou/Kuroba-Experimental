package com.github.k1rakishou.chan.features.thread_downloading

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.animatedVectorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.StartActivityStartupHandlerHelper
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers.simpleVerticalScrollbar
import com.github.k1rakishou.chan.ui.compose.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.ProvideChanTheme
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.thread.ThreadDownload
import com.google.accompanist.coil.rememberCoilPainter
import com.google.accompanist.insets.ProvideWindowInsets
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

class LocalArchiveController(context: Context) : Controller(context), ToolbarNavigationController.ToolbarSearchCallback {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2

  private val viewModel by lazy { requireComponentActivity().viewModelByKey<LocalArchiveViewModel>() }

  private val startActivityCallback: StartActivityStartupHandlerHelper.StartActivityCallbacks
    get() = (context as StartActivityStartupHandlerHelper.StartActivityCallbacks)

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    navigation.title = getString(R.string.controller_local_archive_title)
    navigation.swipeable = false
    navigation.hasDrawer = true
    navigation.hasBack = false

    navigation.buildMenu(context)
      .withItem(R.drawable.ic_search_white_24dp) { requireToolbarNavController().showSearch() }
      .withItem(R.drawable.ic_refresh_white_24dp) {
        mainScope.launch {
          ThreadDownloadingCoordinator.startOrRestartThreadDownloading(
            appContext = context.applicationContext,
            appConstants = appConstants,
            eager = true
          )

          showToast(R.string.controller_local_archive_updating_threads, Toast.LENGTH_LONG)
        }
      }
      .build()

    view = ComposeView(context).apply {
      setContent {
        ProvideChanTheme(themeEngine) {
          ProvideWindowInsets {
            val chanTheme = LocalChanTheme.current

            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(chanTheme.backColorCompose)
            ) {
              BuildContent()
            }
          }
        }
      }
    }
  }

  override fun onSearchVisibilityChanged(visible: Boolean) {
    if (!visible) {
      viewModel.updateQueryAndReload(null)
    }
  }

  override fun onSearchEntered(entered: String) {
    viewModel.updateQueryAndReload(entered)
  }

  override fun onDestroy() {
    super.onDestroy()

    requireToolbarNavController().closeSearch()
    viewModel.updateQueryAndReload(null)
  }

  @Composable
  private fun BoxScope.BuildContent() {
    val state by viewModel.state.collectAsState()

    val threadDownloadViews = when (val asyncData = state.threadDownloadsAsync) {
      AsyncData.NotInitialized -> return
      AsyncData.Loading -> {
        KurobaComposeProgressIndicator()
        return
      }
      is AsyncData.Error -> {
        KurobaComposeErrorMessage(error = asyncData.throwable)
        return
      }
      is AsyncData.Data -> asyncData.data
    }

    BuildThreadDownloadsList(
      threadDownloadViews = threadDownloadViews,
      onThreadDownloadClicked = { threadDescriptor ->
        startActivityCallback.loadThread(threadDescriptor, animated = true)
      }
    )
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun BuildThreadDownloadsList(
    threadDownloadViews: List<LocalArchiveViewModel.ThreadDownloadView>,
    onThreadDownloadClicked: (ChanDescriptor.ThreadDescriptor) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
    val state = rememberLazyListState()

    LazyVerticalGrid(
      state = state,
      cells = GridCells.Adaptive(260.dp),
      modifier = Modifier
        .fillMaxSize()
        .simpleVerticalScrollbar(state, chanTheme)
    ) {
      if (threadDownloadViews.isEmpty()) {
        val searchQuery = viewModel.searchQuery
        if (searchQuery.isNullOrEmpty()) {
          item {
            KurobaComposeErrorMessage(
              errorMessage = stringResource(id = R.string.search_nothing_found)
            )
          }
        } else {
          item {
            KurobaComposeErrorMessage(
              errorMessage = stringResource(id = R.string.search_nothing_found_with_query, searchQuery)
            )
          }
        }

        return@LazyVerticalGrid
      }

      items(threadDownloadViews.size) { index ->
        val threadDownloadView = threadDownloadViews[index]
        BuildThreadDownloadItem(
          threadDownloadView = threadDownloadView,
          onThreadDownloadClicked = onThreadDownloadClicked
        )
      }
    }
  }

  @Composable
  private fun BuildThreadDownloadItem(
    threadDownloadView: LocalArchiveViewModel.ThreadDownloadView,
    onThreadDownloadClicked: (ChanDescriptor.ThreadDescriptor) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current

    Box(modifier = Modifier
      .fillMaxWidth()
      .height(128.dp)
      .clickable { onThreadDownloadClicked(threadDownloadView.threadDescriptor) }
      .padding(2.dp)
      .background(chanTheme.backColorSecondaryCompose)
      .padding(4.dp)
    ) {
      val threadDescriptor = threadDownloadView.threadDescriptor

      Column(modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
      ) {
        KurobaComposeText(
          text = threadDownloadView.threadSubject,
          fontSize = 14.sp,
          color = chanTheme.postSubjectColorCompose,
          maxLines = 1,
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
        )

        Spacer(modifier = Modifier.height(2.dp))

        Row(modifier = Modifier
          .wrapContentHeight()
          .fillMaxWidth()
        ) {
          if (threadDownloadView.threadThumbnailUrl != null) {
            val context = LocalContext.current
            val errorDrawable = remember(key1 = chanTheme) {
              imageLoaderV2.getImageErrorLoadingDrawable(context)
            }

            Image(
              painter = rememberCoilPainter(
                request = threadDownloadView.threadThumbnailUrl,
                requestBuilder = { this.error(errorDrawable) }
              ),
              contentDescription = null,
              modifier = Modifier
                .fillMaxHeight()
                .width(60.dp)
                .align(Alignment.CenterVertically)
            )

            Spacer(modifier = Modifier.width(4.dp))
          }

          KurobaComposeText(
            text = threadDownloadView.threadDownloadInfo,
            fontSize = 12.sp,
            color = chanTheme.textColorHintCompose,
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f)
              .align(Alignment.CenterVertically)
          )

          val iconAlpha = if (threadDownloadView.status == ThreadDownload.Status.Completed) {
            0.5f
          } else {
            DefaultAlpha
          }

          Column(modifier = Modifier
            .wrapContentSize()
            .align(Alignment.CenterVertically)
          ) {
            BuildThreadDownloadStatusIcon(threadDownloadView, threadDescriptor, iconAlpha)
            BuildLastThreadUpdateStatusIcon(threadDownloadView, iconAlpha)
          }
        }
      }
    }
  }

  @Composable
  private fun BuildLastThreadUpdateStatusIcon(
    threadDownloadView: LocalArchiveViewModel.ThreadDownloadView,
    iconAlpha: Float
  ) {
    val downloadResultMsg = threadDownloadView.downloadResultMsg
    val isBackColorDark = LocalChanTheme.current.isBackColorDark

    val colorFilter = remember(key1 = isBackColorDark) {
      ColorFilter.tint(Color(themeEngine.resolveDrawableTintColor(isBackColorDark)))
    }

    if (downloadResultMsg == null) {
      Image(
        painter = painterResource(id = R.drawable.exo_ic_check),
        contentDescription = null,
        alpha = iconAlpha,
        colorFilter = colorFilter,
        modifier = Modifier
          .wrapContentSize(align = Alignment.Center)
          .clickable { showToast(R.string.controller_local_archive_thread_last_download_status_ok, Toast.LENGTH_LONG) }
      )
    } else {
      Image(
        painter = painterResource(id = R.drawable.ic_alert),
        contentDescription = null,
        alpha = iconAlpha,
        colorFilter = colorFilter,
        modifier = Modifier
          .wrapContentSize(align = Alignment.Center)
          .clickable {
            val message = getString(R.string.controller_local_archive_thread_last_download_status_error, downloadResultMsg)
            showToast(message, Toast.LENGTH_LONG)
          }
      )
    }
  }

  @OptIn(ExperimentalComposeUiApi::class)
  @Composable
  private fun BuildThreadDownloadStatusIcon(
    threadDownloadView: LocalArchiveViewModel.ThreadDownloadView,
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    iconAlpha: Float
  ) {
    val isBackColorDark = LocalChanTheme.current.isBackColorDark

    val colorFilter = remember(key1 = isBackColorDark) {
      ColorFilter.tint(Color(themeEngine.resolveDrawableTintColor(isBackColorDark)))
    }


    val painter = when (threadDownloadView.status) {
      ThreadDownload.Status.Running -> {
        var atEnd by remember(key1 = threadDescriptor) { mutableStateOf(false) }

        val painter = animatedVectorResource(id = R.drawable.ic_download_anim)
          .painterFor(atEnd = atEnd)

        LaunchedEffect(key1 = threadDescriptor) {
          while (isActive) {
            atEnd = !atEnd
            delay(3000)
          }
        }

        painter
      }
      ThreadDownload.Status.Stopped -> {
        painterResource(id = R.drawable.ic_download_anim0)
      }
      ThreadDownload.Status.Completed -> {
        painterResource(id = R.drawable.ic_download_anim1)
      }
    }

    Image(
      painter = painter,
      alpha = iconAlpha,
      contentDescription = null,
      colorFilter = colorFilter,
      modifier = Modifier
        .wrapContentSize(align = Alignment.Center)
        .clickable { }
    )
  }

}