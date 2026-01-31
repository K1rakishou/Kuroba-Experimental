package com.github.k1rakishou.chan.features.filters

import android.content.Context
import android.net.Uri
import android.text.Html
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.Divider
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.BaseSelectionHelper
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.usecase.ExportFiltersUseCase
import com.github.k1rakishou.chan.core.usecase.ImportFiltersUseCase
import com.github.k1rakishou.chan.features.toolbar.BackArrowMenuItem
import com.github.k1rakishou.chan.features.toolbar.CloseMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMenuOverflowItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMiddleContent
import com.github.k1rakishou.chan.features.toolbar.ToolbarText
import com.github.k1rakishou.chan.ui.compose.SelectableItem
import com.github.k1rakishou.chan.ui.compose.addBottom
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeClickableText
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeDraggableElementContainer
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeErrorMessageNoInsets
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeSwitch
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.compose_task.rememberCancellableCoroutineTask
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.lazylist.LazyColumnWithFastScroller
import com.github.k1rakishou.chan.ui.compose.providers.ComposeEntrypoint
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.providers.LocalContentPaddings
import com.github.k1rakishou.chan.ui.compose.providers.LocalWindowInsets
import com.github.k1rakishou.chan.ui.compose.reorder.ReorderableItem
import com.github.k1rakishou.chan.ui.compose.reorder.ReorderableLazyListState
import com.github.k1rakishou.chan.ui.compose.reorder.detectReorder
import com.github.k1rakishou.chan.ui.compose.reorder.rememberReorderableLazyListState
import com.github.k1rakishou.chan.ui.compose.reorder.reorderable
import com.github.k1rakishou.chan.ui.compose.search.rememberSimpleSearchStateV2
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.controller.base.DeprecatedNavigationFlags
import com.github.k1rakishou.chan.ui.theme.SimpleSquarePainter
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.openLink
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.callback.FileChooserCallback
import com.github.k1rakishou.fsaf.callback.FileCreateCallback
import com.github.k1rakishou.model.data.filter.ChanFilter
import com.github.k1rakishou.model.data.filter.ChanFilterMutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormatterBuilder
import org.joda.time.format.ISODateTimeFormat
import javax.inject.Inject

class FiltersController(
  context: Context,
  private val chanFilterMutable: ChanFilterMutable?
) : Controller(context) {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var boardManager: BoardManager
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var fileChooser: FileChooser
  @Inject
  lateinit var exportFiltersUseCase: ExportFiltersUseCase
  @Inject
  lateinit var importFiltersUseCase: ImportFiltersUseCase

  private val viewModel by viewModelByKey<FiltersControllerViewModel>()

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    updateNavigationFlags(
      newNavigationFlags = DeprecatedNavigationFlags()
    )

    toolbarState.enterDefaultMode(
      leftItem = BackArrowMenuItem(
        onClick = { requireNavController().popController() }
      ),
      middleContent = ToolbarMiddleContent.Title(
        title = ToolbarText.Id(R.string.filters_screen)
      ),
      menuBuilder = {
        withMenuItem(
          id = ACTION_SEARCH,
          drawableId = R.drawable.ic_search_white_24dp,
          onClick = { item -> toolbarState.enterSearchMode() }
        )

        withOverflowMenu {
          withOverflowMenuItem(
            id = ACTION_ENABLE_OR_DISABLE_ALL_FILTERS,
            stringId = R.string.filters_controller_enable_all_filters,
            visible = false,
            onClick = { controllerScope.launch { viewModel.enableOrDisableAllFilters() } }
          )
          withOverflowMenuItem(
            id = ACTION_EXPORT_FILTERS,
            stringId = R.string.filters_controller_export_action,
            onClick = { item -> exportFilters(item) }
          )
          withOverflowMenuItem(
            id = ACTION_IMPORT_FILTERS,
            stringId = R.string.filters_controller_import_action,
            onClick = { item -> importFilters(item) }
          )
          withOverflowMenuItem(
            id = ACTION_SHOW_HELP,
            stringId = R.string.filters_controller_show_help,
            onClick = { item -> helpClicked(item) }
          )
        }
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
      viewModel.reloadFilters()
      viewModel.reloadFilterMatchedPosts()
    }

    controllerScope.launch {
      viewModel.updateEnableDisableAllFiltersButtonFlow
        .collect { updateEnableDisableAllFiltersButton() }
    }

    if (chanFilterMutable != null) {
      controllerScope.launch {
        viewModel.awaitUntilDependenciesInitialized()
        delay(32L)

        showCreateNewFilterController(chanFilterMutable)
      }
    }

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

  override fun onDestroy() {
    super.onDestroy()

    viewModel.viewModelSelectionHelper.unselectAll()
    requireBottomPanelContract().hideBottomPanel(controllerKey)
  }

  override fun onBack(): Boolean {
    if (viewModel.viewModelSelectionHelper.unselectAll()) {
      return true
    }

    return super.onBack()
  }

  @Composable
  private fun BuildContent() {
    val chanTheme = LocalChanTheme.current
    val windowInsets = LocalWindowInsets.current

    Box(
      modifier = Modifier.fillMaxSize()
    ) {
      BuildFilterList()

      val bottomPanelHeight by globalUiStateHolder.bottomPanel.bottomPanelHeight.collectAsState()

      val fabBottomPadding = maxOf(
        windowInsets.bottom,
        bottomPanelHeight
      )

      FloatingActionButton(
        modifier = Modifier
          .size(FAB_SIZE)
          .align(Alignment.BottomEnd)
          .offset(x = -FAB_MARGIN, y = -(fabBottomPadding + (FAB_MARGIN / 2))),
        backgroundColor = chanTheme.accentColorCompose,
        contentColor = Color.White,
        onClick = {
          viewModel.viewModelSelectionHelper.unselectAll()
          showCreateNewFilterController(null)
        }
      ) {
        Icon(
          painter = painterResource(id = R.drawable.ic_add_white_24dp),
          contentDescription = null
        )
      }
    }
  }

  @Composable
  private fun BuildFilterList() {
    val layoutDirection = LocalLayoutDirection.current
    val contentPaddings = LocalContentPaddings.current

    val searchState = rememberSimpleSearchStateV2<FiltersControllerViewModel.ChanFilterInfo>(
      textFieldState = toolbarState.search.searchQueryState
    )

    val filters by viewModel.filtersState
    val searchQuery = searchState.textFieldState.text

    LaunchedEffect(
      key1 = searchQuery,
      key2 = filters,
      block = {
        if (searchQuery.isEmpty()) {
          searchState.results.value = filters
          return@LaunchedEffect
        }

        delay(125L)

        withContext(Dispatchers.Default) {
          searchState.results.value = processSearchQuery(searchQuery, filters)
        }
      }
    )

    val searchResults by searchState.results

    val reorderTask = rememberCancellableCoroutineTask()
    val reorderableState = rememberReorderableLazyListState(
      onMove = { from, to -> reorderTask.launch { viewModel.reorderFilterInMemory(from.index, to.index) } },
      onDragEnd = { _, _ -> reorderTask.launch { viewModel.persistReorderedFilters() } }
    )

    val paddingValues = remember(contentPaddings, layoutDirection) {
      contentPaddings
        .asPaddingValues(controllerKey)
        .addBottom(layoutDirection, FAB_SIZE + FAB_MARGIN)
    }

    LazyColumnWithFastScroller(
      modifier = Modifier
        .fillMaxSize()
        .reorderable(reorderableState),
      state = reorderableState.listState,
      contentPadding = paddingValues,
      draggableScrollbar = false
    ) {
      if (searchResults.isEmpty()) {
        if (searchState.usingSearch) {
          item(
            key = "nothing_found_with_query",
            contentType = "nothing_found_with_query_item"
          ) {
            KurobaComposeErrorMessageNoInsets(
              modifier = Modifier.fillParentMaxSize(),
              errorMessage = stringResource(id = R.string.search_nothing_found_with_query, searchQuery)
            )
          }
        } else {
          item(
            key = "no_filters",
            contentType = "no_filters_item"
          ) {
            KurobaComposeErrorMessageNoInsets(
              modifier = Modifier.fillParentMaxSize(),
              errorMessage = stringResource(id = R.string.filter_controller_no_filters)
            )
          }
        }

        return@LazyColumnWithFastScroller
      }

      items(
        count = searchResults.size,
        key = { index -> searchResults[index].chanFilter.getDatabaseId() },
        contentType = { "filter_item" },
        itemContent = { index ->
          val chanFilterInfo = searchResults[index]

          BuildChanFilter(
            index = index,
            totalCount = searchResults.size,
            reorderableState = reorderableState,
            chanFilterInfo = chanFilterInfo,
            onFilterClicked = { clickedFilter ->
              if (viewModel.viewModelSelectionHelper.isInSelectionMode()) {
                viewModel.toggleSelection(clickedFilter)

                return@BuildChanFilter
              }

              showCreateNewFilterController(ChanFilterMutable.from(clickedFilter))
            },
            onFilterLongClicked = { clickedFilter ->
              if (toolbarState.isInSearchMode()) {
                return@BuildChanFilter
              }

              controllerViewOrNull()?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
              viewModel.toggleSelection(clickedFilter)
            }
          )
        }
      )
    }
  }

  @Composable
  private fun LazyItemScope.BuildChanFilter(
    index: Int,
    totalCount: Int,
    reorderableState: ReorderableLazyListState,
    chanFilterInfo: FiltersControllerViewModel.ChanFilterInfo,
    onFilterClicked: (ChanFilter) -> Unit,
    onFilterLongClicked: (ChanFilter) -> Unit,
  ) {
    val chanTheme = LocalChanTheme.current
    val selectionEvent by viewModel.viewModelSelectionHelper.collectSelectionModeAsState()
    val isInSelectionMode = selectionEvent?.isIsSelectionMode() ?: false
    val chanFilter = chanFilterInfo.chanFilter

    val coroutineScope = rememberCoroutineScope()

    ReorderableItem(
      reorderableState = reorderableState,
      orientationLocked = false,
      key = chanFilterInfo.chanFilter.getDatabaseId()
    ) { isDragging ->
      KurobaComposeDraggableElementContainer(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .kurobaClickable(
            bounded = true,
            onLongClick = { onFilterLongClicked(chanFilter) },
            onClick = { onFilterClicked(chanFilter) }
          ),
        isDragging = isDragging
      ) {
        SelectableItem(
          isInSelectionMode = isInSelectionMode,
          observeSelectionStateFunc = { viewModel.viewModelSelectionHelper.observeSelectionState(chanFilter) },
          onSelectionChanged = { viewModel.viewModelSelectionHelper.toggleSelection(chanFilter) }
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .wrapContentHeight()
          ) {
            val filterMatchedPostCountMap = viewModel.filterMatchedPostCountMap
            val filterMatchedPostCount = filterMatchedPostCountMap[chanFilterInfo.chanFilter.getDatabaseId()]

            val squareDrawableInlineContent = remember(key1 = chanFilterInfo) {
              getFilterInlineContentContent(chanFilterInfo = chanFilterInfo)
            }

            val fullText = remember(key1 = chanFilterInfo.filterText, key2 = filterMatchedPostCount) {
              return@remember formatFilterInfo(index, chanFilterInfo, filterMatchedPostCount, chanTheme)
            }

            KurobaComposeClickableText(
              modifier = Modifier
                .weight(1f)
                .wrapContentHeight()
                .padding(horizontal = 8.dp, vertical = 4.dp),
              fontSize = 12.ktu,
              text = fullText,
              inlineContent = squareDrawableInlineContent,
              onTextClicked = { textLayoutResult, position -> handleClickedText(textLayoutResult, position) }
            )

            Column(
              modifier = Modifier
                .fillMaxHeight()
                .wrapContentWidth()
                .padding(end = 8.dp)
            ) {
              KurobaComposeSwitch(
                modifier = Modifier
                  .wrapContentWidth()
                  .wrapContentHeight()
                  .padding(all = 4.dp),
                initiallyChecked = chanFilter.enabled,
                onCheckedChange = { nowChecked ->
                  if (isDragging) {
                    return@KurobaComposeSwitch
                  }

                  if (isInSelectionMode) {
                    return@KurobaComposeSwitch
                  }

                  coroutineScope.launch {
                    viewModel.enableOrDisableFilter(nowChecked, chanFilter)
                  }
                }
              )

              val reorderModifier = if (isInSelectionMode) {
                Modifier
              } else {
                Modifier.detectReorder(reorderableState)
              }

              KurobaComposeIcon(
                modifier = Modifier
                  .size(32.dp)
                  .padding(all = 4.dp)
                  .align(Alignment.CenterHorizontally)
                  .then(reorderModifier),
                drawableId = R.drawable.ic_baseline_reorder_24
              )
            }

          }

          if (index in 0 until (totalCount - 1)) {
            Divider(
              modifier = Modifier.padding(horizontal = 4.dp),
              color = chanTheme.dividerColorCompose,
              thickness = 1.dp
            )
          }
        }
      }
    }
  }

  private fun formatFilterInfo(
    index: Int,
    chanFilterInfo: FiltersControllerViewModel.ChanFilterInfo,
    filterMatchedPostCount: Int?,
    chanTheme: ChanTheme
  ): AnnotatedString {
    return buildAnnotatedString {
      append("#")
      append((index + 1).toString())
      append(" ")
      append("\n")

      append(chanFilterInfo.filterText)

      if (filterMatchedPostCount != null) {
        append("\n")

        append(
          AnnotatedString(
            getString(R.string.filter_matched_posts),
            SpanStyle(color = chanTheme.textColorSecondaryCompose)
          )
        )

        append(" ")
        append(filterMatchedPostCount.toString())
      }
    }
  }

  private fun handleClickedText(
    textLayoutResult: TextLayoutResult,
    position: Int
  ): Boolean {
    val linkAnnotation = textLayoutResult.layoutInput.text
      .getStringAnnotations(FiltersControllerViewModel.LINK_TAG, position, position)
      .firstOrNull()

    if (linkAnnotation != null) {
      val urlToOpen = textLayoutResult.layoutInput.text
        .substring(linkAnnotation.start, linkAnnotation.end)
        .toHttpUrlOrNull()

      if (urlToOpen != null) {
        openLink(urlToOpen.toString())
        return true
      }
    }

    return false
  }

  private fun importFilters(toolbarMenuOverflowItem: ToolbarMenuOverflowItem) {
    dialogFactory.createSimpleConfirmationDialog(
      context = context,
      titleText = getString(R.string.filters_controller_import_warning),
      descriptionText = getString(R.string.filters_controller_import_warning_description),
      positiveButtonText = getString(R.string.filters_controller_do_import),
      negativeButtonText = getString(R.string.filters_controller_do_not_import),
      onPositiveButtonClickListener = {
        fileChooser.openChooseFileDialog(object : FileChooserCallback() {
          override fun onCancel(reason: String) {
            showToast(R.string.canceled)
          }

          override fun onResult(uri: Uri) {
            controllerScope.launch {
              val params = ImportFiltersUseCase.Params(uri)

              when (val result = importFiltersUseCase.execute(params)) {
                is ModularResult.Value -> {
                  showToast(R.string.done)
                }
                is ModularResult.Error -> {
                  Logger.e(TAG, "importFilters()", result.error)

                  val message = getString(
                    R.string.filters_controller_import_error,
                    result.error.errorMessageOrClassName()
                  )

                  showToast(message)
                }
              }
            }
          }
        })
      }
    )
  }

  private fun exportFilters(toolbarMenuOverflowItem: ToolbarMenuOverflowItem) {
    val dateString = FILTER_DATE_FORMAT.print(DateTime.now())
    val exportFileName = "KurobaEx_exported_filters_($dateString).json"

    fileChooser.openCreateFileDialog(exportFileName, object : FileCreateCallback() {
      override fun onCancel(reason: String) {
        showToast(R.string.canceled)
      }

      override fun onResult(uri: Uri) {
        val params = ExportFiltersUseCase.Params(uri)

        when (val result = exportFiltersUseCase.execute(params)) {
          is ModularResult.Value -> {
            showToast(R.string.done)
          }
          is ModularResult.Error -> {
            Logger.e(TAG, "exportFilters()", result.error)

            val message = getString(
              R.string.filters_controller_export_error,
              result.error.errorMessageOrClassName()
            )

            showToast(message)
          }
        }
      }
    })
  }

  private fun updateEnableDisableAllFiltersButton() {
    toolbarState.findOverflowItem(ACTION_ENABLE_OR_DISABLE_ALL_FILTERS)?.let { menuItem ->
      val text = if (viewModel.allFiltersEnabled()) {
        getString(R.string.filters_controller_disable_all_filters)
      } else {
        getString(R.string.filters_controller_enable_all_filters)
      }

      menuItem.updateMenuText(text)
      menuItem.updateVisibility(viewModel.hasFilters())
    }
  }

  private fun helpClicked(item: ToolbarMenuOverflowItem) {
    DialogFactory.Builder.newBuilder(context, dialogFactory)
      .withTitle(R.string.help)
      .withDescription(Html.fromHtml(getString(R.string.filters_controller_help_message)))
      .withCancelable(true)
      .withNegativeButtonTextId(R.string.filters_controller_open_regex101)
      .withOnNegativeButtonClickListener { openLink("https://regex101.com/") }
      .create()
  }

  private fun showCreateNewFilterController(chanFilterMutable: ChanFilterMutable?) {
    val createOrUpdateFilterController = CreateOrUpdateFilterController(
      context = context,
      previousChanFilterMutable = chanFilterMutable,
      activeBoardsCountForAllSites = viewModel.activeBoardsCountForAllSites()
    )

    presentController(createOrUpdateFilterController)
  }

  private fun getFilterInlineContentContent(
    chanFilterInfo: FiltersControllerViewModel.ChanFilterInfo
  ): Map<String, InlineTextContent> {
    val placeholder = Placeholder(
      width = 12.sp,
      height = 12.sp,
      placeholderVerticalAlign = PlaceholderVerticalAlign.Center
    )

    val children: @Composable (String) -> Unit = {
      val dropdownArrowPainter = remember(key1 = chanFilterInfo.chanFilter.color) {
        return@remember SimpleSquarePainter(
          color = Color(chanFilterInfo.chanFilter.color),
        )
      }

      Image(
        modifier = Modifier.fillMaxSize(),
        painter = dropdownArrowPainter,
        contentDescription = null
      )
    }

    return mapOf(
      FiltersControllerViewModel.squareDrawableKey to InlineTextContent(placeholder = placeholder, children = children)
    )
  }

  private fun onMenuItemClicked(
    menuItemType: FiltersControllerViewModel.MenuItemType,
    selectedItems: List<ChanFilter>
  ) {
    if (selectedItems.isEmpty()) {
      return
    }

    when (menuItemType) {
      FiltersControllerViewModel.MenuItemType.Delete -> {
        controllerScope.launch { viewModel.deleteFilters(selectedItems) }
      }
    }
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
    val totalItemsCount = viewModel.filters.size

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

  private fun processSearchQuery(
    query: CharSequence,
    filters: List<FiltersControllerViewModel.ChanFilterInfo>
  ): List<FiltersControllerViewModel.ChanFilterInfo> {
    if (query.isEmpty()) {
      return filters
    }

    return filters
      .filter { chanFilterInfo -> chanFilterInfo.filterText.contains(query, ignoreCase = true) }
  }

  companion object {
    private const val TAG = "FiltersController"

    private val FAB_SIZE = 52.dp
    private val FAB_MARGIN = 16.dp

    private const val ACTION_EXPORT_FILTERS = 0
    private const val ACTION_IMPORT_FILTERS = 1
    private const val ACTION_SHOW_HELP = 2
    private const val ACTION_ENABLE_OR_DISABLE_ALL_FILTERS = 3
    private const val ACTION_SEARCH = 4

    private val FILTER_DATE_FORMAT = DateTimeFormatterBuilder()
      .append(ISODateTimeFormat.date())
      .toFormatter()
  }
}