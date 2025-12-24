package com.github.k1rakishou.chan.features.toolbar.state.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.features.toolbar.AbstractToolbarMenuOverflowItem
import com.github.k1rakishou.chan.features.toolbar.MoreVerticalMenuItem
import com.github.k1rakishou.chan.features.toolbar.state.ToolbarClickableIcon
import com.github.k1rakishou.chan.features.toolbar.state.ToolbarTitleWithSubtitle
import com.github.k1rakishou.chan.ui.compose.badge.ToolbarBadge
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.core_themes.ChanTheme

@Composable
fun KurobaCatalogToolbarContent(
  modifier: Modifier,
  chanTheme: ChanTheme,
  state: KurobaCatalogToolbarSubState,
  showFloatingMenu: (List<AbstractToolbarMenuOverflowItem>) -> Unit
) {
  val toolbarBadgeMut by state.toolbarBadgeState
  val toolbarBadge = toolbarBadgeMut

  val leftIconMut by state.leftItem
  val leftIcon = leftIconMut

  val toolbarMenuMut by state.toolbarMenu
  val toolbarMenu = toolbarMenuMut

  val titleMut by state.title
  val title = titleMut

  val subtitle by state.subtitle
  val toolbarContentState by state.toolbarContentState

  val path = remember { Path() }

  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (leftIcon != null) {
      Spacer(modifier = Modifier.width(12.dp))

      Box {
        ToolbarClickableIcon(
          chanTheme = chanTheme,
          toolbarMenuItem = leftIcon,
          onClick = {
            val iconClickInterceptor = state.iconClickInterceptor

            if (iconClickInterceptor == null || !iconClickInterceptor(leftIcon)) {
              leftIcon.onClick(leftIcon)
            }
          }
        )

        if (toolbarBadge != null) {
          ToolbarBadge(
            chanTheme = chanTheme,
            toolbarBadge = toolbarBadge
          )
        }
      }
    }

    if (title != null) {
      Spacer(modifier = Modifier.width(12.dp))

      ToolbarTitleWithSubtitle(
        modifier = Modifier
          .weight(1f)
          .kurobaClickable(
            bounded = true,
            enabled = true,
            onClick = { state.onMainContentClick?.invoke() }
          )
          .drawBehind { drawTriangle(path, chanTheme.onToolbarBackgroundComposeColor) }
          .padding(start = 12.dp, end = 28.dp),
        title = title,
        subtitle = subtitle,
        chanTheme = chanTheme,
        scrollableTitle = false
      )
    } else {
      Spacer(modifier = Modifier.weight(1f))
    }

    if (toolbarMenu != null) {
      val menuItems = toolbarMenu.menuItems
      if (menuItems.isNotEmpty()) {
        Spacer(modifier = Modifier.width(8.dp))

        for (rightIcon in menuItems) {
          val visible by rightIcon.visibleState
          if (!visible) {
            continue
          }

          Spacer(modifier = Modifier.width(12.dp))

          ToolbarClickableIcon(
            toolbarMenuItem = rightIcon,
            chanTheme = chanTheme,
            enabled = rightIcon.forceEnabled || toolbarContentState.isLoaded,
            onClick = {
              val iconClickInterceptor = state.iconClickInterceptor

              if (iconClickInterceptor == null || !iconClickInterceptor(rightIcon)) {
                rightIcon.onClick(rightIcon)
              }
            }
          )
        }
      }

      val overflowMenuItems = toolbarMenu.overflowMenuItems
      if (overflowMenuItems.isNotEmpty()) {
        val overflowIcon = remember { MoreVerticalMenuItem(onClick = { }) }

        Spacer(modifier = Modifier.width(12.dp))

        ToolbarClickableIcon(
          toolbarMenuItem = overflowIcon,
          chanTheme = chanTheme,
          enabled = toolbarContentState.isLoaded,
          onClick = {
            val iconClickInterceptor = state.iconClickInterceptor

            if (iconClickInterceptor == null || !iconClickInterceptor(overflowIcon)) {
              showFloatingMenu(overflowMenuItems)
            }
          }
        )
      }

      Spacer(modifier = Modifier.width(12.dp))
    }
  }
}

private fun DrawScope.drawTriangle(
  path: Path,
  textColor: Color
) {
  val width = with(density) { 10.dp.toPx() }
  val height = with(density) { 6.dp.toPx() }

  val leftOffset = with(density) { 8.dp.toPx() }
  val topOffset = (size.height - height) / 2f

  translate(left = -leftOffset, top = -topOffset) {
    rotate(degrees = 180f) {
      path.rewind()

      with(path) {
        moveTo(x = width / 2, y = 0f)
        lineTo(x = width, y = height)
        lineTo(x = 0f, y = height)
        close()
      }

      drawPath(path = path, color = textColor)
    }
  }
}
