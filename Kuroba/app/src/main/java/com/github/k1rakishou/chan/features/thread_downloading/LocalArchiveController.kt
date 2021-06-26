package com.github.k1rakishou.chan.features.thread_downloading

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.Checkbox
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
import androidx.compose.ui.draw.alpha
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
import com.github.k1rakishou.chan.core.base.BaseSelectionHelper
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.helper.StartActivityStartupHandlerHelper
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.features.drawer.MainControllerCallbacks
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

class LocalArchiveController(
  context: Context,
  private val mainControllerCallbacks: MainControllerCallbacks
) : Controller(context),
  ToolbarNavigationController.ToolbarSearchCallback {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2
  @Inject
  lateinit var dialogFactory: DialogFactory

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
      .withMenuItemClickInterceptor { viewModel.unselectAll() }
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

    mainScope.launch {
      viewModel.selectionMode.collect { selectionEvent ->
        onNewSelectionEvent(selectionEvent)
      }
    }

    mainScope.launch {
      viewModel.bottomPanelMenuItemClickEventFlow
        .collect { menuItemClickEvent ->
          onMenuItemClicked(menuItemClickEvent.archiveMenuItemType, menuItemClickEvent.items)
        }
    }

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

  override fun onBack(): Boolean {
    if (viewModel.unselectAll()) {
      return true
    }

    return super.onBack()
  }

  override fun onDestroy() {
    super.onDestroy()

    requireToolbarNavController().closeSearch()
    mainControllerCallbacks.hideBottomPanel()

    viewModel.updateQueryAndReload(null)
    viewModel.unselectAll()
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
        if (viewModel.isInSelectionMode()) {
          viewModel.toggleSelection(threadDescriptor)

          return@BuildThreadDownloadsList
        }

        startActivityCallback.loadThread(threadDescriptor, animated = true)
      },
      onThreadDownloadLongClicked = { threadDescriptor ->
        viewModel.toggleSelection(threadDescriptor)
      }
    )
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun BuildThreadDownloadsList(
    threadDownloadViews: List<LocalArchiveViewModel.ThreadDownloadView>,
    onThreadDownloadClicked: (ChanDescriptor.ThreadDescriptor) -> Unit,
    onThreadDownloadLongClicked: (ChanDescriptor.ThreadDescriptor) -> Unit
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
        val searchQuery = viewModel.searchQuery.value
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
          onThreadDownloadClicked = onThreadDownloadClicked,
          onThreadDownloadLongClicked = onThreadDownloadLongClicked
        )
      }
    }
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun BuildThreadDownloadItem(
    threadDownloadView: LocalArchiveViewModel.ThreadDownloadView,
    onThreadDownloadClicked: (ChanDescriptor.ThreadDescriptor) -> Unit,
    onThreadDownloadLongClicked: (ChanDescriptor.ThreadDescriptor) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
    val selectionEvent by viewModel.collectSelectionModeAsState()
    val isInSelectionMode = selectionEvent?.isIsSelectionMode() ?: false


    Box(modifier = Modifier
      .fillMaxWidth()
      .height(128.dp)
      .combinedClickable(
        onClick = { onThreadDownloadClicked(threadDownloadView.threadDescriptor) },
        onLongClick = { onThreadDownloadLongClicked(threadDownloadView.threadDescriptor) }
      )
      .padding(2.dp)
      .background(chanTheme.backColorSecondaryCompose)
      .padding(4.dp)
    ) {
      val threadDescriptor = threadDownloadView.threadDescriptor

      Row(modifier = Modifier.fillMaxSize()) {
        val transition = updateTransition(
          targetState = isInSelectionMode,
          label = "Selection mode transition"
        )

        val alpha by transition.animateFloat(label = "Selection mode alpha animation") { selection ->
          if (selection) {
            1f
          } else {
            0f
          }
        }

        val width by transition.animateDp(label = "Selection mode checkbox size animation") { selection ->
          if (selection) {
            32.dp
          } else {
            0.dp
          }
        }

        Box(
          modifier = Modifier
            .width(width)
            .alpha(alpha)
        ) {
          if (isInSelectionMode) {
            val checked by viewModel.observeSelectionState(threadDescriptor)

            Checkbox(
              checked = checked,
              onCheckedChange = { viewModel.toggleSelection(threadDescriptor) }
            )
          }
        }
        
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

  private fun onMenuItemClicked(
    archiveMenuItemType: LocalArchiveViewModel.ArchiveMenuItemType,
    selectedItems: List<ChanDescriptor.ThreadDescriptor>
  ) {
    if (selectedItems.isEmpty()) {
      return
    }

    when (archiveMenuItemType) {
      LocalArchiveViewModel.ArchiveMenuItemType.Delete -> {
        val title = if (selectedItems.size == 1) {
          getString(R.string.controller_local_archive_delete_one_thread, selectedItems.first().userReadableString())
        } else {
          getString(R.string.controller_local_archive_delete_many_threads, selectedItems.size)
        }

        val descriptionText = getString(R.string.controller_local_archive_delete_threads_description)

        dialogFactory.createSimpleConfirmationDialog(
          context,
          titleText = title,
          descriptionText = descriptionText,
          negativeButtonText = getString(R.string.cancel),
          positiveButtonText = getString(R.string.delete),
          onPositiveButtonClickListener = {
            viewModel.deleteDownloads(selectedItems)
          }
        )
      }
      LocalArchiveViewModel.ArchiveMenuItemType.Stop -> {
        viewModel.stopDownloads(selectedItems)
      }
      LocalArchiveViewModel.ArchiveMenuItemType.Start -> {
        viewModel.startDownloads(selectedItems)
      }
    }
  }

  private fun onNewSelectionEvent(selectionEvent: BaseSelectionHelper.SelectionEvent?) {
    when (selectionEvent) {
      BaseSelectionHelper.SelectionEvent.EnteredSelectionMode,
      BaseSelectionHelper.SelectionEvent.ItemSelectionToggled -> {
        mainControllerCallbacks.showBottomPanel(viewModel.getBottomPanelMenus())
        enterSelectionModeOrUpdate()
      }
      BaseSelectionHelper.SelectionEvent.ExitedSelectionMode -> {
        mainControllerCallbacks.hideBottomPanel()
        requireNavController().requireToolbar().exitSelectionMode()
      }
      null -> return
    }
  }

  private fun enterSelectionModeOrUpdate() {
    val navController = requireNavController()
    val toolbar = navController.requireToolbar()

    if (!toolbar.isInSelectionMode) {
      toolbar.enterSelectionMode(formatSelectionText())
      return
    }

    navigation.selectionStateText = formatSelectionText()
    toolbar.updateSelectionTitle(navigation)
  }

  private fun formatSelectionText(): String {
    require(viewModel.isInSelectionMode()) { "Not in selection mode" }

    return getString(
      R.string.controller_local_archive_selection_title,
      viewModel.selectedItemsCount()
    )
  }

}