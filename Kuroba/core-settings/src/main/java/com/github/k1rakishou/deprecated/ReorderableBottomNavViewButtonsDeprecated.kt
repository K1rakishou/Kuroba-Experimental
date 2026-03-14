package com.github.k1rakishou.deprecated

import com.google.gson.annotations.SerializedName

@Deprecated("Deprecated")
data class ReorderableBottomNavViewButtonsDeprecated(
  @SerializedName("bottom_nav_view_button_ids")
  val bottomNavViewButtonIds: List<Long> = DEFAULT_IDS
) {
  fun bottomNavViewButtons(): List<BottomNavViewButtonDeprecated> {
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

    val buttons = buttonIds.mapNotNull { buttonId -> BottomNavViewButtonDeprecated.findByIdOrNull(buttonId) }
    if (buttons.size != DEFAULT.size) {
      return DEFAULT
    }

    return buttons
  }

  companion object {
    val DEFAULT = listOf(
      BottomNavViewButtonDeprecated.Search,
      BottomNavViewButtonDeprecated.Archive,
      BottomNavViewButtonDeprecated.MyPosts,
      BottomNavViewButtonDeprecated.Bookmarks,
      BottomNavViewButtonDeprecated.Settings
    )

    val DEFAULT_IDS = DEFAULT.map { button -> button.id }
  }
}

@Deprecated("Deprecated")
enum class BottomNavViewButtonDeprecated(val id: Long, val title: String) {
  Search(0, "Search"),
  MyPosts(1, "MyPosts"),
  Bookmarks(2, "Bookmarks"),
  Settings(3, "Settings"),
  Archive(4, "Archive");

  companion object {
    fun contains(id: Long): Boolean {
      return entries.any { button -> button.id == id }
    }

    fun findByIdOrNull(id: Long): BottomNavViewButtonDeprecated? {
      return entries.firstOrNull { button -> button.id == id }
    }
  }
}