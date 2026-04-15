package com.github.k1rakishou.chan.features.settings

import android.content.Context
import android.os.Parcelable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.controller.ControllerComponent
import com.github.k1rakishou.chan.features.settings.setting.SettingUiElement
import com.github.k1rakishou.chan.features.toolbar.BackArrowMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMiddleContent
import com.github.k1rakishou.chan.features.toolbar.ToolbarText
import com.github.k1rakishou.chan.features.toolbar.state.ToolbarStateKind
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeDivider
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeMessage
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.lazylist.LazyColumnWithFastScroller
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.providers.LocalContentPaddings
import com.github.k1rakishou.chan.ui.compose.providers.LocalWindowSizeClass
import com.github.k1rakishou.chan.ui.compose.settings.SettingUiElementWidget
import com.github.k1rakishou.chan.ui.compose.settings.SettingUiElementWidgetState
import com.github.k1rakishou.chan.ui.compose.settings.rememberSettingUiElementWidgetState
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarScope
import com.github.k1rakishou.chan.ui.compose.window.KurobaWindowWidthSizeClass
import com.github.k1rakishou.chan.ui.controller.base.BaseComposeController
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import com.github.k1rakishou.chan.ui.controller.base.DeprecatedNavigationFlags
import com.github.k1rakishou.chan.ui.controller.base.onResult
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.ui.helper.awaitUntil
import com.github.k1rakishou.chan.ui.helper.readyForScrollEvents
import com.github.k1rakishou.chan.utils.ViewModelScope
import com.github.k1rakishou.common.unreachable
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.resolveTextColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.milliseconds

class AppSettingsController(
  context: Context,
  private val params: AppSettingsController.Params
) : BaseComposeController<
  AppSettingsControllerViewModel,
  AppSettingsController.Params,
  AppSettingsController.ScreenResult
>(
  context = context,
  viewModelClass = AppSettingsControllerViewModel::class.java,
  viewModelParams = params
) {
  override val viewModelScope: ViewModelScope
    get() = ViewModelScope.ControllerScope(this)

  override fun injectControllerDependencies(component: ControllerComponent) {
    component.inject(this)
  }

  override val controllerKey: ControllerKey
    get() = ControllerKey("${super.controllerKey.key}_${params.screenKey.name}")
  override val layoutAnchor: SnackbarScope.LayoutAnchor
    get() = SnackbarScope.LayoutAnchor.Left

  override fun onDestroy() {
    if (params.isLastSettingsScreen()) {
      viewModel.restartTheAppIfNeeded()
    }

    super.onDestroy()
  }

  override fun setupNavigation() {
    updateNavigationFlags(
      newNavigationFlags = DeprecatedNavigationFlags(
        hasBack = false,
        hasDrawer = true
      )
    )

    toolbarState.enterDefaultMode(
      leftItem = BackArrowMenuItem(
        onClick = { requireNavController().popController() }
      ),
      middleContent = ToolbarMiddleContent.Title(
        // null by default, actually set once setting screens are built
        title = null
      ),
      menuBuilder = {
        if (params.scrollToSettingKeyRaw == null) {
          withMenuItem(
            id = ACTION_SEARCH,
            drawableId = R.drawable.ic_search_white_24dp,
            onClick = { toolbarState.enterSearchMode() }
          )
        }
      }
    )
  }

  override fun onPrepare() {
    super.onPrepare()

    controllerScope.launch {
      viewModel.openScreenRequests
        .onEach { (screenKey, rawSettingKey) ->
          if (isNotTopSettingsController()) {
            return@onEach
          }

          openNewSettingsController(screenKey, rawSettingKey)
        }
        .collect()
    }

    controllerScope.launch {
      viewModel.pushControllerRequests
        .onEach { controller ->
          if (isNotTopSettingsController()) {
            return@onEach
          }

          requireNavController().pushController(controller)
        }
        .collect()
    }

    controllerScope.launch {
      viewModel.presentControllerRequests
        .onEach { controller ->
          if (isNotTopSettingsController()) {
            return@onEach
          }

          presentController(controller)
        }
        .collect()
    }

    controllerScope.launch {
      viewModel.showToastRequests
        .onEach { message ->
          if (isNotTopSettingsController()) {
            return@onEach
          }

          showToast(message)
        }
        .collect()
    }
  }

  @Composable
  override fun ScreenContent() {
    val context = LocalContext.current
    val density = LocalDensity.current
    val windowSizeClass = LocalWindowSizeClass.current
    val contentPaddings = LocalContentPaddings.current
    val layoutDirection = LocalLayoutDirection.current
    val chanTheme = LocalChanTheme.current

    val lazyListState = rememberLazyListState()
    val settingUiElementWidgetState = rememberSettingUiElementWidgetState(appSettingsGraph = viewModel.appSettingsGraph)

    var settingsScreenMut by remember { mutableStateOf<SettingsScreen?>(null) }
    val settingsScreen = settingsScreenMut
    val updatedSettingsScreen = rememberUpdatedState(settingsScreen)

    var settingsMatchedByQueryMut by remember { mutableStateOf<List<SettingUiElement>?>(null) }
    val settingsMatchedByQuery = settingsMatchedByQueryMut

    var currentSearchQuery by remember { mutableStateOf<String?>(null) }

    val needToRestartApp by viewModel.needToRestartApp

    LaunchedEffect(key1 = settingsScreen, key2 = currentSearchQuery) {
      val scrollToSettingKeyRaw = params.scrollToSettingKeyRaw
      if (scrollToSettingKeyRaw == null) {
        return@LaunchedEffect
      }

      if (settingsScreen == null) {
        return@LaunchedEffect
      }

      if (currentSearchQuery != null) {
        return@LaunchedEffect
      }

      doScrollToSetting(
        lazyListState = lazyListState,
        settingUiElementWidgetState = settingUiElementWidgetState,
        settingsScreen = settingsScreen,
        scrollToSettingKeyRaw = scrollToSettingKeyRaw
      )
    }

    LaunchedEffect(key1 = Unit) {
      viewModel.settingScrollRequests
        .collectLatest { scrollToSettingKeyRaw ->
          val settingsScreen = updatedSettingsScreen.value
          if (settingsScreen == null || toolbarState.isInSearchMode()) {
            return@collectLatest
          }

          doScrollToSetting(
            lazyListState = lazyListState,
            settingUiElementWidgetState = settingUiElementWidgetState,
            settingsScreen = settingsScreen,
            scrollToSettingKeyRaw = scrollToSettingKeyRaw
          )
        }
    }

    LaunchedEffect(key1 = Unit) {
      viewModel.buildSettings(context)

      toolbarState.search.listenForSearchState()
        .debounce(100.milliseconds)
        .onEach { (searchMode, searchQuery) ->
          try {
            if (!searchMode || searchQuery == null) {
              val screen = viewModel.getScreen(viewModel.settingsScreenKey)
              toolbarState.default.updateTitle(newTitle = ToolbarText.from(screen.title))

              currentSearchQuery = null
              settingsMatchedByQueryMut = null
              settingsScreenMut = screen
            } else {
              currentSearchQuery = searchQuery
              settingsMatchedByQueryMut = viewModel.onSearchEntered(searchQuery)

              lazyListState.scrollToItem(0)
            }
          } catch (ignored: CancellationException) {
            // no-op
          }
        }
        .collect()
    }

    val horizontalPadding = when (windowSizeClass.widthSizeClass.asKuroba()) {
      KurobaWindowWidthSizeClass.Compact -> 16.dp
      KurobaWindowWidthSizeClass.Medium -> 24.dp
      KurobaWindowWidthSizeClass.Expanded -> 32.dp
    }
    val restartNotificationElementHeight = 24.dp
    val navigationController = remember { requireNavController() }

    val paddingForNotification = remember(contentPaddings) {
      contentPaddings
        .asPaddingValues(controllerKey)
        .calculateTopPadding()
    }
    val paddingValuesForLazyList = remember(key1 = contentPaddings, key2 = needToRestartApp, key3 = layoutDirection) {
      contentPaddings
        .asPaddingValues(controllerKey)
        .let { paddings ->
          return@let if (needToRestartApp) {
            PaddingValues(
              top = paddings.calculateTopPadding() + restartNotificationElementHeight + 8.dp,
              bottom = paddings.calculateBottomPadding(),
              start = paddings.calculateLeftPadding(layoutDirection),
              end = paddings.calculateRightPadding(layoutDirection)
            )
          } else {
            paddings
          }
        }
    }

    val backgroundColor = if (currentSearchQuery == null) {
      chanTheme.backColorSecondaryCompose
    } else {
      chanTheme.backColorCompose
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
    ) {
      LazyColumnWithFastScroller(
        modifier = Modifier
          .fillMaxSize()
          .background(backgroundColor),
        state = lazyListState,
        draggableScrollbar = false,
        contentPadding = paddingValuesForLazyList
      ) {
        if (currentSearchQuery != null) {
          searchQueryMatchedSettingElement(
            settingUiElementWidgetState = settingUiElementWidgetState,
            searchQuery = currentSearchQuery,
            horizontalPadding = horizontalPadding,
            navigationController = navigationController,
            settingsMatchedByQuery = settingsMatchedByQuery
          )
        } else {
          normalSettingElements(
            density = density,
            chanTheme = chanTheme,
            settingUiElementWidgetState = settingUiElementWidgetState,
            horizontalPadding = horizontalPadding,
            settingsScreen = settingsScreen,
            navigationController = navigationController
          )
        }
      }

      AnimatedVisibility(
        visible = needToRestartApp,
        enter = expandVertically(expandFrom = Alignment.Top),
        exit = shrinkVertically(shrinkTowards = Alignment.Top),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(chanTheme.accentColorCompose)
            .padding(vertical = 4.dp)
            .align(Alignment.TopCenter)
        ) {
          Spacer(modifier = Modifier.height(paddingForNotification))

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(restartNotificationElementHeight),
            contentAlignment = Alignment.Center
          ) {
            KurobaComposeText(
              text = stringResource(R.string.the_app_will_be_restarted),
              color = chanTheme.accentColorCompose.resolveTextColor(),
              fontSize = 14.ktu.fixedSize()
            )
          }
        }
      }
    }
  }

  private fun LazyListScope.searchQueryMatchedSettingElement(
    settingUiElementWidgetState: SettingUiElementWidgetState,
    searchQuery: String?,
    horizontalPadding: Dp,
    navigationController: NavigationController,
    settingsMatchedByQuery: List<SettingUiElement>?
  ) {
    if (settingsMatchedByQuery == null) {
      return
    }

    if (searchQuery != null && settingsMatchedByQuery.isEmpty()) {
      item(key = "nothing_found") {
        KurobaComposeMessage(
          modifier = Modifier.fillParentMaxSize(),
          message = appResources.string(R.string.search_nothing_found_with_query, searchQuery)
        )
      }

      return
    }

    items(
      count = settingsMatchedByQuery.size,
      key = { idx -> settingsMatchedByQuery.getOrNull(idx)?.rawKey() ?: "null" },
      contentType = { "setting" },
      itemContent = { idx ->
        val settingUiElement = settingsMatchedByQuery.getOrNull(idx)
          ?: return@items

        val showDivider = idx > 0 && idx < settingsMatchedByQuery.size
        if (showDivider) {
          KurobaComposeDivider(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = horizontalPadding / 2)
          )
        }

        SettingUiElementWidget(
          state = settingUiElementWidgetState,
          isInSearchMode = true,
          drawCardOutline = false,
          settingUiElement = settingUiElement,
          horizontalPadding = horizontalPadding,
          navigationController = navigationController,
          settingBadges = viewModel.appSettingsGraph.settingBadges,
          onSettingElementClicked = { clickedRawSettingKey ->
            val screenKey = viewModel.findScreenKeyBySettingKey(clickedRawSettingKey)
              ?: return@SettingUiElementWidget

            viewModel.openScreen(screenKey, clickedRawSettingKey)
          },
          onSettingUiElementShown = {
            // no-op
          }
        )
      }
    )
  }

  private fun LazyListScope.normalSettingElements(
    density: Density,
    chanTheme: ChanTheme,
    settingUiElementWidgetState: SettingUiElementWidgetState,
    horizontalPadding: Dp,
    settingsScreen: SettingsScreen?,
    navigationController: NavigationController
  ) {
    if (settingsScreen == null) {
      return
    }

    for (settingsGroup in settingsScreen.groups) {
      val settingsInGroup = settingsGroup.settings
      if (settingsInGroup.isEmpty()) {
        continue
      }

      val groupKey = "${settingsScreen.key.name}_${settingsGroup.key}"

      run {
        item(
          key = "${groupKey}_start",
          contentType = "group_start",
          content = {
            KurobaComposeText(
              modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                  drawGroupHeaderOrFooter(
                    density = density,
                    chanTheme = chanTheme,
                    horizontalPadding = horizontalPadding,
                    isHeader = true
                  )
                }
                .padding(
                  start = horizontalPadding,
                  end = horizontalPadding,
                  top = 16.dp,
                  bottom = 8.dp
                ),
              fontSize = 16.ktu,
              text = settingsGroup.title,
              color = chanTheme.textColorSecondaryCompose
            )
          }
        )
      }

      items(
        count = settingsInGroup.size,
        key = { idx ->
          val settingKey = settingsInGroup.getOrNull(idx)?.rawKey() ?: "null"
          return@items "${groupKey}_${settingKey}"
        },
        contentType = { "setting" },
        itemContent = { idx ->
          val settingUiElement = settingsInGroup.getOrNull(idx)
            ?: return@items

          val showDivider = idx > 0 && idx < settingsInGroup.size
          if (showDivider) {
            KurobaComposeDivider(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding / 2)
            )
          }

          SettingUiElementWidget(
            state = settingUiElementWidgetState,
            drawCardOutline = true,
            isInSearchMode = false,
            settingUiElement = settingUiElement,
            horizontalPadding = horizontalPadding,
            navigationController = navigationController,
            settingBadges = viewModel.appSettingsGraph.settingBadges,
            onSettingElementClicked = { unreachable("Shouldn't be called") },
            onSettingUiElementShown = { kurobaSettingKey -> viewModel.onSettingUiElementShown(kurobaSettingKey) }
          )
        }
      )

      run {
        val groupKey = "${settingsScreen.key.name}_${settingsGroup.key}"

        item(
          key = "${groupKey}_end",
          contentType = "group_end",
          content = {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .drawBehind {
                  drawGroupHeaderOrFooter(
                    density = density,
                    chanTheme = chanTheme,
                    horizontalPadding = horizontalPadding,
                    isHeader = false
                  )
                }
                .padding(
                  horizontal = horizontalPadding
                )
            )
          }
        )
      }
    }
  }

  private fun DrawScope.drawGroupHeaderOrFooter(
    density: Density,
    chanTheme: ChanTheme,
    horizontalPadding: Dp,
    isHeader: Boolean
  ) {
    withTransform(
      transformBlock = {
        with(density) {
          inset(
            left = horizontalPadding.toPx() / 2,
            right = horizontalPadding.toPx() / 2,
            top = if (isHeader) 8.dp.toPx() else 0f,
            bottom = 0f
          )
        }
      },
      drawBlock = {
        val path = Path().apply {
          addRoundRect(
            if (isHeader) {
              RoundRect(
                rect = Rect(Offset.Zero, size),
                topLeft = CornerRadius(4.dp.toPx()),
                topRight = CornerRadius(4.dp.toPx()),
                bottomLeft = CornerRadius(0f),
                bottomRight = CornerRadius(0f),
              )
            } else {
              RoundRect(
                rect = Rect(Offset.Zero, size),
                topLeft = CornerRadius(0f),
                topRight = CornerRadius(0f),
                bottomLeft = CornerRadius(4.dp.toPx()),
                bottomRight = CornerRadius(4.dp.toPx()),
              )
            }
          )
        }

        drawPath(color = chanTheme.backColorCompose, path = path)
      }
    )
  }

  private suspend fun openNewSettingsController(screenKey: SettingsScreenKey, rawSettingKey: String) {
    if (params.allOpenedSettingScreenKeys.contains(screenKey)) {
      // This screen already exists in the navigation graph, we can't open it again because this will at the very least
      // break the toolbar. What we can do instead is close the search and scroll to the setting the user has just
      // clicked.
      if (toolbarState.popIfInState(ToolbarStateKind.Search)) {
        toolbarState.invokeAfterTransitionFinished {
          controllerScope.launch {
            viewModel.requestScrollToSettingWithKey(rawSettingKey)
          }
        }
      }

      return
    }

    val controller = AppSettingsController(
      context = context,
      params = Params.createForNextScreen(
        currentParams = params,
        screenKey = screenKey,
        scrollToSettingKeyRaw = rawSettingKey
      )
    )

    requireNavController().pushController(controller)

    controller.awaitForControllerResult()
      .onResult { screenResult -> viewModel.requestScrollToSettingWithKey(screenResult.rawSettingKey) }
  }

  private suspend fun CoroutineScope.doScrollToSetting(
    lazyListState: LazyListState,
    settingUiElementWidgetState: SettingUiElementWidgetState,
    settingsScreen: SettingsScreen,
    scrollToSettingKeyRaw: String
  ) {
    try {
      awaitUntil { lazyListState.readyForScrollEvents() }
      // Dirty hack... But! There is no way to know when new elements were actually fully processed by the
      // LazyList. Like when you recompose elements of the list, and they are different
      // (elements added or removed) - there is no way to know when that information is internalized by the LazyList.
      // And the problem is that without this delay it's possible that, after doing a search and clicking a setting
      // item, the scroll will happen when the items haven't been updated in the LazyList internals yet.
      // Which means the scroll will always fail because the scroll index will be incorrect.
      // This delay "kinda" fixes this.
      delay(100)

      val scrollItemIndex = settingsScreen.findScrollIndex(scrollToSettingKeyRaw)
      if (scrollItemIndex != null && scrollItemIndex >= 0) {
        val firstVisibleItemIndex = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()?.index
        val lastVisibleItemIndex = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index

        if (firstVisibleItemIndex != null && lastVisibleItemIndex != null) {
          val leftDistance = (scrollItemIndex - firstVisibleItemIndex).absoluteValue
          val rightDistance = (scrollItemIndex - lastVisibleItemIndex).absoluteValue
          val itemDistanceForAnimation = 20

          // If the scroll target index is within the viewport or the distance is less than 10 items then we do scroll
          // animation, otherwise we do snap scroll
          if (
            scrollItemIndex in firstVisibleItemIndex..lastVisibleItemIndex ||
            leftDistance < itemDistanceForAnimation ||
            rightDistance < itemDistanceForAnimation
          ) {
            lazyListState.animateScrollToItem(scrollItemIndex)
          } else {
            lazyListState.scrollToItem(scrollItemIndex)
          }
        }
      }

      settingUiElementWidgetState.blink(scrollToSettingKeyRaw)
    } catch (ignored: CancellationException) {
      // catch all CancellationExceptions so that we don't accidentally cancel the coroutine listening to a Flow
    }
  }

  // Only process events for the top-most (in the navstack) settings controller
  private fun isNotTopSettingsController(): Boolean {
    return parentController?.topController !== this@AppSettingsController
  }

  @ConsistentCopyVisibility
  @Parcelize
  data class Params private constructor(
    val screenKey: SettingsScreenKey,
    val allOpenedSettingScreenKeys: Set<SettingsScreenKey>,
    val scrollToSettingKeyRaw: String?
  ) : Parcelable {

    fun isLastSettingsScreen(): Boolean {
      return allOpenedSettingScreenKeys.size <= 1
    }

    companion object {
      fun createForInitialScreen(screenKey: SettingsScreenKey, scrollToSettingKeyRaw: String? = null): Params {
        return AppSettingsController.Params(
          screenKey = screenKey,
          allOpenedSettingScreenKeys = setOf(screenKey),
          scrollToSettingKeyRaw = scrollToSettingKeyRaw
        )
      }

      fun createForNextScreen(
        currentParams: Params,
        screenKey: SettingsScreenKey,
        scrollToSettingKeyRaw: String
      ): Params {
        return AppSettingsController.Params(
          screenKey = screenKey,
          allOpenedSettingScreenKeys = currentParams.allOpenedSettingScreenKeys + screenKey,
          scrollToSettingKeyRaw = scrollToSettingKeyRaw
        )
      }
    }
  }

  @Parcelize
  data class ScreenResult(
    val rawSettingKey: String
  ) : Parcelable

  companion object {
    private const val ACTION_SEARCH = 0
  }
}