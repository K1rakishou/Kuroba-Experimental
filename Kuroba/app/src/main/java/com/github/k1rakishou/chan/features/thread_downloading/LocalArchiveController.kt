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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Card
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.BaseSelectionHelper
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.compose.AsyncUiData
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.StartActivityStartupHandlerHelper
import com.github.k1rakishou.chan.core.image.ImageLoaderDeprecated
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.features.toolbar.BackArrowMenuItem
import com.github.k1rakishou.chan.features.toolbar.CloseMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMiddleContent
import com.github.k1rakishou.chan.features.toolbar.ToolbarText
import com.github.k1rakishou.chan.ui.compose.SelectableItem
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeMessage
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.image.ImageLoaderRequest
import com.github.k1rakishou.chan.ui.compose.image.ImageLoaderRequestData
import com.github.k1rakishou.chan.ui.compose.image.KurobaComposeImage
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.lazylist.LazyColumnWithFastScroller
import com.github.k1rakishou.chan.ui.compose.providers.ComposeEntrypoint
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.providers.LocalContentPaddings
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.controller.base.DeprecatedNavigationFlags
import com.github.k1rakishou.chan.ui.view.floating_menu.CheckableFloatingListMenuItem
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

class LocalArchiveController(
  context: Context,
  private val startActivityCallback: StartActivityStartupHandlerHelper.StartActivityCallbacks
) : Controller(context) {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var imageLoaderDeprecated: ImageLoaderDeprecated
  @Inject
  lateinit var fileChooser: FileChooser
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private val viewModel by viewModelByKey<LocalArchiveViewModel>()

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    updateNavigationFlags(
      newNavigationFlags = DeprecatedNavigationFlags(
        hasDrawer = true,
        hasBack = false
      )
    )

    toolbarState.enterDefaultMode(
      leftItem = BackArrowMenuItem(
        onClick = { requireNavController().popController() }
      ),
      middleContent = ToolbarMiddleContent.Title(
        title = ToolbarText.Id(R.string.controller_local_archive_title)
      ),
      iconClickInterceptor = {
        viewModel.viewModelSelectionHelper.unselectAll()
        return@enterDefaultMode false
      },
      menuBuilder = {
        withMenuItem(
          id = ACTION_SEARCH,
          drawableId = R.drawable.ic_search_white_24dp,
          onClick = { toolbarState.enterSearchMode() }
        )
        withMenuItem(
          id = ACTION_UPDATE_ALL,
          drawableId = R.drawable.ic_refresh_white_24dp,
          onClick = {
            toolbarState.findItem(ACTION_UPDATE_ALL)
              ?.spinItemOnce()

            onRefreshClicked()
          }
        )
        withMenuItem(
          id = ACTION_FILTER,
          drawableId = R.drawable.baseline_filter_alt_24,
          onClick = { onFilterClicked() }
        )
      }
    )

    controllerScope.launch {
      viewModel.viewModelSelectionHelper.selectionMode.collect { selectionEvent ->
        onNewSelectionEvent(selectionEvent)
      }
    }

    controllerScope.launch {
      viewModel.viewModelSelectionHelper.bottomPanelMenuItemClickEventFlow
        .collect { menuItemClickEvent ->
          onMenuItemClicked(menuItemClickEvent.menuItemType, menuItemClickEvent.items)
        }
    }

    controllerScope.launch {
      viewModel.controllerTitleInfoUpdatesFlow
        .debounce(1.seconds)
        .collect { controllerTitleInfo -> updateControllerTitle(controllerTitleInfo) }
    }

    controllerScope.launch {
      toolbarState.search.listenForSearchVisibilityUpdates()
        .onEach { searchVisible ->
          if (!searchVisible) {
            viewModel.updateQueryAndReload(null)
          }

          viewModel.onSearchVisibilityChanged(searchVisible)
        }
        .collect()
    }

    controllerScope.launch {
      toolbarState.search.listenForSearchQueryUpdates()
        .onEach { query -> viewModel.updateQueryAndReload(query) }
        .collect()
    }

    updateControllerTitle(viewModel.controllerTitleInfoUpdatesFlow.value)

    view = ComposeView(context).apply {
      setContent {
        ComposeEntrypoint {
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

  override fun onBack(): Boolean {
    if (viewModel.viewModelSelectionHelper.unselectAll()) {
      return true
    }

    return super.onBack()
  }

  override fun onDestroy() {
    super.onDestroy()

    requireBottomPanelContract().hideBottomPanel(controllerKey)

    viewModel.viewModelSelectionHelper.unselectAll()
  }

  @Composable
  private fun BoxScope.BuildContent() {
    val state by viewModel.state.collectAsState()

    val threadDownloadViews = when (val asyncData = state.threadDownloadsAsync) {
      AsyncUiData.NotInitialized -> return
      AsyncUiData.Loading -> {
        KurobaComposeProgressIndicator(
          modifier = Modifier.fillMaxSize()
        )

        return
      }
      is AsyncUiData.Error -> {
        KurobaComposeErrorMessage(
          modifier = Modifier.fillMaxSize(),
          error = asyncData.throwable
        )

        return
      }
      is AsyncUiData.UiData -> asyncData.data
    }

    BuildThreadDownloadsList(
      threadDownloadViews = threadDownloadViews,
      onThreadDownloadClicked = { threadDescriptor ->
        if (viewModel.viewModelSelectionHelper.isInSelectionMode()) {
          viewModel.viewModelSelectionHelper.toggleSelection(threadDescriptor)

          return@BuildThreadDownloadsList
        }

        withLayoutMode(
          phone = {
            requireNavController().popController {
              startActivityCallback.loadThread(threadDescriptor, true)
            }
          },
          tablet = {
            startActivityCallback.loadThread(threadDescriptor, true)
          }
        )
      },
      onThreadDownloadLongClicked = { threadDescriptor ->
        if (toolbarState.isInSearchMode()) {
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
    onThreadDownloadClicked: (ChanDescriptor.ThreadDescriptor) -> Unit,
    onThreadDownloadLongClicked: (ChanDescriptor.ThreadDescriptor) -> Unit
  ) {
    val contentPaddings = LocalContentPaddings.current

    val state = rememberLazyListState(
      initialFirstVisibleItemIndex = viewModel.rememberedFirstVisibleItemIndex,
      initialFirstVisibleItemScrollOffset = viewModel.rememberedFirstVisibleItemScrollOffset
    )

    DisposableEffect(
      key1 = Unit,
      effect = {
        onDispose {
          viewModel.updatePrevLazyListState(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
        }
      }
    )

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

    val paddingValues = remember(contentPaddings) {
      contentPaddings
        .asPaddingValues(controllerKey)
    }

    Column(
      modifier = Modifier.fillMaxSize()
    ) {
      LazyColumnWithFastScroller(
        state = state,
        modifier = Modifier
          .fillMaxSize(),
        contentPadding = paddingValues,
        draggableScrollbar = true
      ) {
        if (threadDownloadViews.isEmpty()) {
          val searchQuery = toolbarState.search.searchQueryState.text
          if (searchQuery.isNullOrEmpty()) {
            item(key = "error_nothing_found", contentType = "error") {
              KurobaComposeMessage(
                modifier = Modifier.fillParentMaxSize(),
                message = stringResource(id = R.string.search_nothing_found)
              )
            }
          } else {
            item(key = "error_nothing_found_with_query", contentType = "error") {
              KurobaComposeMessage(
                modifier = Modifier.fillParentMaxSize(),
                message = stringResource(id = R.string.search_nothing_found_with_query, searchQuery)
              )
            }
          }

          return@LazyColumnWithFastScroller
        }

        items(
          count = threadDownloadViews.size,
          key = { index -> threadDownloadViews[index].threadDescriptor },
          contentType = { "download_item" }
        ) { index ->
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

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun LazyItemScope.BuildThreadDownloadItem(
    animationAtEnd: Boolean,
    threadDownloadView: LocalArchiveViewModel.ThreadDownloadView,
    onThreadDownloadClicked: (ChanDescriptor.ThreadDescriptor) -> Unit,
    onThreadDownloadLongClicked: (ChanDescriptor.ThreadDescriptor) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
    val selectionEvent by viewModel.viewModelSelectionHelper.collectSelectionModeAsState()
    val isInSelectionMode = selectionEvent?.isIsSelectionMode() ?: false

    Card(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .padding(2.dp)
        .animateItem()
    ) {
      Box(
        modifier = Modifier
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
            fontSize = 14.ktu,
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
                modifier = Modifier
                  .height(100.dp)
                  .width(60.dp)
                  .alpha(contentAlpha),
                request = imageLoaderRequest,
                controllerKey = null,
                contentScale = ContentScale.Crop
              )

              Spacer(modifier = Modifier.width(4.dp))
            }

            KurobaComposeText(
              text = threadDownloadView.threadDownloadInfo,
              fontSize = 12.ktu,
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
              fontSize = 12.ktu,
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
        painter = painterResource(id = com.google.android.exoplayer2.ui.R.drawable.exo_ic_check),
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
            val message =
              getString(R.string.controller_local_archive_thread_last_download_status_error, downloadResultMsg)
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

  private fun onFilterClicked() {
    val floatingMenuItems = mutableListOf<FloatingListMenuItem>()
    val groupId = "view_mode"
    val currentViewMode = viewModel.viewMode.value

    floatingMenuItems += CheckableFloatingListMenuItem(
      key = ACTION_SHOW_ALL,
      name = appResources.string(R.string.controller_local_archive_show_all_threads),
      value = LocalArchiveViewModel.ViewMode.ShowAll,
      groupId = groupId,
      checked = currentViewMode == LocalArchiveViewModel.ViewMode.ShowAll
    )

    floatingMenuItems += CheckableFloatingListMenuItem(
      key = ACTION_SHOW_DOWNLOADING,
      name = appResources.string(R.string.controller_local_archive_show_downloading_threads),
      value = LocalArchiveViewModel.ViewMode.ShowDownloading,
      groupId = groupId,
      checked = currentViewMode == LocalArchiveViewModel.ViewMode.ShowDownloading
    )

    floatingMenuItems += CheckableFloatingListMenuItem(
      key = ACTION_SHOW_COMPLETED,
      name = appResources.string(R.string.controller_local_archive_show_completed_threads),
      value = LocalArchiveViewModel.ViewMode.ShowCompleted,
      groupId = groupId,
      checked = currentViewMode == LocalArchiveViewModel.ViewMode.ShowCompleted
    )

    val floatingMenuScreen = FloatingListMenuController(
      context = context,
      constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      items = floatingMenuItems,
      itemClickListener = { clickedItem ->
        val newViewMode = clickedItem.value as? LocalArchiveViewModel.ViewMode
        if (newViewMode == null || newViewMode == viewModel.viewMode.value) {
          return@FloatingListMenuController
        }

        viewModel.viewModelSelectionHelper.unselectAll()
        viewModel.viewMode.value = newViewMode
        viewModel.reload()
      }
    )

    presentController(floatingMenuScreen)
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
          FloatingListMenuItem(ACTION_EXPORT_THREAD_MEDIA, getString(R.string.controller_local_archive_export_thread_media)),
          FloatingListMenuItem(ACTION_EXPORT_THREADS, getString(R.string.controller_local_archive_export_threads)),
          FloatingListMenuItem(ACTION_EXPORT_THREAD_JSON, getString(R.string.controller_local_archive_export_thread_json))
        )

        val floatingListMenuController = FloatingListMenuController(
          context = context,
          constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
          items = items,
          itemClickListener = { clickedItem ->
            controllerScope.launch {
              when (clickedItem.key as Int) {
                ACTION_EXPORT_THREADS -> {
                  exportThreadAsHtml(selectedItems)
                }
                ACTION_EXPORT_THREAD_MEDIA -> {
                  exportThreadMedia(selectedItems)
                }
                ACTION_EXPORT_THREAD_JSON -> {
                  exportThreadAsJson(selectedItems)
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

        val job = controllerScope.launch(start = CoroutineStart.LAZY) {
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

  private fun exportThreadAsJson(threadDescriptors: List<ChanDescriptor.ThreadDescriptor>) {
    if (threadDescriptors.isEmpty()) {
      return
    }

    fileChooser.openChooseDirectoryDialog(object : DirectoryChooserCallback() {
      override fun onCancel(reason: String) {
        showToast(R.string.canceled)
      }

      override fun onResult(uri: Uri) {
        val loadingViewController = LoadingViewController(context, false)

        val job = controllerScope.launch(start = CoroutineStart.LAZY) {
          try {
            viewModel.exportThreadsAsJson(
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

        val job = controllerScope.launch(start = CoroutineStart.LAZY) {
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
      is BaseSelectionHelper.SelectionEvent.EnteredSelectionMode,
      is BaseSelectionHelper.SelectionEvent.ItemSelectionToggled -> {
        requireBottomPanelContract().showBottomPanel(controllerKey, viewModel.getBottomPanelMenus())
        enterSelectionModeOrUpdate()
      }
      BaseSelectionHelper.SelectionEvent.ExitedSelectionMode -> {
        requireBottomPanelContract().hideBottomPanel(controllerKey)

        if (toolbarState.isInSelectionMode()) {
          toolbarState.pop()
        }
      }
      null -> return
    }
  }

  private fun enterSelectionModeOrUpdate() {
    val selectedItemsCount = viewModel.viewModelSelectionHelper.selectedItemsCount()
    val totalItemsCount = (viewModel.state.value.threadDownloadsAsync as? AsyncUiData.UiData)?.data?.size ?: 0

    if (!toolbarState.isInSelectionMode()) {
      toolbarState.enterSelectionMode(
        leftItem = CloseMenuItem(
          onClick = {
            if (toolbarState.isInSelectionMode()) {
              toolbarState.pop()
              viewModel.viewModelSelectionHelper.unselectAll()
            }
          }
        ),
        selectedItemsCount = selectedItemsCount,
        totalItemsCount = totalItemsCount,
      )
    }

    toolbarState.selection.updateCounters(
      selectedItemsCount = selectedItemsCount,
      totalItemsCount = totalItemsCount,
    )
  }

  private fun updateControllerTitle(controllerTitleInfo: LocalArchiveViewModel.ControllerTitleInfo?) {
    if (controllerTitleInfo == null) {
      return
    }

    if (toolbarState.isInSelectionMode()) {
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

    toolbarState.default.updateTitle(
      newTitle = ToolbarText.String(titleString)
    )
  }

  private fun onRefreshClicked() {
    controllerScope.launch {
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

  companion object {
    private const val ACTION_SEARCH = 0
    private const val ACTION_UPDATE_ALL = 1
    private const val ACTION_FILTER = 2

    private const val ACTION_EXPORT_THREADS = 100
    private const val ACTION_EXPORT_THREAD_MEDIA = 101
    private const val ACTION_EXPORT_THREAD_JSON = 102

    private const val ACTION_SHOW_ALL = 200
    private const val ACTION_SHOW_DOWNLOADING = 201
    private const val ACTION_SHOW_COMPLETED = 202

    private val ICON_SIZE = 26.dp
    private val PROGRESS_SIZE = 20.dp
  }

}