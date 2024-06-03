package com.github.k1rakishou.chan.ui.compose.post.state

data class PostDisplayOptions(
  val textSelectionEnabled: Boolean,
  val detectLinkableClicks: Boolean,
  val postDisplayMode: PostDisplayMode?,
  val popupOptions: PopupOptions?
) {

  val postSelectionMode: Boolean
    get() = postDisplayMode == PostDisplayMode.PostSelection

}

data class PopupOptions(
  val showGoToPostButton: Boolean,
)

enum class PostDisplayMode {
  PostSelection
}

fun postDisplayOptionsForCatalog(): PostDisplayOptions {
  return PostDisplayOptions(
    textSelectionEnabled = false,
    detectLinkableClicks = false,
    postDisplayMode = null,
    popupOptions = null
  )
}

fun postDisplayOptionsForThread(): PostDisplayOptions {
  return PostDisplayOptions(
    textSelectionEnabled = true,
    detectLinkableClicks = true,
    postDisplayMode = null,
    popupOptions = null
  )
}

fun postDisplayOptionsForRepliesPopup(): PostDisplayOptions {
  return PostDisplayOptions(
    textSelectionEnabled = true,
    detectLinkableClicks = true,
    postDisplayMode = null,
    popupOptions = PopupOptions(
      showGoToPostButton = true
    )
  )
}
