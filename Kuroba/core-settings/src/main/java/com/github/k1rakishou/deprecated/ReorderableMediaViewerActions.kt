package com.github.k1rakishou.deprecated

import com.google.gson.annotations.SerializedName

@Deprecated("Deprecated")
data class ReorderableMediaViewerActionsDeprecated(
  @SerializedName("media_viewer_action_buttons")
  val mediaViewerActionButtons: List<Long> = DEFAULT_IDS
) {

  fun mediaViewerActionButtons(): List<MediaViewerActionButtonDeprecated> {
    val buttonIds = mediaViewerActionButtons
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

    val buttons = buttonIds.mapNotNull { buttonId -> MediaViewerActionButtonDeprecated.findByIdOrNull(buttonId) }
    if (buttons.size != DEFAULT.size) {
      return DEFAULT
    }

    return buttons
  }

  companion object {
    val DEFAULT = listOf(
      MediaViewerActionButtonDeprecated.GoToPost,
      MediaViewerActionButtonDeprecated.Replies,
      MediaViewerActionButtonDeprecated.Reload,
      MediaViewerActionButtonDeprecated.Download,
      MediaViewerActionButtonDeprecated.Settings
    )

    val DEFAULT_IDS = DEFAULT.map { button -> button.id }
  }
}

@Deprecated("Deprecated")
enum class MediaViewerActionButtonDeprecated(val id: Long, val title: String) {
  GoToPost(0, "GoToPost"),
  Replies(1, "Replies"),
  Reload(2, "Reload"),
  Download(3, "Download"),
  Settings(4, "Settings");

  companion object {
    fun contains(id: Long): Boolean {
      return values().any { button -> button.id == id }
    }

    fun findByIdOrNull(id: Long): MediaViewerActionButtonDeprecated? {
      return values().firstOrNull { button -> button.id == id }
    }
  }
}