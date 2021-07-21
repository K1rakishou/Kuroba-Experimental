package com.github.k1rakishou.chan.features.setup

interface BoardsSetupView {
  fun onBoardsLoaded()

  fun showLoadingView(titleMessage: String? = null)
  fun hideLoadingView()
  fun showMessageToast(message: String)
}