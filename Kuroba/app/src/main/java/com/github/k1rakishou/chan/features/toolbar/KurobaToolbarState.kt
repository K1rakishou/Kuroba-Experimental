package com.github.k1rakishou.chan.features.toolbar

import androidx.compose.runtime.FloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.unit.Dp
import com.github.k1rakishou.chan.core.concurrency.KurobaCoroutineScope
import com.github.k1rakishou.chan.features.toolbar.state.IKurobaToolbarParams
import com.github.k1rakishou.chan.features.toolbar.state.KurobaToolbarSubState
import com.github.k1rakishou.chan.features.toolbar.state.ToolbarStateKind
import com.github.k1rakishou.chan.features.toolbar.state.catalog.KurobaCatalogSearchToolbarParams
import com.github.k1rakishou.chan.features.toolbar.state.catalog.KurobaCatalogSearchToolbarSubState
import com.github.k1rakishou.chan.features.toolbar.state.catalog.KurobaCatalogToolbarParams
import com.github.k1rakishou.chan.features.toolbar.state.catalog.KurobaCatalogToolbarSubState
import com.github.k1rakishou.chan.features.toolbar.state.container.KurobaContainerToolbarParams
import com.github.k1rakishou.chan.features.toolbar.state.container.KurobaContainerToolbarSubState
import com.github.k1rakishou.chan.features.toolbar.state.default.KurobaDefaultToolbarParams
import com.github.k1rakishou.chan.features.toolbar.state.default.KurobaDefaultToolbarSubState
import com.github.k1rakishou.chan.features.toolbar.state.reply.KurobaReplyToolbarParams
import com.github.k1rakishou.chan.features.toolbar.state.reply.KurobaReplyToolbarSubState
import com.github.k1rakishou.chan.features.toolbar.state.search.KurobaSearchToolbarParams
import com.github.k1rakishou.chan.features.toolbar.state.search.KurobaSearchToolbarSubState
import com.github.k1rakishou.chan.features.toolbar.state.selection.KurobaSelectionToolbarParams
import com.github.k1rakishou.chan.features.toolbar.state.selection.KurobaSelectionToolbarSubState
import com.github.k1rakishou.chan.features.toolbar.state.thread.KurobaThreadSearchToolbarParams
import com.github.k1rakishou.chan.features.toolbar.state.thread.KurobaThreadSearchToolbarSubState
import com.github.k1rakishou.chan.features.toolbar.state.thread.KurobaThreadToolbarParams
import com.github.k1rakishou.chan.features.toolbar.state.thread.KurobaThreadToolbarSubState
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import com.github.k1rakishou.chan.ui.controller.base.transition.TransitionMode
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.common.quantize
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Stable
class KurobaToolbarState(
  private val controllerKey: ControllerKey,
  private val globalUiStateHolder: GlobalUiStateHolder
) {
  private val _keyboardOpenRequesters = MutableStateFlow<PersistentSet<ToolbarStateKind>>(persistentSetOf())
  val keyboardOpenRequesters: StateFlow<ImmutableSet<ToolbarStateKind>>
    get() = _keyboardOpenRequesters.asStateFlow()

  private val _toolbarStateList = mutableStateOf<PersistentList<KurobaToolbarSubState>>(persistentListOf())
  val toolbarStateList: State<ImmutableList<KurobaToolbarSubState>>
    get() = _toolbarStateList

  private val _toolbarList: PersistentList<KurobaToolbarSubState>
    get() = _toolbarStateList.value

  val topToolbar: KurobaToolbarSubState?
    get() = _toolbarStateList.value.lastOrNull()

  private val _transitionToolbarState = mutableStateOf<KurobaToolbarTransition?>(null)
  val transitionToolbarState: State<KurobaToolbarTransition?>
    get() = _transitionToolbarState

  private val _toolbarAlpha = mutableFloatStateOf(1f)
  val toolbarAlpha: FloatState
    get() = _toolbarAlpha

  private val _overriddenTheme = mutableStateOf<ChanTheme?>(null)
  val overriddenTheme: State<ChanTheme?>
    get() = _overriddenTheme

  private val _container by lazy(LazyThreadSafetyMode.NONE) { mutableStateOf(KurobaContainerToolbarSubState()) }
  val container: KurobaContainerToolbarSubState
    get() = _container.value

  private val _catalog by lazy(LazyThreadSafetyMode.NONE) { mutableStateOf(KurobaCatalogToolbarSubState()) }
  val catalog: KurobaCatalogToolbarSubState
    get() = _catalog.value
  private val _catalogSearch by lazy(LazyThreadSafetyMode.NONE) { mutableStateOf(KurobaCatalogSearchToolbarSubState()) }
  val catalogSearch: KurobaCatalogSearchToolbarSubState
    get() = _catalogSearch.value

  private val _thread by lazy(LazyThreadSafetyMode.NONE) { mutableStateOf(KurobaThreadToolbarSubState()) }
  val thread: KurobaThreadToolbarSubState
    get() = _thread.value
  private val _threadSearch by lazy(LazyThreadSafetyMode.NONE) { mutableStateOf(KurobaThreadSearchToolbarSubState()) }
  val threadSearch: KurobaThreadSearchToolbarSubState
    get() = _threadSearch.value

  private val _default by lazy(LazyThreadSafetyMode.NONE) { mutableStateOf(KurobaDefaultToolbarSubState()) }
  val default: KurobaDefaultToolbarSubState
    get() = _default.value
  private val _search by lazy(LazyThreadSafetyMode.NONE) { mutableStateOf(KurobaSearchToolbarSubState()) }
  val search: KurobaSearchToolbarSubState
    get() = _search.value
  private val _selection by lazy(LazyThreadSafetyMode.NONE) { mutableStateOf(KurobaSelectionToolbarSubState()) }
  val selection: KurobaSelectionToolbarSubState
    get() = _selection.value
  private val _reply by lazy(LazyThreadSafetyMode.NONE) { mutableStateOf(KurobaReplyToolbarSubState()) }
  val reply: KurobaReplyToolbarSubState
    get() = _reply.value

  private val _toolbarSubStateChangesFlow = MutableSharedFlow<Unit>(extraBufferCapacity = Channel.UNLIMITED)
  val toolbarSubStateChangesFlow: SharedFlow<Unit>
    get() = _toolbarSubStateChangesFlow.asSharedFlow()

  private val _invokeAfterTransitionFinishedCallbacks = mutableListOf<KurobaToolbarState.() -> Unit>()
  private var _destroyed = false
  private var _coroutineScope = KurobaCoroutineScope()

  val toolbarVisibleState: Flow<Boolean>
    get() = globalUiStateHolder.toolbar.toolbarShown
  val toolbarHeightState: StateFlow<Dp?>
    get() = globalUiStateHolder.toolbar.toolbarHeight
  val toolbarHeight: Dp?
    get() = toolbarHeightState.value

  val toolbarKey: String
    get() = "Toolbar_${controllerKey.key}"

  fun init() {
    Logger.debug(TAG) { "Toolbar '${toolbarKey}' is being initialized (${hashCode()})" }

    _destroyed = false

    _coroutineScope.cancel()
    _coroutineScope = KurobaCoroutineScope()

    _coroutineScope.launch {
      globalUiStateHolder.toolbar.toolbarBadges
        .collectLatest { toolbarBadges ->
          toolbarBadges[ToolbarStateKind.Catalog]
            ?.let { catalogBadgeState -> catalog.updateBadge(catalogBadgeState.number, catalogBadgeState.highImportance) }
          toolbarBadges[ToolbarStateKind.Thread]
            ?.let { threadBadgeState -> thread.updateBadge(threadBadgeState.number, threadBadgeState.highImportance) }
        }
    }
  }

  fun destroy() {
    _invokeAfterTransitionFinishedCallbacks.forEach { callback -> callback.invoke(this) }
    _invokeAfterTransitionFinishedCallbacks.clear()

    Logger.debug(TAG) { "Toolbar '${toolbarKey}' is being destroyed" }

    _toolbarStateList.value = persistentListOf()
    _transitionToolbarState.value = null
    _toolbarAlpha.floatValue = 0f
    _destroyed = true
    _coroutineScope.cancel()

    _toolbarSubStateChangesFlow.tryEmit(Unit)
  }

  fun onBack(): Boolean {
    return pop(withAnimation = true)
  }

  fun onToolbarHeightChanged(totalToolbarHeight: Dp) {
    globalUiStateHolder.updateToolbarState {
      updateToolbarHeightState(totalToolbarHeight)
    }
  }

  fun overrideChanTheme(chanTheme: ChanTheme) {
    _overriddenTheme.value = chanTheme
  }

  fun showToolbar() {
    _toolbarAlpha.floatValue = 1f

    globalUiStateHolder.updateToolbarState {
      updateToolbarVisibilityState(visible = true)
    }
    globalUiStateHolder.updateScrollState {
      resetScrollState()
    }
  }

  fun showKeyboard(toolbarStateKind: ToolbarStateKind) {
    if (_keyboardOpenRequesters.value.contains(toolbarStateKind)) {
      return
    }

    _keyboardOpenRequesters.value = _keyboardOpenRequesters.value.add(toolbarStateKind)
  }

  fun hideKeyboard(toolbarStateKind: ToolbarStateKind) {
    if (!_keyboardOpenRequesters.value.contains(toolbarStateKind)) {
      return
    }

    _keyboardOpenRequesters.value = _keyboardOpenRequesters.value.remove(toolbarStateKind)
  }

  fun updateToolbarAlpha(toolbarAlpha: Float) {
    _toolbarAlpha.floatValue = toolbarAlpha
  }

  fun onTransitionProgressStart(
    other: KurobaToolbarState,
    transitionMode: TransitionMode
  ) {
    when (val transition = _transitionToolbarState.value) {
      is KurobaToolbarTransition.Instant -> {
        // End current transition animation
        onKurobaToolbarTransitionInstantFinished(transition)
      }
      is KurobaToolbarTransition.Progress -> {
        error("Attempt to perform more than one transition at the same time!\n" +
          "current: ${transitionToolbarState.value}\n" +
          "new: ${other} with transitionMode: ${transitionMode}")
      }
      null -> {
        // no-op
      }
    }

    val topToolbar = checkNotNull(other.topToolbar) {
      "Attempt to perform a transition with a non-initialized toolbar! toolbar: ${other}"
    }

    _transitionToolbarState.value = KurobaToolbarTransition.Progress(
      transitionToolbarState = topToolbar,
      transitionMode = transitionMode,
      progress = -1f
    )
  }

  fun onTransitionProgress(progress: Float) {
    val transitionState = _transitionToolbarState.value
      ?: return

    check(transitionState is KurobaToolbarTransition.Progress) {
      "Expected transitionState to be Progress but got ${transitionState}"
    }

    val quantizedProgress = progress.quantize(precision = 0.033f)
    if (quantizedProgress == transitionState.progress) {
      return
    }

    _transitionToolbarState.value = transitionState.copy(progress = quantizedProgress)
  }

  fun onTransitionProgressFinished() {
    val transitionState = _transitionToolbarState.value

    if (transitionState != null) {
      check(transitionState is KurobaToolbarTransition.Progress) {
        "Expected transitionState to be Progress but got ${transitionState}"
      }
    }

    _transitionToolbarState.value = null
    invokeAllAfterTransitionFinishedCallbacks()
  }

  fun invokeAfterTransitionFinished(func: KurobaToolbarState.() -> Unit) {
    val hasNoActiveTransition = _transitionToolbarState.value == null
    if (_destroyed || hasNoActiveTransition) {
      func(this)
      return
    }

    _invokeAfterTransitionFinishedCallbacks += func
  }

  private fun invokeAllAfterTransitionFinishedCallbacks() {
    val callbacks = _invokeAfterTransitionFinishedCallbacks.toList()
    _invokeAfterTransitionFinishedCallbacks.clear()

    for (callback in callbacks) {
      callback.invoke(this)
    }
  }

  fun enterContainerMode() {
    enterToolbarMode(
      params = KurobaContainerToolbarParams(),
      state = _container.value,
      withAnimation = false
    )
  }

  fun enterCatalogMode(
    leftItem: ToolbarMenuItem?,
    onMainContentClick: () -> Unit,
    menuBuilder: (ToolbarMenuBuilder.() -> Unit)? = null,
    iconClickInterceptor: ((ToolbarMenuItem) -> Boolean)? = null
  ) {
    val toolbarMenuBuilder = ToolbarMenuBuilder()
    menuBuilder?.invoke(toolbarMenuBuilder)

    enterToolbarMode(
      params = KurobaCatalogToolbarParams(
        leftItem = leftItem,
        toolbarMenu = toolbarMenuBuilder.build(),
        onMainContentClick = onMainContentClick,
        iconClickInterceptor = iconClickInterceptor
      ),
      state = _catalog.value,
      withAnimation = false
    )
  }

  fun enterCatalogSearchMode(
    withAnimation: Boolean = true,
    initialSearchQuery: String? = null,
    menuBuilder: (ToolbarMenuBuilder.() -> Unit)? = null,
  ) {
    val toolbarMenuBuilder = ToolbarMenuBuilder()
    menuBuilder?.invoke(toolbarMenuBuilder)

    enterToolbarMode(
      params = KurobaCatalogSearchToolbarParams(
        initialSearchQuery = initialSearchQuery,
        toolbarMenu = toolbarMenuBuilder.build(),
      ),
      state = _catalogSearch.value,
      withAnimation = withAnimation
    )
  }

  fun isInCatalogSearchMode(): Boolean {
    return topToolbar?.kind == ToolbarStateKind.CatalogSearch
  }

  fun enterThreadMode(
    leftItem: ToolbarMenuItem?,
    scrollableTitle: Boolean = false,
    menuBuilder: (ToolbarMenuBuilder.() -> Unit)? = null,
    iconClickInterceptor: ((ToolbarMenuItem) -> Boolean)? = null
  ) {
    val toolbarMenuBuilder = ToolbarMenuBuilder()
    menuBuilder?.invoke(toolbarMenuBuilder)

    enterToolbarMode(
      params = KurobaThreadToolbarParams(
        leftItem = leftItem,
        scrollableTitle = scrollableTitle,
        toolbarMenu = toolbarMenuBuilder.build(),
        iconClickInterceptor = iconClickInterceptor
      ),
      state = _thread.value,
      withAnimation = false
    )
  }

  fun enterThreadSearchMode(
    withAnimation: Boolean = true,
    initialSearchQuery: String? = null,
    menuBuilder: (ToolbarMenuBuilder.() -> Unit)? = null,
  ) {
    val toolbarMenuBuilder = ToolbarMenuBuilder()
    menuBuilder?.invoke(toolbarMenuBuilder)

    enterToolbarMode(
      params = KurobaThreadSearchToolbarParams(
        initialSearchQuery = initialSearchQuery,
        toolbarMenu = toolbarMenuBuilder.build(),
      ),
      state = _threadSearch.value,
      withAnimation = withAnimation
    )
  }

  fun isInThreadSearchMode(): Boolean {
    return topToolbar?.kind == ToolbarStateKind.ThreadSearch
  }

  fun enterDefaultMode(
    leftItem: ToolbarMenuItem?,
    scrollableTitle: Boolean = false,
    withAnimation: Boolean = true,
    middleContent: ToolbarMiddleContent? = null,
    menuBuilder: (ToolbarMenuBuilder.() -> Unit)? = null,
    iconClickInterceptor: ((ToolbarMenuItem) -> Boolean)? = null
  ) {
    val toolbarMenuBuilder = ToolbarMenuBuilder()
    menuBuilder?.invoke(toolbarMenuBuilder)

    enterToolbarMode(
      params = KurobaDefaultToolbarParams(
        leftItem = leftItem,
        scrollableTitle = scrollableTitle,
        middleContent = middleContent,
        toolbarMenu = toolbarMenuBuilder.build(),
        iconClickInterceptor = iconClickInterceptor
      ),
      state = _default.value,
      withAnimation = withAnimation
    )
  }

  fun enterSearchMode(
    withAnimation: Boolean = true,
    initialSearchQuery: String? = null,
    menuBuilder: (ToolbarMenuBuilder.() -> Unit)? = null,
  ) {
    val toolbarMenuBuilder = ToolbarMenuBuilder()
    menuBuilder?.invoke(toolbarMenuBuilder)

    enterToolbarMode(
      params = KurobaSearchToolbarParams(
        initialSearchQuery = initialSearchQuery,
        toolbarMenu = toolbarMenuBuilder.build(),
      ),
      state = _search.value,
      withAnimation = withAnimation
    )
  }

  fun isInSearchMode(): Boolean {
    return topToolbar?.kind == ToolbarStateKind.Search
  }

  fun enterSelectionMode(
    leftItem: ToolbarMenuItem?,
    withAnimation: Boolean = true,
    selectedItemsCount: Int,
    totalItemsCount: Int,
    menuBuilder: (ToolbarMenuBuilder.() -> Unit)? = null,
  ) {
    val toolbarMenuBuilder = ToolbarMenuBuilder()
    menuBuilder?.invoke(toolbarMenuBuilder)

    enterToolbarMode(
      params = KurobaSelectionToolbarParams(
        leftItem = leftItem,
        selectedItemsCount = selectedItemsCount,
        totalItemsCount = totalItemsCount,
        toolbarMenu = toolbarMenuBuilder.build(),
      ),
      state = _selection.value,
      withAnimation = withAnimation
    )
  }

  fun isInSelectionMode(): Boolean {
    return topToolbar?.kind == ToolbarStateKind.Selection
  }

  fun enterReplyMode(
    chanDescriptor: ChanDescriptor,
    withAnimation: Boolean = true,
    leftItem: ToolbarMenuItem? = null,
    menuBuilder: (ToolbarMenuBuilder.() -> Unit)? = null,
  ) {
    val toolbarMenuBuilder = ToolbarMenuBuilder()
    menuBuilder?.invoke(toolbarMenuBuilder)

    enterToolbarMode(
      params = KurobaReplyToolbarParams(
        leftItem = leftItem,
        chanDescriptor = chanDescriptor,
        toolbarMenu = toolbarMenuBuilder.build(),
      ),
      state = _reply.value,
      withAnimation = withAnimation
    )
  }

  fun isInReplyMode(): Boolean {
    return topToolbar?.kind == ToolbarStateKind.Reply
  }

  fun popUntil(withAnimation: Boolean, predicate: (KurobaToolbarSubState) -> Boolean) {
    while (true) {
      val topToolbar = _toolbarList.lastOrNull()
      if (topToolbar == null) {
        return
      }

      if (!predicate(topToolbar)) {
        return
      }

      if (!pop(withAnimation)) {
        return
      }
    }
  }

  fun pop(withAnimation: Boolean = true): Boolean {
    if (_toolbarList.size <= 1) {
      return false
    }

    if (!_destroyed && _transitionToolbarState.value != null) {
      return false
    }

    val topToolbar = _toolbarList.lastOrNull()
    if (topToolbar == null) {
      return false
    }

    Logger.debug(TAG) {
      "Toolbar '${toolbarKey}' exiting state ${topToolbar.kind}, withAnimation: ${withAnimation}"
    }

    if (!withAnimation) {
      val prevTop = _toolbarList.lastOrNull()
      _toolbarStateList.value = _toolbarList.removeAt(_toolbarList.lastIndex)
      val newTop = _toolbarList.lastOrNull()

      if (prevTop != null) {
        hideKeyboard(prevTop.kind)
      }

      prevTop?.onHidden()
      newTop?.onShown()
      prevTop?.onDestroyed()

      if (newTop != null) {
        globalUiStateHolder.updateToolbarState { onToolbarTopStateChanged(controllerKey, newTop.kind) }
      }

      _toolbarSubStateChangesFlow.tryEmit(Unit)
    } else {
      val belowTop = _toolbarList.getOrNull(_toolbarList.lastIndex - 1)
      if (belowTop == null) {
        return false
      }

      _transitionToolbarState.value = KurobaToolbarTransition.Instant(
        transitionMode = TransitionMode.Out,
        transitionToolbarState = belowTop
      )
    }

    return true
  }

  fun findItem(id: Int): ToolbarMenuItem? {
    for (toolbarState in _toolbarList) {
      val toolbarMenuItem = toolbarState.findItem(id)
      if (toolbarMenuItem?.id == id) {
        return toolbarMenuItem
      }
    }

    return null
  }

  fun findOverflowItem(id: Int): ToolbarMenuOverflowItem? {
    for (toolbarState in _toolbarList) {
      val toolbarOverflowMenuItem = toolbarState.findOverflowItem(id)
      if (toolbarOverflowMenuItem?.id == id) {
        return toolbarOverflowMenuItem
      }
    }

    return null
  }

  fun findCheckableOverflowItem(id: Int): ToolbarMenuCheckableOverflowItem? {
    for (toolbarState in _toolbarList) {
      val toolbarCheckableOverflowMenuItem = toolbarState.findCheckableOverflowItem(id)
      if (toolbarCheckableOverflowMenuItem?.id == id) {
        return toolbarCheckableOverflowMenuItem
      }
    }

    return null
  }

  fun checkOrUncheckItem(subItem: ToolbarMenuCheckableOverflowItem, check: Boolean) {
    for (toolbarState in _toolbarList) {
      toolbarState.checkOrUncheckItem(subItem, check)
    }
  }

  fun onKurobaToolbarTransitionInstantFinished(instant: KurobaToolbarTransition.Instant) {
    if (_transitionToolbarState.value == null) {
      // Already canceled by someone else
      return
    }

    if (_transitionToolbarState.value is KurobaToolbarTransition.Progress) {
      return
    }

    when (instant.transitionMode) {
      TransitionMode.In -> {
        val prevTop = _toolbarList.lastOrNull()
        _toolbarStateList.value = _toolbarList.add(instant.transitionToolbarState)
        val newTop = _toolbarList.lastOrNull()

        newTop?.onCreated()
        prevTop?.onHidden()
        newTop?.onShown()

        if (newTop != null) {
          if (newTop.kind.isSearchToolbar()) {
            showKeyboard(newTop.kind)
          }

          globalUiStateHolder.updateToolbarState { onToolbarTopStateChanged(controllerKey, newTop.kind) }
        }
      }
      TransitionMode.Out -> {
        val prevTop = _toolbarList.lastOrNull()
        if (prevTop != null) {
          _toolbarStateList.value = _toolbarList.removeAt(_toolbarList.lastIndex)
          hideKeyboard(prevTop.kind)
        }

        val newTop = _toolbarList.lastOrNull()

        prevTop?.onHidden()
        newTop?.onShown()
        prevTop?.onDestroyed()

        if (newTop != null) {
          globalUiStateHolder.updateToolbarState { onToolbarTopStateChanged(controllerKey, newTop.kind) }
        }
      }
    }

    _transitionToolbarState.value = null
    invokeAllAfterTransitionFinishedCallbacks()
    _toolbarSubStateChangesFlow.tryEmit(Unit)
  }

  private fun enterToolbarMode(
    params: IKurobaToolbarParams,
    state: KurobaToolbarSubState,
    withAnimation: Boolean
  ) {
    Logger.debug(TAG) { "Toolbar '${toolbarKey}' entering state ${state.kind}, withAnimation: ${withAnimation}" }

    val indexOfState = _toolbarList.indexOfFirst { prevLayer -> prevLayer.kind == params.kind }
    if (indexOfState >= 0) {
      val prevToolbarLayer = _toolbarList[indexOfState]
      Snapshot.withMutableSnapshot { prevToolbarLayer.update(params) }
    } else {
      Snapshot.withMutableSnapshot { state.update(params) }

      if (topToolbar == null || !withAnimation) {
        val prevTop = _toolbarList.lastOrNull()
        _toolbarStateList.value = _toolbarList.add(state)
        val newTop = _toolbarList.lastOrNull()

        newTop?.onCreated()
        prevTop?.onHidden()
        newTop?.onShown()

        if (newTop != null) {
          globalUiStateHolder.updateToolbarState { onToolbarTopStateChanged(controllerKey, newTop.kind) }

          if (newTop.kind.isSearchToolbar()) {
            showKeyboard(newTop.kind)
          }
        }

        _toolbarSubStateChangesFlow.tryEmit(Unit)
      } else {
        _transitionToolbarState.value = KurobaToolbarTransition.Instant(
          transitionMode = TransitionMode.In,
          transitionToolbarState = state
        )
      }
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as KurobaToolbarState

    return controllerKey == other.controllerKey
  }

  override fun hashCode(): Int {
    return controllerKey.hashCode()
  }

  override fun toString(): String {
    return "KurobaToolbarState(controllerKey: $controllerKey)"
  }

  companion object {
    private const val TAG = "KurobaToolbarState"
  }

}