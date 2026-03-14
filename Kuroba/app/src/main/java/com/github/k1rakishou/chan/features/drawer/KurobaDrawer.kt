package com.github.k1rakishou.chan.features.drawer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateInt
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.features.drawer.data.HistoryControllerState
import com.github.k1rakishou.chan.features.drawer.data.NavHistoryBookmarkAdditionalInfo
import com.github.k1rakishou.chan.features.drawer.data.NavigationHistoryEntry
import com.github.k1rakishou.chan.ui.compose.components.IconTint
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeMessage
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeSelectionIndicator
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.KurobaSearchInput
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.image.ImageLoaderRequest
import com.github.k1rakishou.chan.ui.compose.image.ImageLoaderRequestData
import com.github.k1rakishou.chan.ui.compose.image.KurobaComposeImage
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.lazylist.LazyColumnWithFastScroller
import com.github.k1rakishou.chan.ui.compose.lazylist.LazyVerticalGridWithFastScroller
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.providers.LocalWindowInsets
import com.github.k1rakishou.chan.ui.compose.search.SimpleSearchStateV2
import com.github.k1rakishou.chan.ui.compose.search.rememberSimpleSearchStateV2
import com.github.k1rakishou.chan.ui.compose.textAsFlow
import com.github.k1rakishou.chan.ui.helper.awaitWhile
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun KurobaDrawer(
  mainControllerViewModel: MainControllerViewModel,
  onSwitchDayNightThemeIconClick: () -> Unit,
  onShowDrawerOptionIconClick: () -> Unit,
  onHistoryEntryViewClicked: (NavigationHistoryEntry) -> Unit,
  onHistoryEntryViewLongClicked: (NavigationHistoryEntry) -> Unit,
  onHistoryDeleteClicked: (NavigationHistoryEntry) -> Unit,
  onHistoryEntrySelectionChanged: (Boolean, NavigationHistoryEntry) -> Unit,
  kurobaComposeBottomPanel: @Composable () -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val bgColor = chanTheme.backColorCompose

  val kurobaDrawerState = mainControllerViewModel.kurobaDrawerState
  val historyControllerState by kurobaDrawerState.historyControllerState
  val searchTextFieldState = kurobaDrawerState.searchTextFieldState

  val searchState = rememberSimpleSearchStateV2<NavigationHistoryEntry>(textFieldState = searchTextFieldState)
  val coroutineScope = rememberCoroutineScope()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(bgColor)
  ) {
    BuildNavigationHistoryListHeader(
      textFieldState = searchState.textFieldState,
      onSwitchDayNightThemeIconClick = onSwitchDayNightThemeIconClick,
      onShowDrawerOptionsIconClick = { onShowDrawerOptionIconClick() }
    )

    when (historyControllerState) {
      HistoryControllerState.Loading -> {
        KurobaComposeProgressIndicator(
          modifier = Modifier.fillMaxSize()
        )
      }
      is HistoryControllerState.Error -> {
        KurobaComposeMessage(
          modifier = Modifier.fillMaxSize(),
          message = (historyControllerState as HistoryControllerState.Error).errorText
        )
      }
      is HistoryControllerState.Data -> {
        val navHistoryEntryList = remember { kurobaDrawerState.navigationHistoryEntryList }
        if (navHistoryEntryList.isEmpty()) {
          KurobaComposeText(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            text = stringResource(id = R.string.drawer_controller_navigation_history_is_empty),
            textAlign = TextAlign.Center,
          )
        } else {
          BuildNavigationHistoryList(
            mainControllerViewModel = mainControllerViewModel,
            kurobaDrawerState = kurobaDrawerState,
            navHistoryEntryList = navHistoryEntryList,
            searchState = searchState,
            onHistoryEntryViewClicked = { navHistoryEntry ->
              onHistoryEntryViewClicked(navHistoryEntry)

              coroutineScope.launch {
                delay(100L)
                searchState.reset()
              }
            },
            onHistoryEntryViewLongClicked = { navHistoryEntry ->
              onHistoryEntryViewLongClicked(navHistoryEntry)
            },
            onHistoryEntrySelectionChanged = { currentlySelected, navHistoryEntry ->
              onHistoryEntrySelectionChanged(currentlySelected, navHistoryEntry)
            },
            onNavHistoryDeleteClicked = onHistoryDeleteClicked
          )
        }
      }
    }

    kurobaComposeBottomPanel()
  }
}

@Composable
private fun ColumnScope.BuildNavigationHistoryList(
  mainControllerViewModel: MainControllerViewModel,
  kurobaDrawerState: KurobaDrawerState,
  navHistoryEntryList: List<NavigationHistoryEntry>,
  searchState: SimpleSearchStateV2<NavigationHistoryEntry>,
  onHistoryEntryViewClicked: (NavigationHistoryEntry) -> Unit,
  onHistoryEntryViewLongClicked: (NavigationHistoryEntry) -> Unit,
  onHistoryEntrySelectionChanged: (Boolean, NavigationHistoryEntry) -> Unit,
  onNavHistoryDeleteClicked: (NavigationHistoryEntry) -> Unit
) {
  var searchQueryIsEmpty by remember { mutableStateOf(false) }
  val currentNavHistoryEntryList by rememberUpdatedState(newValue = navHistoryEntryList)

  LaunchedEffect(
    key1 = searchState,
    key2 = currentNavHistoryEntryList,
    block = {
      searchState.textFieldState.textAsFlow()
        .onEach { query ->
          delay(125)

          if (query.isEmpty()) {
            searchState.results.value = currentNavHistoryEntryList
            searchQueryIsEmpty = query.isEmpty()
            return@onEach
          }

          withContext(Dispatchers.Default) {
            searchState.results.value = processSearchQuery(query, currentNavHistoryEntryList)
            searchQueryIsEmpty = query.isEmpty()
          }
        }
        .collect()
    }
  )

  val searchResults by searchState.results
  if (searchResults.isEmpty()) {
    KurobaComposeText(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .padding(8.dp),
      textAlign = TextAlign.Center,
      text = stringResource(id = R.string.search_nothing_found_with_query, searchState.searchQuery)
    )

    return
  }

  val selectedHistoryEntries = remember { kurobaDrawerState.selectedHistoryEntries }
  val isLowRamDevice by kurobaDrawerState.isLowRamDevice

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxWidth()
      .weight(1f)
  ) {
    val drawerGridMode by kurobaDrawerState.drawerGridMode

    if (drawerGridMode) {
      val gridState = rememberLazyGridState()

      AutoScrollListToTop(
        kurobaDrawerState = kurobaDrawerState,
        searchState = searchState,
        totalItemsCountProvider = { gridState.layoutInfo.totalItemsCount },
        scrollTopTop = { gridState.scrollToItem(0) }
      )

      val spanCount = with(LocalDensity.current) {
        (maxWidth.toPx() / MainController.GRID_COLUMN_WIDTH).toInt()
          .coerceIn(MainController.MIN_SPAN_COUNT, MainController.MAX_SPAN_COUNT)
      }

      LazyVerticalGridWithFastScroller(
        state = gridState,
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        columns = GridCells.Fixed(count = spanCount),
        draggableScrollbar = false,
        content = {
          items(
            count = searchResults.size,
            key = { index -> searchResults.getOrNull(index)?.descriptor ?: "<null>" },
            contentType = { "grid_mode_item" }
          ) { index ->
            val navHistoryEntry = searchResults.getOrNull(index)
              ?: return@items

            val isSelectionMode = selectedHistoryEntries.isNotEmpty()
            val isSelected = selectedHistoryEntries.contains(navHistoryEntry.descriptor)

            BuildNavigationHistoryListEntryGridMode(
              mainControllerViewModel = mainControllerViewModel,
              kurobaDrawerState = kurobaDrawerState,
              searchQueryIsNotEmpty = !searchQueryIsEmpty,
              navHistoryEntry = navHistoryEntry,
              isSelectionMode = isSelectionMode,
              isSelected = isSelected,
              isLowRamDevice = isLowRamDevice,
              onHistoryEntryViewClicked = onHistoryEntryViewClicked,
              onHistoryEntryViewLongClicked = onHistoryEntryViewLongClicked,
              onHistoryEntrySelectionChanged = onHistoryEntrySelectionChanged,
              onNavHistoryDeleteClicked = onNavHistoryDeleteClicked
            )
          }
        }
      )
    } else {
      val listState = rememberLazyListState()

      AutoScrollListToTop(
        kurobaDrawerState = kurobaDrawerState,
        searchState = searchState,
        totalItemsCountProvider = { listState.layoutInfo.totalItemsCount },
        scrollTopTop = { listState.scrollToItem(0) }
      )

      LazyColumnWithFastScroller(
        state = listState,
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        draggableScrollbar = false,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = {
          items(
            count = searchResults.size,
            key = { index -> searchResults.getOrNull(index)?.descriptor ?: "<null_${index}>" },
            contentType = { "list_mode_item" }
          ) { index ->
            val navHistoryEntry = searchResults.getOrNull(index)
              ?: return@items

            val isSelectionMode = selectedHistoryEntries.isNotEmpty()
            val isSelected = selectedHistoryEntries.contains(navHistoryEntry.descriptor)

            BuildNavigationHistoryListEntryListMode(
              mainControllerViewModel = mainControllerViewModel,
              kurobaDrawerState = kurobaDrawerState,
              searchQueryIsNotEmpty = !searchQueryIsEmpty,
              navHistoryEntry = navHistoryEntry,
              isSelectionMode = isSelectionMode,
              isSelected = isSelected,
              isLowRamDevice = isLowRamDevice,
              onHistoryEntryViewClicked = onHistoryEntryViewClicked,
              onHistoryEntryViewLongClicked = onHistoryEntryViewLongClicked,
              onHistoryEntrySelectionChanged = onHistoryEntrySelectionChanged,
              onNavHistoryDeleteClicked = onNavHistoryDeleteClicked
            )
          }
        }
      )
    }
  }
}

@Composable
private fun AutoScrollListToTop(
  kurobaDrawerState: KurobaDrawerState,
  searchState: SimpleSearchStateV2<NavigationHistoryEntry>,
  totalItemsCountProvider: () -> Int,
  scrollTopTop: suspend () -> Unit
) {
  val lastRememberedTotalItemsCount = remember { mutableIntStateOf(0) }

  LaunchedEffect(key1 = Unit) {
    searchState.textFieldState.textAsFlow()
      .filter { searchState.usingSearch }
      .distinctUntilChangedBy { textFieldCharSequence -> textFieldCharSequence.length }
      .collectLatest {
        try {
          val success = awaitWhile { lastRememberedTotalItemsCount.intValue == totalItemsCountProvider() }
          if (success) {
            awaitFrame()
            scrollTopTop()
            lastRememberedTotalItemsCount.intValue = totalItemsCountProvider()
          }
        } catch (_: Throwable) {
          // no-op
        }
      }
  }

  LaunchedEffect(key1 = Unit) {
    kurobaDrawerState.resetScrollPositionEvent
      .onEach {
        try {
          awaitFrame()
          scrollTopTop()
        } catch (_: Throwable) {
          // no-op
        }
      }
      .collect()
  }

}

@Composable
private fun BuildNavigationHistoryListHeader(
  textFieldState: TextFieldState,
  onSwitchDayNightThemeIconClick: () -> Unit,
  onShowDrawerOptionsIconClick: () -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val windowInsets = LocalWindowInsets.current
  val toolbarBackgroundComposeColor = chanTheme.toolbarBackgroundComposeColor

  val topInset = windowInsets.top
  val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)

  val kurobaSearchInputColor = if (ThemeEngine.isDarkColor(toolbarBackgroundComposeColor)) {
    Color.White
  } else {
    Color.Black
  }

  Row(
    modifier = Modifier
      .background(toolbarBackgroundComposeColor)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .height(toolbarHeight + topInset)
    ) {
      Spacer(modifier = Modifier.height(topInset))

      Row(
        modifier = Modifier
          .fillMaxHeight()
          .padding(start = 2.dp, end = 2.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.End
      ) {
        Row(
          modifier = Modifier
            .wrapContentHeight()
            .weight(1f),
          verticalAlignment = Alignment.CenterVertically
        ) {
          KurobaSearchInput(
            modifier = Modifier
              .wrapContentHeight()
              .fillMaxWidth()
              .padding(start = 4.dp, end = 4.dp, top = 8.dp),
            color = kurobaSearchInputColor,
            searchQueryState = textFieldState
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        KurobaComposeIcon(
          drawableId = if (chanTheme.isDarkTheme){
            R.drawable.ic_baseline_dark_mode_24
          } else {
            R.drawable.ic_baseline_light_mode_24
          },
          modifier = Modifier
            .align(Alignment.CenterVertically)
            .kurobaClickable(onClick = onSwitchDayNightThemeIconClick),
          iconTint = IconTint.TintWithColor(kurobaSearchInputColor)
        )

        Spacer(modifier = Modifier.width(16.dp))

        KurobaComposeIcon(
          drawableId = R.drawable.ic_more_vert_white_24dp,
          modifier = Modifier
            .align(Alignment.CenterVertically)
            .kurobaClickable(onClick = onShowDrawerOptionsIconClick),
          iconTint = IconTint.TintWithColor(kurobaSearchInputColor)
        )
      }
    }
  }
}

@Composable
private fun LazyItemScope.BuildNavigationHistoryListEntryListMode(
  mainControllerViewModel: MainControllerViewModel,
  kurobaDrawerState: KurobaDrawerState,
  searchQueryIsNotEmpty: Boolean,
  navHistoryEntry: NavigationHistoryEntry,
  isSelectionMode: Boolean,
  isSelected: Boolean,
  isLowRamDevice: Boolean,
  onHistoryEntryViewClicked: (NavigationHistoryEntry) -> Unit,
  onHistoryEntryViewLongClicked: (NavigationHistoryEntry) -> Unit,
  onHistoryEntrySelectionChanged: (Boolean, NavigationHistoryEntry) -> Unit,
  onNavHistoryDeleteClicked: (NavigationHistoryEntry) -> Unit
) {
  val chanDescriptor = navHistoryEntry.descriptor
  val chanTheme = LocalChanTheme.current

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(MainController.LIST_MODE_ROW_HEIGHT)
      .padding(all = 2.dp)
      .kurobaClickable(
        bounded = true,
        onClick = {
          if (isSelectionMode) {
            onHistoryEntrySelectionChanged(isSelected, navHistoryEntry)
          } else {
            onHistoryEntryViewClicked(navHistoryEntry)
          }
        },
        onLongClick = {
          onHistoryEntryViewLongClicked(navHistoryEntry)
        }
      )
      .animateItem(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box {
      val contentScale = if (navHistoryEntry.descriptor is ChanDescriptor.ICatalogDescriptor) {
        ContentScale.Fit
      } else {
        ContentScale.Crop
      }

      val thumbnailRequest = remember(key1 = chanDescriptor) {
        if (navHistoryEntry.isCompositeIconUrl) {
          ImageLoaderRequest(
            data = ImageLoaderRequestData.DrawableResource(R.drawable.composition_icon)
          )
        } else {
          ImageLoaderRequest(
            data = ImageLoaderRequestData.Url(
              httpUrl = navHistoryEntry.threadThumbnailUrl,
              cacheFileType = CacheFileType.NavHistoryThumbnail
            )
          )
        }
      }

      KurobaComposeImage(
        modifier = Modifier
          .size(MainController.LIST_MODE_ROW_HEIGHT)
          .clip(CircleShape),
        controllerKey = null,
        request = thumbnailRequest,
        contentScale = contentScale
      )

      val showDeleteButtonShortcut by remember { kurobaDrawerState.showDeleteButtonShortcut }

      if (isSelectionMode) {
        KurobaComposeSelectionIndicator(
          size = MainController.NAV_HISTORY_DELETE_BTN_SIZE,
          currentlySelected = isSelected,
          onSelectionChanged = { checked -> onHistoryEntrySelectionChanged(checked, navHistoryEntry) }
        )
      } else if (showDeleteButtonShortcut) {
        val shape = remember { CircleShape }

        Box(
          modifier = Modifier
            .align(Alignment.TopStart)
            .size(MainController.NAV_HISTORY_DELETE_BTN_SIZE)
            .kurobaClickable(onClick = { onNavHistoryDeleteClicked(navHistoryEntry) })
            .background(color = MainController.NAV_HISTORY_DELETE_BTN_BG_COLOR, shape = shape)
        ) {
          Image(
            modifier = Modifier.padding(4.dp),
            painter = painterResource(id = R.drawable.ic_clear_white_24dp),
            contentDescription = null
          )
        }
      }

      Column(
        modifier = Modifier
          .wrapContentHeight()
          .wrapContentWidth()
          .align(Alignment.TopEnd)
      ) {
        val siteIconRequest = remember(key1 = chanDescriptor) {
          if (navHistoryEntry.siteThumbnailUrl != null) {
            val data = ImageLoaderRequestData.Url(
              httpUrl = navHistoryEntry.siteThumbnailUrl,
              cacheFileType = CacheFileType.SiteIcon
            )

            ImageLoaderRequest(data = data)
          } else {
            null
          }
        }

        if (siteIconRequest != null) {
          KurobaComposeImage(
            controllerKey = null,
            request = siteIconRequest,
            modifier = Modifier.size(20.dp),
            contentScale = ContentScale.Crop
          )

          Spacer(modifier = Modifier.weight(1f))
        }

        if (navHistoryEntry.pinned) {
          Image(
            modifier = Modifier.size(20.dp),
            painter = painterResource(id = R.drawable.sticky_icon),
            contentDescription = null
          )
        }
      }
    }

    Spacer(modifier = Modifier.width(8.dp))

    KurobaComposeText(
      modifier = Modifier
        .weight(1f)
        .wrapContentHeight(),
      text = navHistoryEntry.title,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      fontSize = 16.ktu
    )

    if (navHistoryEntry.additionalInfo != null) {
      BuildAdditionalBookmarkInfoText(
        kurobaDrawerState = kurobaDrawerState,
        isLowRamDevice = isLowRamDevice,
        isListMode = true,
        searchQueryIsNotEmpty = searchQueryIsNotEmpty,
        additionalInfo = navHistoryEntry.additionalInfo,
        chanTheme = chanTheme
      )
    }
  }
}

@Composable
private fun LazyGridItemScope.BuildNavigationHistoryListEntryGridMode(
  mainControllerViewModel: MainControllerViewModel,
  kurobaDrawerState: KurobaDrawerState,
  searchQueryIsNotEmpty: Boolean,
  navHistoryEntry: NavigationHistoryEntry,
  isSelectionMode: Boolean,
  isSelected: Boolean,
  isLowRamDevice: Boolean,
  onHistoryEntryViewClicked: (NavigationHistoryEntry) -> Unit,
  onHistoryEntryViewLongClicked: (NavigationHistoryEntry) -> Unit,
  onHistoryEntrySelectionChanged: (Boolean, NavigationHistoryEntry) -> Unit,
  onNavHistoryDeleteClicked: (NavigationHistoryEntry) -> Unit
) {
  val chanDescriptor = navHistoryEntry.descriptor
  val chanTheme = LocalChanTheme.current

  val siteIconRequest = remember(key1 = chanDescriptor) {
    if (navHistoryEntry.siteThumbnailUrl != null) {
      val data = ImageLoaderRequestData.Url(
        httpUrl = navHistoryEntry.siteThumbnailUrl,
        cacheFileType = CacheFileType.SiteIcon
      )

      ImageLoaderRequest(data)
    } else {
      null
    }
  }

  val thumbnailRequest = remember(key1 = chanDescriptor) {
    if (navHistoryEntry.isCompositeIconUrl) {
      ImageLoaderRequest(ImageLoaderRequestData.DrawableResource(R.drawable.composition_icon))
    } else {
      val data = ImageLoaderRequestData.Url(
        httpUrl = navHistoryEntry.threadThumbnailUrl,
        cacheFileType = CacheFileType.NavHistoryThumbnail
      )

      ImageLoaderRequest(data)
    }
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .padding(all = 2.dp)
      .kurobaClickable(
        onClick = {
          if (isSelectionMode) {
            onHistoryEntrySelectionChanged(isSelected, navHistoryEntry)
          } else {
            onHistoryEntryViewClicked(navHistoryEntry)
          }
        },
        onLongClick = {
          onHistoryEntryViewLongClicked(navHistoryEntry)
        }
      )
      .animateItem(),
  ) {
    Box {
      val contentScale = if (navHistoryEntry.descriptor is ChanDescriptor.ICatalogDescriptor) {
        ContentScale.Fit
      } else {
        ContentScale.Crop
      }

      KurobaComposeImage(
        controllerKey = null,
        request = thumbnailRequest,
        contentScale = contentScale,
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
      )

      val showDeleteButtonShortcut by remember { kurobaDrawerState.showDeleteButtonShortcut }

      if (isSelectionMode) {
        KurobaComposeSelectionIndicator(
          size = MainController.NAV_HISTORY_DELETE_BTN_SIZE,
          currentlySelected = isSelected,
          onSelectionChanged = { checked -> onHistoryEntrySelectionChanged(checked, navHistoryEntry) }
        )
      } else if (showDeleteButtonShortcut) {
        val shape = remember { CircleShape }

        Box(
          modifier = Modifier
            .align(Alignment.TopStart)
            .size(MainController.NAV_HISTORY_DELETE_BTN_SIZE)
            .kurobaClickable(onClick = { onNavHistoryDeleteClicked(navHistoryEntry) })
            .background(color = MainController.NAV_HISTORY_DELETE_BTN_BG_COLOR, shape = shape)
        ) {
          Image(
            modifier = Modifier.padding(4.dp),
            painter = painterResource(id = R.drawable.ic_clear_white_24dp),
            contentDescription = null
          )
        }
      }

      Row(
        modifier = Modifier
          .wrapContentHeight()
          .wrapContentWidth()
          .align(Alignment.TopEnd)
      ) {

        if (navHistoryEntry.pinned) {
          Image(
            modifier = Modifier.size(20.dp),
            painter = painterResource(id = R.drawable.sticky_icon),
            contentDescription = null
          )
        }

        if (siteIconRequest != null) {
          KurobaComposeImage(
            controllerKey = null,
            request = siteIconRequest,
            modifier = Modifier.size(20.dp),
            contentScale = ContentScale.Crop
          )
        }
      }
    }

    if (navHistoryEntry.additionalInfo != null) {
      BuildAdditionalBookmarkInfoText(
        kurobaDrawerState = kurobaDrawerState,
        isLowRamDevice = isLowRamDevice,
        isListMode = false,
        searchQueryIsNotEmpty = searchQueryIsNotEmpty,
        additionalInfo = navHistoryEntry.additionalInfo,
        chanTheme = chanTheme,
      )
    }

    KurobaComposeText(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(),
      text = navHistoryEntry.title,
      maxLines = 4,
      fontSize = 12.ktu,
      textAlign = TextAlign.Center
    )
  }
}

@Composable
private fun BuildAdditionalBookmarkInfoText(
  kurobaDrawerState: KurobaDrawerState,
  isLowRamDevice: Boolean,
  isListMode: Boolean,
  searchQueryIsNotEmpty: Boolean,
  additionalInfo: NavHistoryBookmarkAdditionalInfo,
  chanTheme: ChanTheme
) {
  val drawerOpened by kurobaDrawerState.drawerOpenedState

  // Now this is epic. So what this thing does (and ^ this one above), they receive all changes
  // to navigation history elements and if the drawer is currently opened display them (with or
  // without animation depending on settings and other stuff) but if the drawer is currently closed
  // then the changes are accumulated and the next time the drawer is opened the difference is
  // displayed. Basically once you open the drawer you will see the changes applied to bookmarks
  // during the time the drawer was closed.
  val prevAdditionalInfoState = remember { mutableStateOf(additionalInfo.copy()) }
  val prevAdditionalInfo by prevAdditionalInfoState

  val currentAdditionalInfo = if (drawerOpened) {
    additionalInfo
  } else {
    prevAdditionalInfo
  }

  val transition = updateTransition(
    targetState = currentAdditionalInfo,
    label = "Text transition animation"
  )

  val animationDisabled = isLowRamDevice
    || searchQueryIsNotEmpty
    || !drawerOpened
    || prevAdditionalInfo == currentAdditionalInfo

  val textAnimationSpec: FiniteAnimationSpec<Int> = if (animationDisabled) {
    // This will disable animations, basically it will switch to the final animation frame right
    // away
    snap()
  } else {
    tween(durationMillis = MainController.TEXT_ANIMATION_DURATION)
  }

  val newPostsCountAnimated by transition.animateInt(
    transitionSpec = { textAnimationSpec },
    label = "New posts animation"
  ) { info -> info.newPosts }
  val newQuotesCountAnimated by transition.animateInt(
    transitionSpec = { textAnimationSpec },
    label = "New quotes animation"
  ) { info -> info.newQuotes }

  val additionalInfoString = remember(
    additionalInfo,
    chanTheme,
    newPostsCountAnimated,
    newQuotesCountAnimated
  ) {
    return@remember currentAdditionalInfo.toAnnotatedString(
      chanTheme = chanTheme,
      newPostsCount = newPostsCountAnimated,
      newQuotesCount = newQuotesCountAnimated
    )
  }


  val targetColor = if (transition.isRunning) {
    val alpha = .35f

    when {
      currentAdditionalInfo.newQuotes > 0 -> {
        chanTheme.bookmarkCounterHasRepliesColorCompose.copy(alpha = alpha)
      }

      currentAdditionalInfo.newPosts > 0 || currentAdditionalInfo.watching -> {
        chanTheme.bookmarkCounterNormalColorCompose.copy(alpha = alpha)
      }

      else -> {
        chanTheme.bookmarkCounterNotWatchingColorCompose.copy(alpha = alpha)
      }
    }
  } else {
    Color.Unspecified
  }

  val shape = if (transition.isRunning) {
    RoundedCornerShape(4.dp)
  } else {
    RectangleShape
  }

  val bgAnimationSpec: AnimationSpec<Color> = if (animationDisabled) {
    snap()
  } else {
    tween(durationMillis = MainController.TEXT_ANIMATION_DURATION)
  }

  val backgroundColor by animateColorAsState(
    targetValue = targetColor,
    animationSpec = bgAnimationSpec
  )

  if (isListMode) {
    KurobaComposeText(
      modifier = Modifier
        .wrapContentWidth()
        .wrapContentHeight()
        .background(color = backgroundColor, shape = shape)
        .padding(horizontal = 6.dp),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      fontSize = 16.ktu,
      text = additionalInfoString
    )
  } else {
    KurobaComposeText(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .background(color = backgroundColor, shape = shape),
      maxLines = 1,
      textAlign = TextAlign.Center,
      overflow = TextOverflow.Ellipsis,
      fontSize = 14.ktu,
      text = additionalInfoString
    )
  }

  SideEffect {
    if (drawerOpened) {
      prevAdditionalInfoState.value = additionalInfo.copy()
    }
  }
}

private fun processSearchQuery(
  query: CharSequence,
  navHistoryEntryList: List<NavigationHistoryEntry>
): List<NavigationHistoryEntry> {
  if (query.isEmpty()) {
    return navHistoryEntryList
  }

  return navHistoryEntryList.filter { navigationHistoryEntry ->
    navigationHistoryEntry.title.contains(other = query, ignoreCase = true)
  }
}