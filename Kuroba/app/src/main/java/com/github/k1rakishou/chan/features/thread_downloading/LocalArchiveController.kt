package com.github.k1rakishou.chan.features.thread_downloading

import android.content.Context
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.animatedVectorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.github.k1rakishou.chan.ui.compose.KurobaComposeCheckbox
import com.github.k1rakishou.chan.ui.compose.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.ProvideChanTheme
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.callback.FileCreateCallback
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.thread.ThreadDownload
import com.github.k1rakishou.model.util.ChanPostUtils
import com.google.accompanist.coil.rememberCoilPainter
import com.google.accompanist.insets.ProvideWindowInsets
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
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
  ToolbarNavigationController.ToolbarSearchCallback {

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
        viewModel.unselectAll()
        return@withMenuItemClickInterceptor true
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

    mainScope.launch {
      viewModel.controllerTitleInfoUpdatesFlow
        .debounce(Duration.seconds(1))
        .collect { controllerTitleInfo -> updateControllerTitle(controllerTitleInfo) }
    }

    updateControllerTitle(viewModel.controllerTitleInfoUpdatesFlow.value)

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
      onViewModeChanged = { newViewMode ->
        viewModel.unselectAll()

        if (newViewMode == viewModel.viewMode.value) {
          return@BuildThreadDownloadsList
        }

        viewModel.viewMode.value = newViewMode
        viewModel.reload()
      },
      threadDownloadViews = threadDownloadViews,
      onThreadDownloadClicked = { threadDescriptor ->
        if (viewModel.isInSelectionMode()) {
          viewModel.toggleSelection(threadDescriptor)

          return@BuildThreadDownloadsList
        }

        startActivityCallback.loadThread(threadDescriptor, animated = true)
      },
      onThreadDownloadLongClicked = { threadDescriptor ->
        controllerViewOrNull()?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        viewModel.toggleSelection(threadDescriptor)
      }
    )
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun BuildThreadDownloadsList(
    onViewModeChanged: (LocalArchiveViewModel.ViewMode) -> Unit,
    threadDownloadViews: List<LocalArchiveViewModel.ThreadDownloadView>,
    onThreadDownloadClicked: (ChanDescriptor.ThreadDescriptor) -> Unit,
    onThreadDownloadLongClicked: (ChanDescriptor.ThreadDescriptor) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
    val state = viewModel.lazyListState()

    var animationAtEnd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
      while (isActive) {
        animationAtEnd = !animationAtEnd
        delay(1500)
      }
    }

    Column(modifier = Modifier.fillMaxSize()) {
      BuildViewModelSelector(onViewModeChanged = onViewModeChanged)

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
      .height(26.dp)
      .padding(horizontal = 4.dp)
      .clip(RoundedCornerShape(4.dp))
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
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier
            .fillMaxHeight()
            .background(color = backgroundColor)
            .weight(weight = 0.33f)
            .clickable {
              onViewModeChanged(LocalArchiveViewModel.ViewMode.ShowAll)
            }
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
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          textAlign = TextAlign.Center,
          modifier = Modifier
            .fillMaxHeight()
            .background(color = backgroundColor)
            .weight(weight = 0.33f)
            .clickable {
              onViewModeChanged(LocalArchiveViewModel.ViewMode.ShowDownloading)
            }
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
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          textAlign = TextAlign.Center,
          modifier = Modifier
            .fillMaxHeight()
            .background(color = backgroundColor)
            .weight(weight = 0.33f)
            .clickable {
              onViewModeChanged(LocalArchiveViewModel.ViewMode.ShowCompleted)
            }
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
    val selectionEvent by viewModel.collectSelectionModeAsState()
    val isInSelectionMode = selectionEvent?.isIsSelectionMode() ?: false

    Box(modifier = Modifier
      .fillMaxWidth()
      .height(170.dp)
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

            KurobaComposeCheckbox(
              currentlyChecked = checked,
              onCheckChanged = { viewModel.toggleSelection(threadDescriptor) }
            )
          }
        }
        
        Column(modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
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

          Row(modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth()
          ) {
            val thumbnailLocation = threadDownloadView.thumbnailLocation
            if (thumbnailLocation != null) {
              val context = LocalContext.current
              val errorDrawable = remember(key1 = chanTheme) {
                imageLoaderV2.getImageErrorLoadingDrawable(context)
              }

              val request: Any = remember(key1 = thumbnailLocation) {
                when (thumbnailLocation) {
                  is LocalArchiveViewModel.ThreadDownloadThumbnailLocation.Local -> {
                    thumbnailLocation.file
                  }
                  is LocalArchiveViewModel.ThreadDownloadThumbnailLocation.Remote -> {
                    thumbnailLocation.url
                  }
                }
              }

              Image(
                painter = rememberCoilPainter(
                  request = request,
                  requestBuilder = { this.error(errorDrawable) }
                ),
                contentDescription = null,
                modifier = Modifier
                  .height(100.dp)
                  .width(60.dp)
                  .alpha(contentAlpha)
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

            Column(modifier = Modifier
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
    val downloadProgressEvent by viewModel.collectDownloadProgressEventsAsState(
      threadDescriptor = threadDownloadView.threadDescriptor
    )

    val isBackColorDark = LocalChanTheme.current.isBackColorDark
    val color = remember(key1 = isBackColorDark) {
      Color(themeEngine.resolveDrawableTintColor(isBackColorDark))
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
      ColorFilter.tint(Color(themeEngine.resolveDrawableTintColor(isBackColorDark)))
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

  @OptIn(ExperimentalComposeUiApi::class)
  @Composable
  private fun BuildThreadDownloadStatusIcon(
    animationAtEnd: Boolean,
    threadDownloadView: LocalArchiveViewModel.ThreadDownloadView,
    iconAlpha: Float
  ) {
    val isBackColorDark = LocalChanTheme.current.isBackColorDark

    val colorFilter = remember(key1 = isBackColorDark) {
      ColorFilter.tint(Color(themeEngine.resolveDrawableTintColor(isBackColorDark)))
    }

    val painter = when (threadDownloadView.status) {
      ThreadDownload.Status.Running -> {
        animatedVectorResource(id = R.drawable.ic_download_anim)
          .painterFor(atEnd = animationAtEnd)
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
      LocalArchiveViewModel.ArchiveMenuItemType.Export -> {
        val threadDescriptor = selectedItems.firstOrNull()
        if (threadDescriptor != null) {
          exportThreadAsHtml(threadDescriptor)
        }
      }
    }
  }

  private fun exportThreadAsHtml(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    val fileName = "${threadDescriptor.siteName()}_${threadDescriptor.boardCode()}_${threadDescriptor.threadNo}.zip"

    fileChooser.openCreateFileDialog(fileName, object : FileCreateCallback() {
      override fun onCancel(reason: String) {
        showToast(R.string.canceled)
      }

      override fun onResult(uri: Uri) {
        onFileSelected(uri, threadDescriptor)
      }
    })
  }

  private fun onFileSelected(uri: Uri, threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    val loadingViewController = LoadingViewController(context, true)

    val job = mainScope.launch(start = CoroutineStart.LAZY) {
      try {
        when (val result = viewModel.exportThreadAsHtml(uri, threadDescriptor)) {
          is ModularResult.Error -> showToast("Failed to export. Error: ${result.error.errorMessageOrClassName()}")
          is ModularResult.Value -> showToast("Successfully exported")
        }
      } finally {
        loadingViewController.stopPresenting()
      }
    }

    loadingViewController.enableBack {
      if (job.isActive) {
        job.cancel()
      }
    }

    presentController(loadingViewController)
    job.start()
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
    require(viewModel.isInSelectionMode()) { "Not in selection mode" }

    return getString(
      R.string.controller_local_archive_selection_title,
      viewModel.selectedItemsCount()
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

    private val ICON_SIZE = 26.dp
  }

}