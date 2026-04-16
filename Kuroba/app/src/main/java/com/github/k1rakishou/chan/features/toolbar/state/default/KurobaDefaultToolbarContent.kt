package com.github.k1rakishou.chan.features.toolbar.state.default

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
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.features.toolbar.AbstractToolbarMenuOverflowItem
import com.github.k1rakishou.chan.features.toolbar.MoreVerticalMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMiddleContent
import com.github.k1rakishou.chan.features.toolbar.state.ToolbarClickableIcon
import com.github.k1rakishou.chan.features.toolbar.state.ToolbarTitleWithSubtitle
import com.github.k1rakishou.chan.ui.compose.badge.ToolbarBadge
import com.github.k1rakishou.core_themes.ChanTheme

@Composable
fun KurobaDefaultToolbarContent(
  modifier: Modifier,
  chanTheme: ChanTheme,
  state: KurobaDefaultToolbarSubState,
  showFloatingMenu: (List<AbstractToolbarMenuOverflowItem>) -> Unit
) {
  val toolbarBadgeMut by state.toolbarBadgeState
  val toolbarBadge = toolbarBadgeMut

  val leftIconMut by state.leftItem
  val leftIcon = leftIconMut

  val middleContentMut by state.middleContent
  val middleContent = middleContentMut

  val toolbarMenuMut by state.toolbarMenu
  val toolbarMenu = toolbarMenuMut

  val scrollableTitle by state.scrollableTitle

  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (leftIcon != null) {
      Spacer(modifier = Modifier.width(12.dp))

      Box {
        ToolbarClickableIcon(
          toolbarMenuItem = leftIcon,
          chanTheme = chanTheme,
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

    when (middleContent) {
      is ToolbarMiddleContent.Title -> {
        if (middleContent.title != null) {
          Spacer(modifier = Modifier.width(12.dp))

          ToolbarTitleWithSubtitle(
            modifier = Modifier
              .weight(1f)
              .padding(start = 12.dp),
            title = middleContent.title,
            subtitle = middleContent.subtitle,
            chanTheme = chanTheme,
            scrollableTitle = scrollableTitle
          )
        } else {
          Spacer(
            modifier = Modifier
              .weight(1f)
          )
        }
      }
      null -> {
        Spacer(modifier = Modifier.weight(1f))
      }
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
        val overflowIcon = remember {
          MoreVerticalMenuItem(onClick = { /**no-op*/ })
        }

        Spacer(modifier = Modifier.width(12.dp))

        ToolbarClickableIcon(
          toolbarMenuItem = overflowIcon,
          chanTheme = chanTheme,
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