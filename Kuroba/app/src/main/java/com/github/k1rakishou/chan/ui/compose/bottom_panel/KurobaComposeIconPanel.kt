package com.github.k1rakishou.chan.ui.compose.bottom_panel

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.ProvideChanTheme
import com.github.k1rakishou.chan.ui.compose.kurobaClickable
import com.github.k1rakishou.chan.ui.helper.PinHelper
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import javax.inject.Inject

class KurobaComposeIconPanel(
  private val context: Context,
  private val orientation: Orientation,
  private val defaultSelectedMenuItemId: Int,
  menuItems: List<MenuItem>
) {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private val menuItemStateList = mutableStateListOf<MenuItemState>()

  val selectedMenuItemId: Int
    get() {
      return menuItemStateList
        .firstOrNull { it.selected.value }?.menuItem?.id
        ?: menuItemStateList.first().menuItem.id
    }

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    val newMenuItemStateList = menuItems.map { menuItem ->
      MenuItemState(
        menuItem = menuItem,
        selected = mutableStateOf(menuItem.id == defaultSelectedMenuItemId)
      )
    }

    menuItemStateList.clear()
    menuItemStateList.addAll(newMenuItemStateList)
  }

  fun setMenuItemSelected(menuItemId: Int) {
    menuItemStateList.forEach { menuItemState ->
      menuItemState.selected.value = menuItemState.menuItem.id == menuItemId
    }
  }

  fun updateBadge(menuItemId: Int, menuItemBadgeInfo: MenuItemBadgeInfo?) {
    val menuItemState = menuItemStateList
      .firstOrNull { menuItemState -> menuItemState.menuItem.id == menuItemId }
      ?: return

    val currentMenuItemBadge = menuItemState.menuItemBadge.value

    when (menuItemBadgeInfo) {
      MenuItemBadgeInfo.Dot -> {
        menuItemState.menuItemBadge.value = MenuItemBadge.Dot
      }
      is MenuItemBadgeInfo.Counter -> {
        if (currentMenuItemBadge is MenuItemBadge.Counter) {
          currentMenuItemBadge.counter.value = menuItemBadgeInfo.counter
          currentMenuItemBadge.highlight.value = menuItemBadgeInfo.highlight
        } else {
          menuItemState.menuItemBadge.value = MenuItemBadge.Counter(
            counter = mutableStateOf(menuItemBadgeInfo.counter),
            highlight = mutableStateOf(menuItemBadgeInfo.highlight)
          )
        }
      }
      null -> {
        menuItemState.menuItemBadge.value = null
      }
    }
  }

  @Composable
  fun BuildPanel(onMenuItemClicked: (Int) -> Unit) {
    ProvideChanTheme(themeEngine, globalWindowInsetsManager) {
      BuildPanelInternal(onMenuItemClicked)
    }
  }

  @Composable
  private fun BuildPanelInternal(onMenuItemClicked: (Int) -> Unit) {
    if (menuItemStateList.isEmpty()) {
      return
    }

    val chanTheme = LocalChanTheme.current
    val backgroundColor = chanTheme.primaryColorCompose

    val currentInsetsCompose by globalWindowInsetsManager.currentInsetsCompose
    val bottomInset = currentInsetsCompose.calculateBottomPadding()
    val leftInset = currentInsetsCompose.calculateStartPadding(LayoutDirection.Ltr)
    val navigationViewSize = dimensionResource(id = R.dimen.navigation_view_size)

    when (orientation) {
      Orientation.Vertical -> {
        Column(
          modifier = Modifier
            .width(navigationViewSize + leftInset)
            .fillMaxHeight()
            .background(backgroundColor)
        ) {
          menuItemStateList.forEachIndexed { _, menuItemState ->
            BuildMenuItemColumn(navigationViewSize, onMenuItemClicked, menuItemState)
          }
        }
      }
      Orientation.Horizontal -> {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(navigationViewSize + bottomInset)
            .background(backgroundColor)
        ) {
          menuItemStateList.forEachIndexed { _, menuItemState ->
            BuildMenuItemRow(navigationViewSize, onMenuItemClicked, menuItemState)
          }
        }
      }
    }
  }

  @Composable
  private fun ColumnScope.BuildMenuItemColumn(
    navigationViewSize: Dp,
    onMenuItemClicked: (Int) -> Unit,
    menuItemState: MenuItemState
  ) {
    val menuItem = menuItemState.menuItem
    val menuItemBadge by menuItemState.menuItemBadge

    Box(
      modifier = Modifier
        .width(navigationViewSize)
        .wrapContentHeight()
        .kurobaClickable(
          bounded = false,
          onClick = {
            onMenuItemClicked(menuItem.id)
            setMenuItemSelected(menuItem.id)
          }
        ),
    ) {
      val uncheckedColor = if (ThemeEngine.isNearToFullyBlackColor(themeEngine.chanTheme.primaryColor)) {
        android.graphics.Color.DKGRAY
      } else {
        ThemeEngine.manipulateColor(themeEngine.chanTheme.primaryColor, .7f)
      }

      val selected by menuItemState.selected
      val targetColor = if (selected) {
        Color.White
      } else {
        remember(key1 = uncheckedColor) { Color(uncheckedColor) }
      }

      val colorAnimated by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(200)
      )

      Image(
        modifier = Modifier
          .fillMaxWidth()
          .height(navigationViewSize + 16.dp)
          .padding(6.dp),
        painter = painterResource(id = menuItem.iconId),
        colorFilter = ColorFilter.tint(colorAnimated),
        contentDescription = null
      )

      menuItemBadge?.let { badge -> BuildMenuItemBadge(badge) }
    }
  }

  @Composable
  private fun RowScope.BuildMenuItemRow(
    navigationViewSize: Dp,
    onMenuItemClicked: (Int) -> Unit,
    menuItemState: MenuItemState
  ) {
    val iconPadding = 6.dp
    val iconWeight = 1f / menuItemStateList.size.toFloat()
    val menuItem = menuItemState.menuItem
    val menuItemBadge by menuItemState.menuItemBadge

    Box(
      modifier = Modifier
        .height(navigationViewSize)
        .weight(iconWeight)
        .kurobaClickable(
          bounded = false,
          onClick = {
            onMenuItemClicked(menuItem.id)
            setMenuItemSelected(menuItem.id)
          }
        ),
    ) {
      val uncheckedColor = if (ThemeEngine.isNearToFullyBlackColor(themeEngine.chanTheme.primaryColor)) {
        android.graphics.Color.DKGRAY
      } else {
        ThemeEngine.manipulateColor(themeEngine.chanTheme.primaryColor, .7f)
      }

      val selected by menuItemState.selected
      val targetColor = if (selected) {
        Color.White
      } else {
        remember(key1 = uncheckedColor) { Color(uncheckedColor) }
      }

      val colorAnimated by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(200)
      )

      Image(
        modifier = Modifier
          .fillMaxSize()
          .padding(iconPadding),
        painter = painterResource(id = menuItem.iconId),
        colorFilter = ColorFilter.tint(colorAnimated),
        contentDescription = null
      )

      menuItemBadge?.let { badge -> BuildMenuItemBadge(badge) }
    }
  }

  @Composable
  private fun BoxScope.BuildMenuItemBadge(menuItemBadge: MenuItemBadge) {
    val chanTheme = LocalChanTheme.current

    when (menuItemBadge) {
      MenuItemBadge.Dot -> {
        Box(modifier = Modifier
          .size(10.dp)
          .offset(x = 8.dp, y = 8.dp)
          .background(color = chanTheme.accentColorCompose, shape = CircleShape)
          .align(Alignment.TopCenter)
        )
      }
      is MenuItemBadge.Counter -> {
        val counter by menuItemBadge.counter
        val highlight by menuItemBadge.highlight

        val backgroundColor = if (highlight) {
          chanTheme.accentColorCompose
        } else {
          if (ThemeEngine.isDarkColor(themeEngine.chanTheme.primaryColor)) {
            Color.LightGray
          } else {
            Color.DarkGray
          }
        }

        val textColor = if (ThemeEngine.isDarkColor(backgroundColor)) {
          Color.White
        } else {
          Color.Black
        }

        val counterText = remember(key1 = counter) { PinHelper.getShortUnreadCount(counter) }

        val fontSize = when (orientation) {
          Orientation.Vertical -> 10.sp
          Orientation.Horizontal -> 11.sp
        }

        KurobaComposeText(
          modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(x = 0.dp, y = 4.dp)
            .background(color = backgroundColor, RoundedCornerShape(size = 4.dp))
            .padding(horizontal = 4.dp),
          text = counterText,
          color = textColor,
          fontSize = fontSize
        )
      }
    }
  }

  data class MenuItem(val id: Int, val iconId: Int)

  sealed class MenuItemBadgeInfo {
    object Dot : MenuItemBadgeInfo()

    data class Counter(
      val counter: Int,
      val highlight: Boolean
    ) : MenuItemBadgeInfo()
  }

  private sealed class MenuItemBadge {
    object Dot : MenuItemBadge()

    data class Counter(
      val counter: MutableState<Int>,
      val highlight: MutableState<Boolean> = mutableStateOf(false)
    ) : MenuItemBadge()
  }

  private data class MenuItemState(
    val menuItem: MenuItem,
    val selected: MutableState<Boolean>,
    val menuItemBadge: MutableState<MenuItemBadge?> = mutableStateOf(null)
  )

  enum class Orientation {
    Vertical,
    Horizontal
  }

}