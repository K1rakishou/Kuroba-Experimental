package com.github.k1rakishou.chan.features.thread_downloading

import android.content.Context
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.base.BaseSelectionHelper
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.helper.StartActivityStartupHandlerHelper
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.features.drawer.MainControllerCallbacks
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers.simpleVerticalScrollbar
import com.github.k1rakishou.chan.ui.compose.ImageLoaderRequest
import com.github.k1rakishou.chan.ui.compose.ImageLoaderRequestData
import com.github.k1rakishou.chan.ui.compose.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.KurobaComposeImage
import com.github.k1rakishou.chan.ui.compose.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.ProvideChanTheme
import com.github.k1rakishou.chan.ui.compose.SelectableItem
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.callback.directory.DirectoryChooserCallback
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.thread.ThreadDownload
import com.github.k1rakishou.model.util.ChanPostUtils
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class LocalArchiveController(
  context: Context,
  private val mainControllerCallbacks: MainControllerCallbacks,
  private val startActivityCallback: StartActivityStartupHandlerHelper.StartActivityCallbacks
) : Controller(context),
  ToolbarNavigationController.ToolbarSearchCallback, WindowInsetsListener {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2
  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var fileChooser: FileChooser
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private val bottomPadding = mutableStateOf(0)
  private val viewModel by lazy { requireComponentActivity().viewModelByKey<LocalArchiveViewModel>() }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  @OptIn(ExperimentalTime::class)
  override fun onCreate() {
    super.onCreate()

    navigation.title = getString(R.string.controller_local_archive_title)
    navigation.swipeable = false
    navigation.hasDrawer = true
    navigation.hasBack = false

    navigation.buildMenu(context)
      .withMenuItemClickInterceptor {
        viewModel.viewModelSelectionHelper.unselectAll()
        return@withMenuItemClickInterceptor false
      }
      .withItem(ACTION_SEARCH, R.drawable.ic_search_white_24dp) { requireToolbarNavController().showSearch() }
      .withItem(ACTION_UPDATE_ALL, R.drawable.ic_refresh_white_24dp) {
        mainScope.launch {
          if (!viewModel.hasNotCompletedDownloads()) {
            showToast(getString(R.string.controller_local_archive_no_threads_to_update))
            return@launch
          }

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
      viewModel.viewModelSelectionHelper.selectionMode.collect { selectionEvent ->
        onNewSelectionEvent(selectionEvent)
      }
    }

    mainScope.launch {
      viewModel.viewModelSelectionHelper.bottomPanelMenuItemClickEventFlow
        .collect { menuItemClickEvent ->
          onMenuItemClicked(menuItemClickEvent.menuItemType, menuItemClickEvent.items)
        }
    }

    mainScope.launch {
      viewModel.controllerTitleInfoUpdatesFlow
        .debounce(Duration.seconds(1))
        .collect { controllerTitleInfo -> updateControllerTitle(controllerTitleInfo) }
    }

    globalWindowInsetsManager.addInsetsUpdatesListener(this)
    mainControllerCallbacks.onBottomPanelStateChanged { onInsetsChanged() }

    updateControllerTitle(viewModel.controllerTitleInfoUpdatesFlow.value)
    onInsetsChanged()

    view = ComposeView(context).apply {
      setContent {
        ProvideChanTheme(themeEngine) {
          Box(modifier = Modifier.fillMaxSize()) {
            BuildContent()
          }
        }
      }
    }
  }

  override fun onBack(): Boolean {
    if (viewModel.viewModelSelectionHelper.unselectAll()) {
      return true
    }

    return super.onBack()
  }

  override fun onDestroy() {
    super.onDestroy()

    globalWindowInsetsManager.removeInsetsUpdatesListener(this)

    toolbarNavControllerOrNull()?.closeSearch()
    mainControllerCallbacks.hideBottomPanel()

    viewModel.updateQueryAndReload(null)
    viewModel.viewModelSelectionHelper.unselectAll()
  }

  override fun onInsetsChanged() {
    bottomPadding.value = calculateBottomPaddingForRecyclerInDp(
      globalWindowInsetsManager = globalWindowInsetsManager,
      mainControllerCallbacks = mainControllerCallbacks
    )
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
      onViewModeChanged = { newViewMode ->
        viewModel.viewModelSelectionHelper.unselectAll()

        if (newViewMode == viewModel.viewMode.value) {
          return@BuildThreadDownloadsList
        }

        viewModel.viewMode.value = newViewMode
        viewModel.reload()
      },
      onThreadDownloadClicked = { threadDescriptor ->
        if (viewModel.viewModelSelectionHelper.isInSelectionMode()) {
          viewModel.viewModelSelectionHelper.toggleSelection(threadDescriptor)

          return@BuildThreadDownloadsList
        }

        startActivityCallback.loadThread(threadDescriptor, animated = true)
      },
      onThreadDownloadLongClicked = { threadDescriptor ->
        if (requireToolbarNavController().isSearchOpened) {
          return@BuildThreadDownloadsList
        }

        controllerViewOrNull()?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        viewModel.viewModelSelectionHelper.toggleSelection(threadDescriptor)
      }
    )
  }

  @Composable
  private fun BuildThreadDownloadsList(
    threadDownloadViews: List<LocalArchiveViewModel.ThreadDownloadView>,
    onViewModeChanged: (LocalArchiveViewModel.ViewMode) -> Unit,
    onThreadDownloadClicked: (ChanDescriptor.ThreadDescriptor) -> Unit,
    onThreadDownloadLongClicked: (ChanDescriptor.ThreadDescriptor) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
    val state = rememberLazyGridState(
      initialFirstVisibleItemIndex = viewModel.rememberedFirstVisibleItemIndex,
      initialFirstVisibleItemScrollOffset = viewModel.rememberedFirstVisibleItemScrollOffset
    )

    DisposableEffect(key1 = Unit, effect = {
      onDispose {
        viewModel.updatePrevLazyListState(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
      }
    })

    var animationAtEnd by remember { mutableStateOf(false) }
    val hasAnyRunningDownloads = remember(key1 = threadDownloadViews) {
      threadDownloadViews
        .any { threadDownloadView -> threadDownloadView.status.isRunning() }
    }

    if (hasAnyRunningDownloads) {
      LaunchedEffect(Unit) {
        while (isActive) {
          animationAtEnd = !animationAtEnd
          delay(1500)
        }
      }
    }

    Column(modifier = Modifier.fillMaxSize()) {
      BuildViewModelSelector(onViewModeChanged = onViewModeChanged)

      val padding by bottomPadding
      val bottomPadding = remember(key1 = padding) { PaddingValues(bottom = padding.dp) }

      LazyVerticalGrid(
        state = state,
        columns = GridCells.Adaptive(300.dp),
        contentPadding = bottomPadding,
        modifier = Modifier
          .fillMaxSize()
          .simpleVerticalScrollbar(state, chanTheme, bottomPadding)
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
            animationAtEnd = animationAtEnd,
            threadDownloadView = threadDownloadView,
            onThreadDownloadClicked = onThreadDownloadClicked,
            onThreadDownloadLongClicked = onThreadDownloadLongClicked
          )
        }
      }
    }
  }

  @Composable
  private fun BuildViewModelSelector(onViewModeChanged: (LocalArchiveViewModel.ViewMode) -> Unit) {
    val chanTheme = LocalChanTheme.current
    val highlightColor = chanTheme.postHighlightedColorCompose
    val viewMode by viewModel.viewMode

    Row(modifier = Modifier
      .fillMaxWidth()
      .height(32.dp)
    ) {
      kotlin.run {
        val backgroundColor = remember(key1 = viewMode) {
          if (viewMode == LocalArchiveViewModel.ViewMode.ShowAll) {
            highlightColor
          } else {
            Color.Unspecified
          }
        }

        KurobaComposeText(
          text = stringResource(id = R.string.controller_local_archive_show_all_threads),
          textAlign = TextAlign.Center,
          fontSize = 15.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier
            .fillMaxHeight()
            .background(color = backgroundColor)
            .weight(weight = 0.2f)
            .clickable { onViewModeChanged(LocalArchiveViewModel.ViewMode.ShowAll) }
            .padding(top = 4.dp)
        )
      }

      Divider(
        color = chanTheme.dividerColorCompose,
        modifier = Modifier
          .width(1.dp)
          .fillMaxHeight()
          .padding(vertical = 2.dp)
      )

      kotlin.run {
        val backgroundColor = remember(key1 = viewMode) {
          if (viewMode == LocalArchiveViewModel.ViewMode.ShowDownloading) {
            highlightColor
          } else {
            Color.Unspecified
          }
        }

        KurobaComposeText(
          text = stringResource(id = R.string.controller_local_archive_show_downloading_threads),
          fontSize = 15.sp,
          fontWeight = FontWeight.SemiBold,
          textAlign = TextAlign.Center,
          modifier = Modifier
            .fillMaxHeight()
            .background(color = backgroundColor)
            .weight(weight = 0.4f)
            .clickable { onViewModeChanged(LocalArchiveViewModel.ViewMode.ShowDownloading) }
            .padding(top = 4.dp)
        )
      }

      Divider(
        color = chanTheme.dividerColorCompose,
        modifier = Modifier
          .width(1.dp)
          .fillMaxHeight()
          .padding(vertical = 2.dp)
      )

      kotlin.run {
        val backgroundColor = remember(key1 = viewMode) {
          if (viewMode == LocalArchiveViewModel.ViewMode.ShowCompleted) {
            highlightColor
          } else {
            Color.Unspecified
          }
        }

        KurobaComposeText(
          text = stringResource(id = R.string.controller_local_archive_show_downloaded_threads),
          fontSize = 15.sp,
          fontWeight = FontWeight.SemiBold,
          textAlign = TextAlign.Center,
          modifier = Modifier
            .fillMaxHeight()
            .background(color = backgroundColor)
            .weight(weight = 0.4f)
            .clickable { onViewModeChanged(LocalArchiveViewModel.ViewMode.ShowCompleted) }
            .padding(top = 4.dp)
        )
      }
    }
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun BuildThreadDownloadItem(
    animationAtEnd: Boolean,
    threadDownloadView: LocalArchiveViewModel.ThreadDownloadView,
    onThreadDownloadClicked: (ChanDescriptor.ThreadDescriptor) -> Unit,
    onThreadDownloadLongClicked: (ChanDescriptor.ThreadDescriptor) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
    val selectionEvent by viewModel.viewModelSelectionHelper.collectSelectionModeAsState()
    val isInSelectionMode = selectionEvent?.isIsSelectionMode() ?: false

    Card(modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .padding(2.dp)
    ) {
      Box(modifier = Modifier
        .fillMaxWidth()
        .height(170.dp)
        .combinedClickable(
          onClick = { onThreadDownloadClicked(threadDownloadView.threadDescriptor) },
          onLongClick = { onThreadDownloadLongClicked(threadDownloadView.threadDescriptor) }
        )
        .background(chanTheme.backColorSecondaryCompose)
        .padding(4.dp)
      ) {
        val threadDescriptor = threadDownloadView.threadDescriptor

        SelectableItem(
          isInSelectionMode = isInSelectionMode,
          observeSelectionStateFunc = { viewModel.viewModelSelectionHelper.observeSelectionState(threadDescriptor) },
          onSelectionChanged = { viewModel.viewModelSelectionHelper.toggleSelection(threadDescriptor) }
        ) {
          val contentAlpha = remember(key1 = threadDownloadView.status) {
            when (threadDownloadView.status) {
              ThreadDownload.Status.Running -> DefaultAlpha
              ThreadDownload.Status.Stopped,
              ThreadDownload.Status.Completed -> 0.7f
            }
          }

          KurobaComposeText(
            text = threadDownloadView.threadSubject,
            fontSize = 14.sp,
            color = chanTheme.postSubjectColorCompose,
            maxLines = 2,
            modifier = Modifier
              .fillMaxWidth()
              .wrapContentHeight()
              .alpha(contentAlpha)
          )

          Spacer(modifier = Modifier.height(2.dp))

          Row(
            modifier = Modifier
              .wrapContentHeight()
              .fillMaxWidth()
          ) {
            val thumbnailLocation = threadDownloadView.thumbnailLocation
            if (thumbnailLocation != null) {
              val imageLoaderRequest = remember(key1 = thumbnailLocation) {
                val requestData = when (thumbnailLocation) {
                  is LocalArchiveViewModel.ThreadDownloadThumbnailLocation.Local -> {
                    ImageLoaderRequestData.File(thumbnailLocation.file)
                  }
                  is LocalArchiveViewModel.ThreadDownloadThumbnailLocation.Remote -> {
                    ImageLoaderRequestData.Url(
                      httpUrl = thumbnailLocation.url,
                      cacheFileType = CacheFileType.ThreadDownloaderThumbnail
                    )
                  }
                }

                return@remember ImageLoaderRequest(data = requestData)
              }

              KurobaComposeImage(
                request = imageLoaderRequest,
                modifier = Modifier
                  .height(100.dp)
                  .width(60.dp)
                  .alpha(contentAlpha),
                imageLoaderV2 = imageLoaderV2
              )

              Spacer(modifier = Modifier.width(4.dp))
            }

            KurobaComposeText(
              text = threadDownloadView.threadDownloadInfo,
              fontSize = 12.sp,
              color = chanTheme.textColorPrimaryCompose,
              modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .align(Alignment.CenterVertically)
                .alpha(contentAlpha)
            )

            Column(
              modifier = Modifier
                .wrapContentSize()
                .align(Alignment.CenterVertically)
            ) {
              BuildThreadDownloadStatusIcon(animationAtEnd, threadDownloadView, contentAlpha)
              BuildLastThreadUpdateStatusIcon(threadDownloadView, contentAlpha)
              BuildThreadDownloadProgressIcon(threadDownloadView, contentAlpha)
            }
          }

          val stats by viewModel.collectAdditionalThreadDownloadStats(threadDescriptor = threadDescriptor)
          if (stats != null) {
            Spacer(modifier = Modifier.weight(1f))

            val formattedDiskSize = remember(key1 = stats!!.mediaTotalDiskSize) {
              ChanPostUtils.getReadableFileSize(stats!!.mediaTotalDiskSize)
            }

            val statsText = stringResource(
              R.string.controller_local_archive_additional_thread_stats,
              stats!!.downloadedPostsCount,
              stats!!.downloadedMediaCount,
              formattedDiskSize
            )

            KurobaComposeText(
              text = statsText,
              fontSize = 12.sp,
              color = chanTheme.textColorHintCompose,
              modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .alpha(contentAlpha)
            )
          }
        }
      }
    }
  }

  @Composable
  private fun BuildThreadDownloadProgressIcon(
    threadDownloadView: LocalArchiveViewModel.ThreadDownloadView,
    contentAlpha: Float
  ) {
    val downloadProgressEvent by viewModel.collectDownloadProgressEventsAsState(threadDownloadView.threadDescriptor)
      .collectAsState(ThreadDownloadProgressNotifier.Event.Empty)

    val isBackColorDark = LocalChanTheme.current.isBackColorDark
    val color = remember(key1 = isBackColorDark) {
      ThemeEngine.resolveDrawableTintColorCompose(isBackColorDark)
    }

    Box(modifier = Modifier
      .size(ICON_SIZE)
      .padding(4.dp)) {
      if (downloadProgressEvent is ThreadDownloadProgressNotifier.Event.Progress) {
        val percent = (downloadProgressEvent as ThreadDownloadProgressNotifier.Event.Progress).percent
        val sweepAngle = remember(key1 = percent) { 360f * percent }

        Canvas(modifier = Modifier.fillMaxSize()) {
          drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = sweepAngle,
            useCenter = false,
            alpha = contentAlpha,
            style = Stroke(width = 8f)
          )
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
      ColorFilter.tint(ThemeEngine.resolveDrawableTintColorCompose(isBackColorDark))
    }

    if (downloadResultMsg == null) {
      Image(
        painter = painterResource(id = R.drawable.exo_ic_check),
        contentDescription = null,
        alpha = iconAlpha,
        colorFilter = colorFilter,
        modifier = Modifier
          .size(ICON_SIZE)
          .clickable { showToast(R.string.controller_local_archive_thread_last_download_status_ok, Toast.LENGTH_LONG) }
      )
    } else {
      Image(
        painter = painterResource(id = R.drawable.ic_alert),
        contentDescription = null,
        alpha = iconAlpha,
        colorFilter = colorFilter,
        modifier = Modifier
          .size(ICON_SIZE)
          .clickable {
            val message = getString(R.string.controller_local_archive_thread_last_download_status_error, downloadResultMsg)
            showToast(message, Toast.LENGTH_LONG)
          }
      )
    }
  }

  @OptIn(ExperimentalComposeUiApi::class, ExperimentalAnimationGraphicsApi::class)
  @Composable
  private fun ColumnScope.BuildThreadDownloadStatusIcon(
    animationAtEnd: Boolean,
    threadDownloadView: LocalArchiveViewModel.ThreadDownloadView,
    iconAlpha: Float
  ) {
    val isBackColorDark = LocalChanTheme.current.isBackColorDark

    val color = remember(key1 = isBackColorDark) {
      ThemeEngine.resolveDrawableTintColorCompose(isBackColorDark)
    }

    val colorFilter = remember(key1 = isBackColorDark) {
      ColorFilter.tint(color)
    }

    val painter = when (threadDownloadView.status) {
      ThreadDownload.Status.Running -> {
        rememberAnimatedVectorPainter(
          animatedImageVector = AnimatedImageVector.animatedVectorResource(id = R.drawable.ic_download_anim),
          atEnd = animationAtEnd
        )
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
        .size(ICON_SIZE)
        .clickable { showToast(threadDownloadView.status.toString(), Toast.LENGTH_LONG) }
    )
  }

  private fun onMenuItemClicked(
    menuItemType: LocalArchiveViewModel.MenuItemType,
    selectedItems: List<ChanDescriptor.ThreadDescriptor>
  ) {
    if (selectedItems.isEmpty()) {
      return
    }

    when (menuItemType) {
      LocalArchiveViewModel.MenuItemType.Delete -> {
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
      LocalArchiveViewModel.MenuItemType.Stop -> {
        viewModel.stopDownloads(selectedItems)
      }
      LocalArchiveViewModel.MenuItemType.Start -> {
        viewModel.startDownloads(selectedItems)
      }
      LocalArchiveViewModel.MenuItemType.Export -> {
        val items = listOf(
          FloatingListMenuItem(ACTION_EXPORT_THREADS, getString(R.string.controller_local_archive_export_threads)),
          FloatingListMenuItem(ACTION_EXPORT_THREAD_MEDIA, getString(R.string.controller_local_archive_export_thread_media))
        )

        val floatingListMenuController = FloatingListMenuController(
          context = context,
          constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
          items = items,
          itemClickListener = { clickedItem ->
            mainScope.launch {
              when (clickedItem.key as Int) {
                ACTION_EXPORT_THREADS -> {
                  exportThreadAsHtml(selectedItems)
                }
                ACTION_EXPORT_THREAD_MEDIA -> {
                  exportThreadMedia(selectedItems)
                }
              }
            }
          }
        )

        presentController(floatingListMenuController)
      }
    }
  }

  private fun exportThreadAsHtml(threadDescriptors: List<ChanDescriptor.ThreadDescriptor>) {
    if (threadDescriptors.isEmpty()) {
      return
    }

    fileChooser.openChooseDirectoryDialog(object : DirectoryChooserCallback() {
      override fun onCancel(reason: String) {
        showToast(R.string.canceled)
      }

      override fun onResult(uri: Uri) {
        val loadingViewController = LoadingViewController(context, false)

        val job = mainScope.launch(start = CoroutineStart.LAZY) {
          try {
            viewModel.exportThreadsAsHtml(
              outputDirUri = uri,
              threadDescriptors = threadDescriptors,
              onUpdate = { exported, total ->
                val text = context.resources.getString(R.string.controller_local_archive_exported_format, exported, total)
                loadingViewController.updateWithText(text)
              }
            )
              .toastOnError(message = { error -> "Failed to export. Error: ${error.errorMessageOrClassName()}" })
              .toastOnSuccess(message = { "Successfully exported" })
              .ignore()
          } finally {
            loadingViewController.stopPresenting()
          }
        }

        loadingViewController.enableCancellation {
          if (job.isActive) {
            job.cancel()
          }
        }

        presentController(loadingViewController)
        job.start()
      }
    })
  }

  private suspend fun exportThreadMedia(threadDescriptors: List<ChanDescriptor.ThreadDescriptor>) {
    fileChooser.openChooseDirectoryDialog(object : DirectoryChooserCallback() {
      override fun onCancel(reason: String) {
        showToast(R.string.canceled)
      }

      override fun onResult(uri: Uri) {
        val loadingViewController = LoadingViewController(context, false)

        val job = mainScope.launch(start = CoroutineStart.LAZY) {
          try {
            viewModel.exportThreadsMedia(
              outputDirectoryUri = uri,
              threadDescriptors = threadDescriptors,
              onUpdate = { exported, total ->
                val text = context.resources.getString(R.string.controller_local_archive_exported_format, exported, total)
                loadingViewController.updateWithText(text)
              }
            )
              .toastOnError(message = { error -> "Failed to export. Error: ${error.errorMessageOrClassName()}" })
              .toastOnSuccess(message = { "Successfully exported" })
              .ignore()
          } finally {
            loadingViewController.stopPresenting()
          }
        }

        loadingViewController.enableCancellation {
          if (job.isActive) {
            job.cancel()
          }
        }

        presentController(loadingViewController)
        job.start()
      }
    })
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
    val toolbar = requireNavController().requireToolbar()
    if (!toolbar.isInSelectionMode) {
      toolbar.enterSelectionMode(formatSelectionText())
      return
    }

    navigation.selectionStateText = formatSelectionText()
    toolbar.updateSelectionTitle(navigation)
  }

  private fun formatSelectionText(): String {
    require(viewModel.viewModelSelectionHelper.isInSelectionMode()) { "Not in selection mode" }

    return getString(
      R.string.controller_local_archive_selection_title,
      viewModel.viewModelSelectionHelper.selectedItemsCount()
    )
  }

  private fun updateControllerTitle(controllerTitleInfo: LocalArchiveViewModel.ControllerTitleInfo?) {
    if (controllerTitleInfo == null) {
      return
    }

    val toolbar = requireNavController().requireToolbar()
    if (toolbar.isInSelectionMode) {
      return
    }

    val titleString = if (controllerTitleInfo.totalDownloads <= 0) {
      getString(R.string.controller_local_archive_title)
    } else {
      getString(
        R.string.controller_local_archive_title_updated,
        controllerTitleInfo.activeDownloads,
        controllerTitleInfo.totalDownloads
      )
    }

    navigation.title = titleString

    toolbar.updateTitle(navigation)
  }

  companion object {
    private const val ACTION_SEARCH = 0
    private const val ACTION_UPDATE_ALL = 1

    private const val ACTION_EXPORT_THREADS = 100
    private const val ACTION_EXPORT_THREAD_MEDIA = 101

    private val ICON_SIZE = 26.dp
    private val PROGRESS_SIZE = 20.dp
  }

}