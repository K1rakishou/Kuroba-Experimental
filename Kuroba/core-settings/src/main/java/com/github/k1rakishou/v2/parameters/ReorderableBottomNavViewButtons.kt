package com.github.k1rakishou.v2.parameters

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class ReorderableBottomNavViewButtons(
  @field:Json("bottom_nav_view_button_ids")
  val bottomNavViewButtonIds: List<Long> = DEFAULT_IDS
) {
  fun bottomNavViewButtons(): List<BottomNavViewButton> {
    val buttonIds = bottomNavViewButtonIds
    if (buttonIds.isNullOrEmpty()) {
      return DEFAULT
    }

    if (buttonIds.toSet().size != DEFAULT.size) {
      return DEFAULT
    }

    val defaultButtonIds = DEFAULT.map { defaultButton -> defaultButton.id }.toSet()

    for (buttonId in buttonIds) {
      if (!defaultButtonIds.contains(buttonId)) {
        return DEFAULT
      }
    }

    val buttons = buttonIds.mapNotNull { buttonId -> BottomNavViewButton.findByIdOrNull(buttonId) }
    if (buttons.size != DEFAULT.size) {
      return DEFAULT
    }

    return buttons
  }

  enum class BottomNavViewButton(val id: Long, val title: String) {
    Search(0, "Search"),
    MyPosts(1, "MyPosts"),
    Bookmarks(2, "Bookmarks"),
    Settings(3, "Settings"),
    Archive(4, "Archive");

    companion object {
      fun contains(id: Long): Boolean {
        return entries.any { button -> button.id == id }
      }

      fun findByIdOrNull(id: Long): BottomNavViewButton? {
        return entries.firstOrNull { button -> button.id == id }
      }
    }
  }

  companion object {
    val DEFAULT = listOf(
      BottomNavViewButton.Search,
      BottomNavViewButton.Archive,
      BottomNavViewButton.MyPosts,
      BottomNavViewButton.Bookmarks,
      BottomNavViewButton.Settings
    )

    val DEFAULT_IDS = DEFAULT.map { button -> button.id }
  }
}