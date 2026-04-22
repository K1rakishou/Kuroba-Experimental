package com.github.k1rakishou.chan.ui.compose.panel

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowColumn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.compose.badge.BottomPanelBadge
import com.github.k1rakishou.chan.ui.compose.badge.MenuItemBadge
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.providers.KurobaWindowInsets
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.providers.LocalWindowInsets
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.chan.utils.appDependencies
import com.github.k1rakishou.core_themes.resolveIconTintColor

@Composable
fun KurobaIconPanel(
  modifier: Modifier = Modifier,
  panelState: KurobaIconPanelState,
  onMenuItemClicked: (Int) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val density = LocalDensity.current
  val windowInsets = LocalWindowInsets.current
  val backgroundColor = chanTheme.primaryColorCompose
  val navigationViewSize = dimensionResource(id = R.dimen.navigation_view_size)

  val orientation = panelState.orientation
  val menuItemStateList = panelState.menuItemStateList

  val visible by panelState.visible
  val animatable = remember {
    val initialOffset = with(density) { calculateTargetOffset(visible, panelState, navigationViewSize, windowInsets) }

    return@remember Animatable<IntOffset, AnimationVector2D>(
      initialValue = initialOffset,
      typeConverter = IntOffset.VectorConverter
    )
  }

  LaunchedEffect(key1 = visible) {
    val targetOffset = with(density) { calculateTargetOffset(visible, panelState, navigationViewSize, windowInsets) }

    animatable.animateTo(targetOffset)
    panelState.onPanelAnimationEnd(visible = visible)
  }

  when (orientation) {
    KurobaIconPanelState.Orientation.Vertical -> {
      Row(
        modifier = modifier
          .then(
            Modifier
              .wrapContentSize()
              .offset { animatable.value }
              .background(backgroundColor)
              .onSizeChanged { intSize ->
                with(density) {
                  panelState.onPanelMeasured(width = intSize.width.toDp(), height = intSize.height.toDp())
                }
              }
          )
      ) {
        Spacer(modifier = Modifier.width(windowInsets.left))

        FlowColumn(
          modifier = Modifier
            .width(navigationViewSize)
            .fillMaxHeight(),
          verticalArrangement = Arrangement.SpaceEvenly
        ) {
          menuItemStateList.forEachIndexed { _, menuItemState ->
            key(menuItemState.menuItem.id) {
              BuildMenuItemColumn(
                backgroundColor = backgroundColor,
                navigationViewSize = navigationViewSize,
                onMenuItemClicked = onMenuItemClicked,
                menuItemState = menuItemState
              )
            }
          }
        }
      }
    }
    KurobaIconPanelState.Orientation.Horizontal -> {
      Column(
        modifier = modifier
          .then(
            Modifier
              .wrapContentSize()
              .offset { animatable.value }
              .background(backgroundColor)
              .onSizeChanged { intSize ->
                with(density) {
                  panelState.onPanelMeasured(width = intSize.width.toDp(), height = intSize.height.toDp())
                }
              }
          )
      ) {
        FlowRow(
          modifier = Modifier
            .fillMaxWidth()
            .height(navigationViewSize),
          horizontalArrangement = Arrangement.SpaceEvenly
        ) {
          menuItemStateList.forEachIndexed { _, menuItemState ->
            key(menuItemState.menuItem.id) {
              BuildMenuItemRow(
                backgroundColor = backgroundColor,
                navigationViewSize = navigationViewSize,
                onMenuItemClicked = onMenuItemClicked,
                menuItemState = menuItemState
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(windowInsets.bottom))
      }
    }
  }
}

private fun Density.calculateTargetOffset(
  visible: Boolean,
  panelState: KurobaIconPanelState,
  navigationViewSize: Dp,
  windowInsets: KurobaWindowInsets
): IntOffset {
  return with(density) {
    if (visible) {
      IntOffset.Zero
    } else {
      when (panelState.orientation) {
        KurobaIconPanelState.Orientation.Vertical -> {
          IntOffset(x = -((navigationViewSize + windowInsets.left).roundToPx()), y = 0)
        }
        KurobaIconPanelState.Orientation.Horizontal -> {
          IntOffset(x = 0, y = (navigationViewSize + windowInsets.bottom).roundToPx())
        }
      }
    }
  }
}

@Composable
private fun BuildMenuItemColumn(
  backgroundColor: Color,
  navigationViewSize: Dp,
  onMenuItemClicked: (Int) -> Unit,
  menuItemState: KurobaIconPanelState.MenuItemState
) {
  val menuItem = menuItemState.menuItem
  val menuItemBadge by menuItemState.menuItemBadge

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(navigationViewSize)
      .kurobaClickable(
        bounded = false,
        onClick = { onMenuItemClicked(menuItem.id) }
      ),
  ) {
    val targetColor = remember(key1 = backgroundColor) {
      backgroundColor.resolveIconTintColor()
    }

    val colorAnimated by animateColorAsState(
      targetValue = targetColor,
      animationSpec = tween(200)
    )

    Image(
      painter = painterResource(id = menuItem.iconId),
      colorFilter = ColorFilter.tint(colorAnimated),
      contentDescription = null
    )

    // text is not supported here

    menuItemBadge?.let { badge -> BottomPanelBadge(badge) }
  }
}

@Composable
private fun BuildMenuItemRow(
  backgroundColor: Color,
  navigationViewSize: Dp,
  onMenuItemClicked: (Int) -> Unit,
  menuItemState: KurobaIconPanelState.MenuItemState
) {
  val menuItem = menuItemState.menuItem
  val menuItemBadge by menuItemState.menuItemBadge

  Box(
    modifier = Modifier
      .fillMaxHeight()
      .width(navigationViewSize)
      .kurobaClickable(
        bounded = false,
        onClick = { onMenuItemClicked(menuItem.id) }
      ),
    contentAlignment = Alignment.Center
  ) {
    val targetColor = remember(key1 = backgroundColor) {
      backgroundColor.resolveIconTintColor()
    }

    val colorAnimated by animateColorAsState(
      targetValue = targetColor,
      animationSpec = tween(200)
    )

    Column(
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Image(
        painter = painterResource(id = menuItem.iconId),
        colorFilter = ColorFilter.tint(colorAnimated),
        contentDescription = null
      )

      if (menuItem.textId != null) {
        Spacer(modifier = Modifier.height(4.dp))

        val text = stringResource(menuItem.textId)

        KurobaComposeText(
          text = text,
          color = colorAnimated
        )
      }
    }

    menuItemBadge?.let { badge -> BottomPanelBadge(badge) }
  }
}

@Stable
class KurobaIconPanelState(
  private val globalUiStateHolder: GlobalUiStateHolder,
  private val controllerKey: ControllerKey?,
  val orientation: Orientation,
  visible: Boolean,
  menuItems: List<MenuItem>,
) {
  private val _menuItemStateList = mutableStateListOf<MenuItemState>()
  val menuItemStateList: SnapshotStateList<MenuItemState>
    get() = _menuItemStateList

  private val _visible = mutableStateOf(visible)
  val visible: State<Boolean>
    get() = _visible

  init {
    require(menuItems.isNotEmpty()) { "MenuItems are empty" }
    val newMenuItemStateList = menuItems.map { menuItem -> MenuItemState(menuItem) }

    _menuItemStateList.clear()
    _menuItemStateList.addAll(newMenuItemStateList)
  }

  fun updateBadge(menuItemId: Int, menuItemBadgeInfo: MenuItemBadgeInfo?) {
    val menuItemState = _menuItemStateList
      .firstOrNull { menuItemState -> menuItemState.menuItem.id == menuItemId }
      ?: return

    when (menuItemBadgeInfo) {
      MenuItemBadgeInfo.Dot -> {
        menuItemState.menuItemBadge.value = MenuItemBadge.Dot
      }
      is MenuItemBadgeInfo.Counter -> {
        menuItemState.menuItemBadge.value = MenuItemBadge.Counter(
          counter = menuItemBadgeInfo.counter,
          highlight = menuItemBadgeInfo.highlight
        )
      }
      null -> {
        menuItemState.menuItemBadge.value = null
      }
    }
  }

  fun show() {
    _visible.value = true
  }

  fun onPanelMeasured(width: Dp, height: Dp) {
    globalUiStateHolder.updateBottomPanelState {
      onBottomPanelWidthKnown(width)
      onBottomPanelHeightKnown(height)
    }
  }

  fun hide() {
    _visible.value = false
  }

  fun onPanelAnimationEnd(visible: Boolean) {
    if (controllerKey == null) {
      return
    }

    globalUiStateHolder.updateBottomPanelState {
      if (visible) {
        onBottomPanelShown(controllerKey)
      } else {
        onBottomPanelHidden(controllerKey)
      }
    }
  }

  data class MenuItem(
    val id: Int,
    val iconId: Int,
    val textId: Int? = null
  )

  sealed class MenuItemBadgeInfo {
    data object Dot : MenuItemBadgeInfo()

    data class Counter(
      val counter: Int,
      val highlight: Boolean
    ) : MenuItemBadgeInfo()
  }

  class MenuItemState(
    val menuItem: MenuItem,
    val menuItemBadge: MutableState<MenuItemBadge?> = mutableStateOf(null)
  )

  enum class Orientation {
    Vertical,
    Horizontal
  }
}

@Composable
fun rememberKurobaIconPanelState(
  controllerKey: ControllerKey?,
  menuItems: List<KurobaIconPanelState.MenuItem>,
  visible: Boolean = true,
  orientation: KurobaIconPanelState.Orientation = KurobaIconPanelState.Orientation.Horizontal,
): KurobaIconPanelState {
  val globalUiStateHolder = appDependencies().globalUiStateHolder

  return remember(key1 = orientation, key2 = menuItems) {
    KurobaIconPanelState(
      globalUiStateHolder = globalUiStateHolder,
      controllerKey = controllerKey,
      menuItems = menuItems,
      visible = visible,
      orientation = orientation
    )
  }
}