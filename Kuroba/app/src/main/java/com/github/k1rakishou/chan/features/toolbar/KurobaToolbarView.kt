package com.github.k1rakishou.chan.features.toolbar

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.concurrency.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.manager.CurrentOpenedDescriptorStateManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.ui.compose.providers.ComposeEntrypoint
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import com.github.k1rakishou.chan.ui.controller.navigation.ContainerToolbarStateUpdatedListener
import com.github.k1rakishou.chan.ui.controller.navigation.DoubleNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.chan.ui.view.floating_menu.CheckableFloatingListMenuItem
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.ui.viewstate.ToolbarVisibilityState
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.combineMany
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import javax.inject.Inject

class KurobaToolbarView @JvmOverloads constructor(
  context: Context,
  attrSet: AttributeSet? = null,
  defAttrStyle: Int = 0
) : FrameLayout(context, attrSet, defAttrStyle), ContainerToolbarStateUpdatedListener {

  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var globalUiStateHolder: GlobalUiStateHolder
  @Inject
  lateinit var currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager

  private val coroutineScope = KurobaCoroutineScope()

  private val _kurobaToolbarState = mutableStateOf<KurobaToolbarState?>(null)
  private var _attachedController: ToolbarNavigationController? = null

  private val currentToolbarState: KurobaToolbarState?
    get() = _kurobaToolbarState.value

  init {
    if (!isInEditMode) {
      AppModuleAndroidUtils.extractActivityComponent(context)
        .inject(this)
    }

    addView(
      ComposeView(context).also { composeView ->
        composeView.setContent {
          ComposeEntrypoint {
            val kurobaToolbarState by _kurobaToolbarState
            KurobaToolbar(
              kurobaToolbarState = kurobaToolbarState,
              showFloatingMenu = { menuItems -> showFloatingMenu(menuItems, context) }
            )
          }
        }
      }
    )
  }

  override fun onStateUpdated(kurobaToolbarState: KurobaToolbarState) {
    val prevToolbar = currentToolbarState
    _kurobaToolbarState.value = kurobaToolbarState
    prevToolbar?.updateToolbarAlpha(1f)
  }

  fun init(controller: ToolbarNavigationController) {
    coroutineScope.cancelChildren()

    _kurobaToolbarState.value = controller.containerToolbarState
    _attachedController = controller

    controller.addOrReplaceContainerToolbarStateUpdated(this)

    coroutineScope.launch {
      combineMany(
        ChanSettings.layoutMode.listenForChanges().asFlow(),
        ChanSettings.neverHideToolbar.listenForChanges().asFlow(),
        globalUiStateHolder.replyLayout.replyLayoutVisibilityEventsFlow,
        snapshotFlow { globalUiStateHolder.fastScroller.isDraggingFastScrollerState.value },
        snapshotFlow { globalUiStateHolder.scroll.scrollTransitionProgress.floatValue },
        globalUiStateHolder.toolbar.currentToolbarStates,
        currentOpenedDescriptorStateManager.currentFocusedControllers,
        controller.topControllerState.flatMapLatest { controller -> mapTopControllerIntoKeys(controller) }
      ) { _, _, replyLayoutVisibilityStates, isDraggingFastScroller, scrollProgress, currentToolbarStates, currentFocusedControllers, topControllerKeys ->
        return@combineMany ToolbarVisibilityState(
          replyLayoutVisibilityStates = replyLayoutVisibilityStates,
          isDraggingFastScroller = isDraggingFastScroller,
          scrollProgress = scrollProgress,
          currentToolbarStates = currentToolbarStates,
          currentFocusedControllers = currentFocusedControllers,
          topControllerKeys = topControllerKeys
        )
      }
        .onEach { toolbarVisibilityState ->
          if (toolbarVisibilityState.isToolbarForceVisible()) {
            currentToolbarState?.updateToolbarAlpha(1f)
            globalUiStateHolder.updateScrollState { resetScrollState() }
            return@onEach
          }

          if (toolbarVisibilityState.isToolbarForceHidden()) {
            currentToolbarState?.updateToolbarAlpha(0f)
            globalUiStateHolder.updateScrollState { resetScrollState() }
            return@onEach
          }

          currentToolbarState?.updateToolbarAlpha(toolbarVisibilityState.toolbarAlpha)
        }
        .collect()
    }
  }

  fun init(kurobaToolbarState: KurobaToolbarState, controller: ToolbarNavigationController) {
    _kurobaToolbarState.value = kurobaToolbarState
    _attachedController = controller
  }

  fun destroy() {
    _attachedController?.let { controller ->
      controller.removeContainerToolbarStateUpdated(this@KurobaToolbarView)
    }

    coroutineScope.cancel()
    _attachedController = null
  }

  private fun showFloatingMenu(
    menuItems: List<AbstractToolbarMenuOverflowItem>,
    context: Context
  ) {
    val controller = _attachedController ?: return

    if (menuItems.isEmpty()) {
      return
    }

    controller.presentController(
      controller = FloatingListMenuController(
        context = context,
        constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
        items = mapMenuItems(menuItems),
        itemClickListener = { clickedMenuItem ->
          val clickedAbstractMenuItem = findClickedItemRecursively(menuItems, clickedMenuItem.key)
          if (clickedAbstractMenuItem == null) {
            return@FloatingListMenuController
          }

          when (clickedAbstractMenuItem) {
            is ToolbarMenuCheckableOverflowItem -> {
              clickedAbstractMenuItem.onClick(clickedAbstractMenuItem)
            }
            is ToolbarMenuOverflowItem -> {
              clickedAbstractMenuItem.onClick?.invoke(clickedAbstractMenuItem)
            }
            else -> {
              error("Unknown clickedAbstractMenuItem: ${clickedAbstractMenuItem::class.java.simpleName}")
            }
          }
        }
      )
    )
  }

  private fun mapMenuItems(menuItems: List<AbstractToolbarMenuOverflowItem>): List<FloatingListMenuItem> {
    return menuItems.map { abstractToolbarMenuOverflowItem ->
      when (abstractToolbarMenuOverflowItem) {
        is ToolbarMenuCheckableOverflowItem -> {
          CheckableFloatingListMenuItem(
            key = abstractToolbarMenuOverflowItem.id,
            name = abstractToolbarMenuOverflowItem.menuTextState.value,
            value = abstractToolbarMenuOverflowItem.value,
            groupId = abstractToolbarMenuOverflowItem.groupId,
            visible = abstractToolbarMenuOverflowItem.visibleState.value,
            enabled = abstractToolbarMenuOverflowItem.enabledState.value,
            more = mapMenuItems(abstractToolbarMenuOverflowItem.subItems),
            checked = abstractToolbarMenuOverflowItem.checkedState.value
          )
        }
        is ToolbarMenuOverflowItem -> {
          FloatingListMenuItem(
            key = abstractToolbarMenuOverflowItem.id,
            name = abstractToolbarMenuOverflowItem.menuTextState.value,
            value = abstractToolbarMenuOverflowItem.value,
            visible = abstractToolbarMenuOverflowItem.visibleState.value,
            enabled = abstractToolbarMenuOverflowItem.enabledState.value,
            more = mapMenuItems(abstractToolbarMenuOverflowItem.subItems)
          )
        }
        else -> {
          error("Unknown abstractToolbarMenuOverflowItem: ${abstractToolbarMenuOverflowItem::class.java.simpleName}")
        }
      }
    }
  }

  private fun findClickedItemRecursively(
    menuItems: List<AbstractToolbarMenuOverflowItem>,
    needle: Any
  ): AbstractToolbarMenuOverflowItem? {
    for (item in menuItems) {
      if (item.id == needle) {
        return item
      }

      if (item.subItems.isNotEmpty()) {
        val foundItem = findClickedItemRecursively(item.subItems, needle)
        if (foundItem != null) {
          return foundItem
        }
      }
    }

    return null
  }

  // Holy shit, what a hack!
  // Basically, what it does: each time ToolbarNavigationController's top controller changes
  // we check what kind of a controller it is. If it's a regular controller then we return just that controller.
  // If it's a DoubleNavigationController then we need to listen to top controllers from both left/right parts so we
  // return a flow of child controllers.
  private fun mapTopControllerIntoKeys(controller: Controller?): Flow<List<ControllerKey>> {
    return flow {
      if (controller == null) {
        emit(emptyList())
        return@flow
      }

      if (controller is DoubleNavigationController) {
        val flowOfChildControllers = combine(
          controller.leftControllerFlow,
          controller.rightControllerFlow
        ) { left, right ->
          return@combine buildList {
            if (left != null) {
              add(left.controllerKey)
            }

            if (right != null) {
              add(right.controllerKey)
            }
          }
        }

        emitAll(flowOfChildControllers)
        return@flow
      }

      emit(listOf(controller.controllerKey))
    }
  }

}